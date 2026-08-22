use crate::models::ModelRecord;
use crate::native_process;
use reqwest::blocking::Client;
use serde::{Deserialize, Serialize};
use std::io::{BufRead, BufReader, Read};
use std::net::TcpListener;
use std::path::{Path, PathBuf};
use std::process::{Child, Stdio};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};
use tauri::{AppHandle, Emitter};

const SERVER_READY_TIMEOUT: Duration = Duration::from_secs(120);

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GenerationStats {
    pub time_to_first_token_ms: Option<f64>,
    pub prompt_tokens_per_second: Option<f64>,
    pub generation_tokens_per_second: Option<f64>,
    pub generated_tokens: u64,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum RuntimeState {
    Stopped,
    Starting,
    LoadingModel,
    Ready,
    Generating,
    Stopping,
    Error,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct EngineStatus {
    pub loaded_model_id: Option<String>,
    pub loaded_model_path: Option<String>,
    pub loaded_model_name: Option<String>,
    pub model_architecture: Option<String>,
    pub chat_template: Option<String>,
    pub context_length: Option<i64>,
    pub executable: Option<String>,
    pub backend: Option<String>,
    pub running: bool,
    pub runtime_state: RuntimeState,
    pub server_port: Option<u16>,
    pub error: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct EngineLog {
    pub timestamp: String,
    pub level: String,
    pub component: String,
    pub message: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct UsageEvent {
    pub generation_id: String,
    pub prompt_tokens: u64,
    pub completion_tokens: u64,
    pub total_tokens: u64,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ChatMessage {
    pub role: String,
    pub content: String,
}

/// The server process stays alive after every turn. Its stdout/stderr are
/// diagnostics only and can never become chat content.
pub struct EngineRuntime {
    pub loaded_model: Option<ModelRecord>,
    pub child: Option<Child>,
    pub server_port: Option<u16>,
    pub runtime_state: RuntimeState,
    pub stop_requested: bool,
    pub error: Option<String>,
    pub logs: Arc<Mutex<Vec<EngineLog>>>,
}

impl Default for EngineRuntime {
    fn default() -> Self {
        Self {
            loaded_model: None,
            child: None,
            server_port: None,
            runtime_state: RuntimeState::Stopped,
            stop_requested: false,
            error: None,
            logs: Arc::new(Mutex::new(Vec::new())),
        }
    }
}

impl EngineRuntime {
    fn log(&self, level: &str, message: impl Into<String>) {
        if let Ok(mut logs) = self.logs.lock() {
            logs.push(EngineLog {
                timestamp: chrono_like_timestamp(),
                level: level.to_string(),
                component: "llama-server".to_string(),
                message: message.into(),
            });
            if logs.len() > 500 {
                let keep_from = logs.len() - 500;
                logs.drain(0..keep_from);
            }
        }
    }

    pub fn emit_runtime_state(&self, app: &AppHandle) {
        let _ = app.emit("llm://runtime-state", self.status());
    }

    pub fn status(&self) -> EngineStatus {
        EngineStatus {
            loaded_model_id: self.loaded_model.as_ref().map(|model| model.id.clone()),
            loaded_model_path: self.loaded_model.as_ref().map(|model| model.path.clone()),
            loaded_model_name: self.loaded_model.as_ref().map(|model| model.name.clone()),
            model_architecture: self
                .loaded_model
                .as_ref()
                .and_then(|model| model.architecture.clone()),
            chat_template: self
                .loaded_model
                .as_ref()
                .and_then(|model| model.chat_template.clone()),
            context_length: self
                .loaded_model
                .as_ref()
                .and_then(|model| model.context_length),
            executable: resolve_llama_server().map(|path| path.to_string_lossy().to_string()),
            backend: self
                .loaded_model
                .as_ref()
                .and_then(|model| model.backend.clone()),
            running: self.child.is_some(),
            runtime_state: self.runtime_state.clone(),
            server_port: self.server_port,
            error: self.error.clone(),
        }
    }

    fn terminate_server(&mut self) {
        self.runtime_state = RuntimeState::Stopping;
        if let Some(mut child) = self.child.take() {
            let _ = child.kill();
            let _ = child.wait();
        }
        self.server_port = None;
        self.runtime_state = RuntimeState::Stopped;
    }

    fn health_check(port: u16) -> bool {
        let client = match Client::builder()
            .connect_timeout(Duration::from_millis(250))
            .timeout(Duration::from_millis(500))
            .build()
        {
            Ok(client) => client,
            Err(_) => return false,
        };
        client
            .get(format!("http://127.0.0.1:{port}/health"))
            .send()
            .map(|response| response.status().is_success())
            .unwrap_or(false)
    }

    fn wait_until_ready(&mut self) -> Result<(), String> {
        let port = self
            .server_port
            .ok_or_else(|| "llama-server no tiene un puerto asignado".to_string())?;
        let started = Instant::now();
        while started.elapsed() < SERVER_READY_TIMEOUT {
            let exited = self
                .child
                .as_mut()
                .and_then(|child| child.try_wait().ok())
                .flatten();
            if let Some(status) = exited {
                self.runtime_state = RuntimeState::Error;
                let error = format!(
                    "llama-server terminó antes de estar listo (código {:?})",
                    status.code()
                );
                self.error = Some(error.clone());
                return Err(error);
            }
            if Self::health_check(port) {
                self.runtime_state = RuntimeState::Ready;
                self.log("info", format!("llama-server listo en 127.0.0.1:{port}"));
                return Ok(());
            }
            std::thread::sleep(Duration::from_millis(250));
        }
        self.runtime_state = RuntimeState::Error;
        let error = "llama-server no respondió a /health dentro de 120 segundos".to_string();
        self.error = Some(error.clone());
        Err(error)
    }
}

fn chrono_like_timestamp() -> String {
    format!("{:?}", std::time::SystemTime::now())
}

pub trait LocalLlmEngine {
    fn load_model(&mut self, model: ModelRecord, gpu_layers: i32) -> Result<EngineStatus, String>;
    fn unload_model(&mut self) -> Result<(), String>;
    fn stop(&mut self) -> Result<(), String>;
    fn get_status(&self) -> EngineStatus;
}

impl LocalLlmEngine for EngineRuntime {
    fn load_model(&mut self, model: ModelRecord, gpu_layers: i32) -> Result<EngineStatus, String> {
        if !Path::new(&model.path).is_file() {
            let error = "El archivo GGUF ya no existe en la ruta guardada".to_string();
            self.runtime_state = RuntimeState::Error;
            self.error = Some(error.clone());
            return Err(error);
        }
        let executable = match resolve_llama_server() {
            Some(path) => path,
            None => {
                let error = "No se encontró llama-server.exe. Coloca el binario y sus DLL en desktop/native/bin/".to_string();
                self.runtime_state = RuntimeState::Error;
                self.error = Some(error.clone());
                return Err(error);
            }
        };

        self.terminate_server();
        self.loaded_model = Some(model.clone());
        self.stop_requested = false;
        self.error = None;
        self.runtime_state = RuntimeState::Starting;

        let port = match TcpListener::bind(("127.0.0.1", 0)) {
            Ok(listener) => match listener.local_addr() {
                Ok(address) => address.port(),
                Err(error) => {
                    self.runtime_state = RuntimeState::Error;
                    self.loaded_model = None;
                    let message = format!("No se pudo leer el puerto local: {error}");
                    self.error = Some(message.clone());
                    return Err(message);
                }
            },
            Err(error) => {
                self.runtime_state = RuntimeState::Error;
                self.loaded_model = None;
                let message = format!("No se pudo reservar un puerto local: {error}");
                self.error = Some(message.clone());
                return Err(message);
            }
        };
        // Do not blindly allocate a model's advertised maximum context (many
        // GGUF files report 128k+ and would OOM a laptop). The UI generation
        // setting is currently 8192, so start the sidecar at that safe limit
        // and let smaller models use their own maximum.
        let context = model
            .context_length
            .unwrap_or(8192)
            .clamp(512, 8192)
            .to_string();
        let gpu_layers_value = gpu_layers.clamp(-1, 999);
        let gpu_layers = gpu_layers_value.to_string();
        // Vulkan exposes every adapter to llama.cpp (including the integrated
        // GPU). Prefer the discrete NVIDIA adapter when GPU offload is
        // enabled; otherwise Vulkan's default can silently select Intel.
        let accelerator_device = (gpu_layers_value != 0)
            .then(|| preferred_accelerator_device(&executable))
            .flatten();

        let mut command = native_process::command(&executable);
        if let Some(device) = accelerator_device.as_deref() {
            self.log("info", format!("Aceleración GPU: {device}"));
            command.args(["--device", device]);
        }
        command
            .args(["--model", &model.path, "--host", "127.0.0.1", "--port"])
            .arg(port.to_string())
            .args([
                "--ctx-size",
                &context,
                "-ngl",
                &gpu_layers,
                "--parallel",
                "1",
                "--no-ui",
                // Roleplay must expose only the character response. Models
                // such as Qwen3 otherwise default to chain-of-thought output
                // even when the client sends enable_thinking=false.
                "--reasoning",
                "off",
                "--log-verbosity",
                "1",
            ])
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());
        let mut child = match command.spawn() {
            Ok(child) => child,
            Err(error) => {
                self.runtime_state = RuntimeState::Error;
                self.loaded_model = None;
                let message = format!("No se pudo iniciar llama-server: {error}");
                self.error = Some(message.clone());
                return Err(message);
            }
        };

        let log_store = Arc::clone(&self.logs);
        if let Some(stdout) = child.stdout.take() {
            spawn_log_reader(stdout, Arc::clone(&log_store), "info");
        }
        if let Some(stderr) = child.stderr.take() {
            spawn_log_reader(stderr, log_store, "debug");
        }
        self.child = Some(child);
        self.server_port = Some(port);
        self.runtime_state = RuntimeState::LoadingModel;

        if let Err(error) = self.wait_until_ready() {
            self.log("error", error.clone());
            self.terminate_server();
            self.runtime_state = RuntimeState::Error;
            self.error = Some(error.clone());
            return Err(error);
        }
        Ok(self.status())
    }

    fn unload_model(&mut self) -> Result<(), String> {
        self.stop_requested = true;
        self.terminate_server();
        self.loaded_model = None;
        self.stop_requested = false;
        self.error = None;
        self.log("info", "Modelo local descargado");
        Ok(())
    }

    fn stop(&mut self) -> Result<(), String> {
        self.stop_requested = true;
        // Only cancel the HTTP stream; keep llama-server and its GGUF alive.
        self.log("info", "Generación detenida por el usuario");
        Ok(())
    }

    fn get_status(&self) -> EngineStatus {
        self.status()
    }
}

impl Drop for EngineRuntime {
    fn drop(&mut self) {
        self.terminate_server();
    }
}

pub fn resolve_llama_server() -> Option<PathBuf> {
    if let Ok(path) = std::env::var("LOCAL_CHARACTER_LLAMA_SERVER") {
        let path = PathBuf::from(path);
        if path.is_file() {
            return Some(path);
        }
    }
    let candidates = [
        PathBuf::from("native/bin/llama-server.exe"),
        PathBuf::from("desktop/native/bin/llama-server.exe"),
        PathBuf::from("llama-server.exe"),
    ];
    if let Some(path) = candidates.into_iter().find(|path| path.is_file()) {
        return Some(path);
    }
    let executable_candidates = std::env::current_exe()
        .ok()
        .and_then(|path| path.parent().map(Path::to_path_buf))
        .map(|directory| {
            [
                directory.join("resources/llama-server.exe"),
                directory.join("resources/native/bin/llama-server.exe"),
                directory.join("native/bin/llama-server.exe"),
            ]
        });
    executable_candidates.and_then(|paths| paths.into_iter().find(|path| path.is_file()))
}

/// Select the best accelerator exposed by llama.cpp. On hybrid-graphics
/// laptops Vulkan commonly lists the integrated adapter first, so choosing
/// the first device would leave the discrete GPU idle. If no NVIDIA adapter
/// is present, fall back to the first Vulkan/CUDA/HIP/SYCL device.
fn preferred_accelerator_device(executable: &Path) -> Option<String> {
    let output = native_process::command(executable)
        .arg("--list-devices")
        .output()
        .ok()?;
    let mut text = String::from_utf8_lossy(&output.stdout).into_owned();
    // Some llama.cpp builds print device enumeration to stderr even though
    // the command succeeds, so inspect both streams.
    if !output.stderr.is_empty() {
        text.push('\n');
        text.push_str(&String::from_utf8_lossy(&output.stderr));
    }
    let mut first_device = None;
    for line in text.lines() {
        let trimmed = line.trim();
        let Some((id, description)) = trimmed.split_once(':') else {
            continue;
        };
        let id = id.trim();
        if !(id.starts_with("Vulkan")
            || id.starts_with("CUDA")
            || id.starts_with("HIP")
            || id.starts_with("SYCL"))
        {
            continue;
        }
        if first_device.is_none() {
            first_device = Some(id.to_string());
        }
        let lower = description.to_ascii_lowercase();
        if lower.contains("nvidia") || lower.contains("geforce") || lower.contains("rtx") {
            return Some(id.to_string());
        }
    }
    first_device
}

/// Kept for the standalone benchmark command. Chat generation never invokes
/// llama-cli; it always uses the sidecar server above.
pub fn resolve_llama_cli() -> Option<PathBuf> {
    if let Ok(path) = std::env::var("LOCAL_CHARACTER_LLAMA_CLI") {
        let path = PathBuf::from(path);
        if path.is_file() {
            return Some(path);
        }
    }
    [
        PathBuf::from("native/bin/llama-cli.exe"),
        PathBuf::from("desktop/native/bin/llama-cli.exe"),
        PathBuf::from("llama-cli.exe"),
    ]
    .into_iter()
    .find(|path| path.is_file())
}

fn spawn_log_reader<R>(reader: R, logs: Arc<Mutex<Vec<EngineLog>>>, level: &'static str)
where
    R: Read + Send + 'static,
{
    std::thread::spawn(move || {
        let reader = BufReader::new(reader);
        for line in reader.lines() {
            let Ok(line) = line else { break };
            if line.trim().is_empty() {
                continue;
            }
            if let Ok(mut logs) = logs.lock() {
                logs.push(EngineLog {
                    timestamp: chrono_like_timestamp(),
                    level: level.to_string(),
                    component: "llama-server".to_string(),
                    message: line,
                });
                if logs.len() > 500 {
                    let keep_from = logs.len() - 500;
                    logs.drain(0..keep_from);
                }
            }
        }
    });
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct DeltaEvent {
    pub generation_id: String,
    pub text: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct CompleteEvent {
    pub generation_id: String,
    pub conversation_id: String,
    pub message_id: String,
    pub finish_reason: String,
    pub stats: GenerationStats,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct CancelledEvent {
    pub generation_id: String,
    pub conversation_id: String,
    pub message_id: String,
    pub finish_reason: String,
}

#[derive(Debug, Deserialize)]
struct StreamChunk {
    #[serde(default)]
    choices: Vec<StreamChoice>,
    usage: Option<Usage>,
}

#[derive(Debug, Deserialize)]
struct StreamChoice {
    #[serde(default)]
    delta: StreamDelta,
    finish_reason: Option<String>,
}

#[derive(Debug, Default, Deserialize)]
struct StreamDelta {
    content: Option<String>,
    #[serde(
        default,
        alias = "reasoning",
        alias = "thinking",
        alias = "reasoningContent"
    )]
    reasoning_content: Option<String>,
}

/// Normalizes both OpenAI token deltas and non-standard cumulative/replayed
/// chunks. The latter occur with some GGUF templates and proxies and would
/// otherwise duplicate the visible answer when every event is concatenated.
#[derive(Default)]
struct StreamTextNormalizer {
    accumulated: String,
}

impl StreamTextNormalizer {
    fn push(&mut self, incoming: &str) -> String {
        if incoming.is_empty() {
            return String::new();
        }
        if self.accumulated.is_empty() {
            self.accumulated.push_str(incoming);
            return incoming.to_string();
        }
        if incoming.len() > self.accumulated.len() && incoming.starts_with(&self.accumulated) {
            let novel = incoming[self.accumulated.len()..].to_string();
            self.accumulated.clear();
            self.accumulated.push_str(incoming);
            return novel;
        }
        if incoming == self.accumulated && incoming.len() >= 8 {
            return String::new();
        }
        if self.accumulated.starts_with(incoming) && incoming.len() >= 8 {
            return String::new();
        }
        if self.accumulated.ends_with(incoming) && incoming.len() >= 12 {
            return String::new();
        }

        let maximum = self.accumulated.len().min(incoming.len());
        for overlap in (12..=maximum).rev() {
            if !self
                .accumulated
                .is_char_boundary(self.accumulated.len() - overlap)
                || !incoming.is_char_boundary(overlap)
            {
                continue;
            }
            if self.accumulated.ends_with(&incoming[..overlap]) {
                let novel = incoming[overlap..].to_string();
                self.accumulated.push_str(&novel);
                return novel;
            }
        }

        self.accumulated.push_str(incoming);
        incoming.to_string()
    }
}

/// Removes protocol artifacts only after the provider has already separated
/// the structured completion from engine logs. It is stateful so tags split
/// across SSE chunks never flash in the UI (`<thi` + `nking>`).
struct RoleplayOutputSanitizer {
    pending: String,
    reasoning: bool,
    stopped: bool,
    leading_checked: bool,
    character_prefix: Option<String>,
    character_name: String,
    user_name: String,
}

impl RoleplayOutputSanitizer {
    fn new(character_name: Option<&str>, user_name: Option<&str>) -> Self {
        let character_name = character_name
            .map(str::trim)
            .filter(|name| !name.is_empty())
            .unwrap_or("el personaje")
            .to_string();
        Self {
            pending: String::new(),
            reasoning: false,
            stopped: false,
            leading_checked: false,
            character_prefix: Some(format!("{character_name}:")),
            character_name,
            user_name: user_name
                .map(str::trim)
                .filter(|name| !name.is_empty())
                .unwrap_or("Usuario")
                .to_string(),
        }
    }

    fn push(&mut self, input: &str) -> Vec<String> {
        self.pending.push_str(input);
        self.drain(false)
    }

    fn finish(&mut self) -> Vec<String> {
        self.drain(true)
    }

    fn drain(&mut self, final_chunk: bool) -> Vec<String> {
        let mut output = Vec::new();
        loop {
            if self.stopped {
                self.pending.clear();
                break;
            }
            if self.reasoning {
                if let Some((index, length)) =
                    find_any(&self.pending, &["</think>", "</thinking>", "</analysis>"])
                {
                    self.pending.drain(..index + length);
                    self.reasoning = false;
                    continue;
                }
                if final_chunk {
                    self.pending.clear();
                } else {
                    retain_possible_suffix(
                        &mut self.pending,
                        &["</think>", "</thinking>", "</analysis>"],
                    );
                }
                break;
            }

            if !self.leading_checked {
                let leading_whitespace = self.pending.len() - self.pending.trim_start().len();
                if leading_whitespace > 0 {
                    self.pending.drain(..leading_whitespace);
                    continue;
                }
                // Some models verbalize their internal status instead of
                // returning a hidden reasoning field. Hold generic preambles
                // until the first action or spoken line is available.
                let generic_meta_prefixes = [
                    "El modelo",
                    "The model",
                    "El sistema",
                    "The system",
                    "El asistente",
                    "The assistant",
                    "La IA",
                    "The AI",
                    "La inteligencia artificial",
                    "Artificial intelligence",
                    "Step-by-step reasoning",
                    "Step by step reasoning",
                    "Chain-of-thought",
                    "Chain of thought",
                    "Reasoning process",
                    "Razonamiento paso a paso",
                    "Proceso de razonamiento",
                    "Pensamiento paso a paso",
                    "Let's think",
                    "Let me think",
                    "Voy a analizar",
                    "Following these steps",
                    "Based on the context",
                    "Based on the instructions",
                    "I will respond",
                    "Here is the response",
                    "Here's the response",
                    "A continuaciÃ³n responderÃ©",
                    "CONTINUITY CONTEXT",
                    "Current topic",
                    "Current situation",
                    "Earlier conversation summary",
                    "Recent meaningful actions",
                    "Unresolved questions",
                    "Pending events",
                    "Recent speakers",
                    "CONTEXTO DE CONTINUIDAD",
                    "Tema actual",
                    "SituaciÃ³n actual",
                    "Resumen de la conversaciÃ³n",
                    "Eventos pendientes",
                ];
                if generic_meta_prefixes
                    .iter()
                    .any(|prefix| starts_with_ascii_case_insensitive(&self.pending, prefix))
                {
                    if let Some(index) = find_roleplay_start(&self.pending) {
                        self.pending.drain(..index);
                        trim_structural_separator(&mut self.pending);
                        continue;
                    }
                    // Keep the whole preamble buffered. Dropping only the first
                    // sentence would expose a second sentence such as
                    // "A continuación..." before the roleplay marker arrives
                    // in a later SSE chunk.
                    if final_chunk {
                        self.pending.clear();
                        self.leading_checked = true;
                        continue;
                    }
                    break;
                }
                for prefix in ["System is thinking...", "System is thinking…"] {
                    if starts_with_ascii_case_insensitive(&self.pending, prefix) {
                        self.pending.drain(..prefix.len());
                        trim_structural_separator(&mut self.pending);
                        continue;
                    }
                }
                for prefix in [
                    "El modelo está pensando",
                    "El modelo esta pensando",
                    "The model is thinking",
                ] {
                    if starts_with_ascii_case_insensitive(&self.pending, prefix) {
                        if let Some(index) = self.pending.find('*') {
                            self.pending.drain(..index);
                            trim_structural_separator(&mut self.pending);
                            continue;
                        }
                        if let Some(index) = self.pending.find(". ") {
                            self.pending.drain(..index + 2);
                            continue;
                        }
                        if final_chunk {
                            self.pending.clear();
                        }
                        break;
                    }
                }
                let waiting_for_roleplay_after_thinking = [
                    "El modelo está pensando",
                    "El modelo esta pensando",
                    "The model is thinking",
                ]
                .iter()
                .any(|prefix| starts_with_ascii_case_insensitive(&self.pending, prefix));
                if waiting_for_roleplay_after_thinking {
                    if final_chunk {
                        self.pending.clear();
                        self.leading_checked = true;
                        continue;
                    }
                    break;
                }
                if let Some(prefix) = self.character_prefix.as_deref() {
                    if starts_with_ascii_case_insensitive(&self.pending, prefix) {
                        self.pending.drain(..prefix.len());
                        trim_structural_separator(&mut self.pending);
                        self.leading_checked = true;
                        continue;
                    }
                }
                for prefix in [
                    "Assistant:",
                    "assistant:",
                    "User:",
                    "user:",
                    "System:",
                    "system:",
                    "Developer:",
                    "developer:",
                    "Thinking:",
                    "thinking:",
                    "Reasoning:",
                    "reasoning:",
                    "Analysis:",
                    "analysis:",
                    "Prompt:",
                    "prompt:",
                    "Generation:",
                    "generation:",
                ] {
                    if self.pending.starts_with(prefix) {
                        self.pending.drain(..prefix.len());
                        trim_structural_separator(&mut self.pending);
                        self.leading_checked = true;
                        continue;
                    }
                }
                if !final_chunk
                    && [
                        "System is thinking...",
                        "System is thinking…",
                        "El modelo está pensando",
                        "El modelo esta pensando",
                        "The model is thinking",
                        "Assistant:",
                        "assistant:",
                        "User:",
                        "user:",
                        "System:",
                        "system:",
                        "Developer:",
                        "developer:",
                        "Thinking:",
                        "thinking:",
                        "Reasoning:",
                        "reasoning:",
                        "Analysis:",
                        "analysis:",
                        "Prompt:",
                        "prompt:",
                        "Generation:",
                        "generation:",
                        "El modelo",
                        "The model",
                        "El sistema",
                        "The system",
                        "El asistente",
                        "The assistant",
                        "La IA",
                        "The AI",
                        "La inteligencia artificial",
                        "Artificial intelligence",
                        "Step-by-step reasoning",
                        "Step by step reasoning",
                        "Chain-of-thought",
                        "Chain of thought",
                        "Reasoning process",
                        "Razonamiento paso a paso",
                        "Proceso de razonamiento",
                        "Pensamiento paso a paso",
                        "Let's think",
                        "Let me think",
                        "Voy a analizar",
                        "Following these steps",
                        "Based on the context",
                        "Based on the instructions",
                        "I will respond",
                        "Here is the response",
                        "Here's the response",
                        "A continuaciÃ³n responderÃ©",
                        "CONTINUITY CONTEXT",
                        "Current topic",
                        "Current situation",
                        "Earlier conversation summary",
                        "Recent meaningful actions",
                        "Unresolved questions",
                        "Pending events",
                        "Recent speakers",
                        "CONTEXTO DE CONTINUIDAD",
                        "Tema actual",
                        "SituaciÃ³n actual",
                        "Resumen de la conversaciÃ³n",
                        "Eventos pendientes",
                    ]
                    .iter()
                    .map(|prefix| *prefix)
                    .chain(self.character_prefix.as_deref())
                    .any(|prefix| is_partial_prefix_ascii_case_insensitive(&self.pending, prefix))
                {
                    break;
                }
                self.leading_checked = true;
            }

            let tags = [
                ("<think>", true),
                ("<thinking>", true),
                ("<analysis>", true),
                ("</think>", false),
                ("</thinking>", false),
                ("</analysis>", false),
                ("<response>", false),
                ("</response>", false),
                ("<RESPONSE>", false),
                ("</RESPONSE>", false),
                ("<|im_end|>", false),
                ("<|eot_id|>", false),
                ("<|end_of_text|>", false),
                ("</s>", false),
            ];
            if let Some((index, tag, opens_reasoning)) = find_tag(&self.pending, &tags) {
                let before = self.pending[..index].to_string();
                self.pending.drain(..index + tag.len());
                let stray_reasoning_close =
                    ["</think>", "</thinking>", "</analysis>"].contains(&tag) && !self.reasoning;
                if !before.is_empty() && !stray_reasoning_close {
                    output.push(self.resolve_placeholders(&before));
                }
                self.reasoning = opens_reasoning;
                if matches!(tag, "<response>" | "<RESPONSE>") {
                    // Some chat templates wrap the visible answer and put the
                    // character name immediately after the wrapper.
                    self.leading_checked = false;
                }
                if ["<|im_end|>", "<|eot_id|>", "<|end_of_text|>", "</s>"].contains(&tag) {
                    self.stopped = true;
                }
                continue;
            }

            if let Some(index) = self.pending.to_ascii_lowercase().find("[prompt:") {
                let before = self.pending[..index].to_string();
                self.pending.clear();
                if !before.is_empty() {
                    output.push(self.resolve_placeholders(&before));
                }
                break;
            }

            if final_chunk {
                if !self.pending.is_empty() {
                    let remaining = std::mem::take(&mut self.pending);
                    output.push(self.resolve_placeholders(&remaining));
                }
            } else {
                let hold = longest_partial_suffix(
                    &self.pending,
                    &[
                        "<think>",
                        "<thinking>",
                        "<analysis>",
                        "</think>",
                        "</thinking>",
                        "</analysis>",
                        "<response>",
                        "</response>",
                        "<RESPONSE>",
                        "</RESPONSE>",
                        "<|im_end|>",
                        "<|eot_id|>",
                        "</s>",
                        "[prompt:",
                        "{{user}}",
                        "{{ user }}",
                        "{{USER}}",
                        "{{ USER }}",
                        "{{char}}",
                        "{{ char }}",
                        "{{CHAR}}",
                        "{{ CHAR }}",
                        "{{character}}",
                        "{{ character }}",
                    ],
                );
                let emit_len = self.pending.len().saturating_sub(hold);
                if emit_len > 0 {
                    let visible: String = self.pending.drain(..emit_len).collect();
                    output.push(self.resolve_placeholders(&visible));
                }
            }
            break;
        }
        output
    }

    fn resolve_placeholders(&self, value: &str) -> String {
        let mut resolved = value.to_string();
        for placeholder in ["{{user}}", "{{ user }}"] {
            resolved = replace_ascii_case_insensitive(&resolved, placeholder, &self.user_name);
        }
        for placeholder in ["{{char}}", "{{ char }}", "{{character}}", "{{ character }}"] {
            resolved = replace_ascii_case_insensitive(&resolved, placeholder, &self.character_name);
        }
        normalize_roleplay_markup(&resolved)
    }
}

fn normalize_roleplay_markup(value: &str) -> String {
    let mut output = String::with_capacity(value.len());
    let mut in_star_run = false;
    for character in value.chars() {
        if character == '*' {
            if !in_star_run {
                output.push('*');
                in_star_run = true;
            }
        } else {
            in_star_run = false;
            output.push(character);
        }
    }
    output
}

fn replace_ascii_case_insensitive(value: &str, needle: &str, replacement: &str) -> String {
    if needle.is_empty() {
        return value.to_string();
    }
    let lower = value.to_ascii_lowercase();
    let target = needle.to_ascii_lowercase();
    let mut output = String::with_capacity(value.len());
    let mut cursor = 0;
    while let Some(relative) = lower[cursor..].find(&target) {
        let start = cursor + relative;
        output.push_str(&value[cursor..start]);
        output.push_str(replacement);
        cursor = start + needle.len();
    }
    output.push_str(&value[cursor..]);
    output
}

fn find_any(value: &str, needles: &[&str]) -> Option<(usize, usize)> {
    needles
        .iter()
        .filter_map(|needle| value.find(needle).map(|index| (index, needle.len())))
        .min_by_key(|(index, _)| *index)
}

fn find_roleplay_start(value: &str) -> Option<usize> {
    value
        .char_indices()
        .find_map(|(index, character)| matches!(character, '*' | '"' | '“' | '«').then_some(index))
}

fn find_tag<'a>(value: &str, tags: &'a [(&'a str, bool)]) -> Option<(usize, &'a str, bool)> {
    tags.iter()
        .filter_map(|(tag, opens)| value.find(tag).map(|index| (index, *tag, *opens)))
        .min_by_key(|(index, _, _)| *index)
}

fn starts_with_ascii_case_insensitive(value: &str, prefix: &str) -> bool {
    value
        .get(..prefix.len())
        .is_some_and(|head| head.eq_ignore_ascii_case(prefix))
}

fn is_partial_prefix_ascii_case_insensitive(value: &str, prefix: &str) -> bool {
    !value.is_empty()
        && value.len() < prefix.len()
        && prefix
            .get(..value.len())
            .is_some_and(|head| head.eq_ignore_ascii_case(value))
}

fn longest_partial_suffix(value: &str, tokens: &[&str]) -> usize {
    tokens
        .iter()
        .flat_map(|token| {
            (1..token.len())
                .filter_map(move |length| value.ends_with(&token[..length]).then_some(length))
        })
        .max()
        .unwrap_or(0)
}

fn retain_possible_suffix(value: &mut String, tokens: &[&str]) {
    let hold = longest_partial_suffix(value, tokens);
    if hold == 0 {
        value.clear();
    } else {
        let tail = value[value.len() - hold..].to_string();
        value.clear();
        value.push_str(&tail);
    }
}

fn trim_structural_separator(value: &mut String) {
    let trimmed = value.trim_start_matches([' ', '\t']);
    if trimmed.len() != value.len() {
        value.drain(..value.len() - trimmed.len());
    }
}

#[derive(Debug, Deserialize)]
struct Usage {
    #[serde(default)]
    prompt_tokens: u64,
    #[serde(default)]
    completion_tokens: u64,
    #[serde(default)]
    total_tokens: u64,
}

#[derive(Debug, Serialize)]
struct ChatCompletionRequest {
    model: String,
    messages: Vec<ChatMessage>,
    max_tokens: u32,
    temperature: f32,
    top_p: f32,
    top_k: u32,
    min_p: f32,
    repeat_penalty: f32,
    stream: bool,
    stream_options: StreamOptions,
    #[serde(skip_serializing_if = "Option::is_none")]
    chat_template_kwargs: Option<serde_json::Value>,
}

#[derive(Debug, Serialize)]
struct StreamOptions {
    include_usage: bool,
}

pub fn generate_stream(
    app: AppHandle,
    runtime: Arc<Mutex<EngineRuntime>>,
    generation_id: String,
    conversation_id: String,
    message_id: String,
    prompt: String,
    messages: Option<Vec<ChatMessage>>,
    character_name: Option<String>,
    user_name: Option<String>,
    max_output: u32,
    _context: u32,
    _gpu_layers: i32,
) -> Result<(), String> {
    let (port, model_name, chat_messages, thinking_capable) = {
        let mut state = runtime
            .lock()
            .map_err(|_| "Estado del motor bloqueado".to_string())?;
        let model = state
            .loaded_model
            .clone()
            .ok_or_else(|| "Carga un modelo GGUF antes de enviar mensajes".to_string())?;
        let port = state
            .server_port
            .ok_or_else(|| "El servidor local todavía no está listo".to_string())?;
        if !matches!(state.runtime_state, RuntimeState::Ready) {
            return Err("El modelo local todavía está cargando; espera a que indique Listo".into());
        }
        if !EngineRuntime::health_check(port) {
            state.runtime_state = RuntimeState::Error;
            state.error = Some("El motor local dejó de responder a /health.".into());
            state.emit_runtime_state(&app);
            return Err(
                "No se pudo conectar con el motor local: el servidor dejó de responder.".into(),
            );
        }
        state.stop_requested = false;
        state.error = None;
        state.runtime_state = RuntimeState::Generating;
        state.emit_runtime_state(&app);
        let chat_messages = messages
            .filter(|items| !items.is_empty())
            .unwrap_or_else(|| {
                vec![ChatMessage {
                    role: "user".into(),
                    content: prompt.clone(),
                }]
            });
        let template_hint = model
            .chat_template
            .as_deref()
            .unwrap_or_default()
            .to_ascii_lowercase();
        let model_hint = model.name.to_ascii_lowercase();
        let architecture_hint = model
            .architecture
            .as_deref()
            .unwrap_or_default()
            .to_ascii_lowercase();
        let thinking_capable = template_hint.contains("enable_thinking")
            || template_hint.contains("thinking")
            || model_hint.contains("qwen3")
            || model_hint.contains("deepseek")
            || model_hint.contains("thinking")
            || architecture_hint.contains("qwen")
            || architecture_hint.contains("deepseek")
            || architecture_hint.contains("reasoning");
        (port, model.name, chat_messages, thinking_capable)
    };

    let request = ChatCompletionRequest {
        model: model_name,
        messages: chat_messages,
        max_tokens: max_output,
        // Small and medium GGUF roleplay models become noticeably less
        // coherent with llama.cpp's more creative defaults. These conservative
        // values keep the current scene and the latest question dominant while
        // retaining enough variation for natural dialogue.
        temperature: 0.35,
        top_p: 0.9,
        top_k: 40,
        min_p: 0.05,
        repeat_penalty: 1.08,
        stream: true,
        stream_options: StreamOptions {
            include_usage: true,
        },
        // Qwen3 and other thinking models otherwise spend the whole short
        // response budget in reasoning_content. Disable hidden reasoning so
        // the visible answer arrives in delta.content, which is the only
        // field allowed into the chat transcript.
        chat_template_kwargs: thinking_capable
            .then(|| serde_json::json!({ "enable_thinking": false })),
    };
    let client = match Client::builder()
        .connect_timeout(Duration::from_secs(5))
        .timeout(Duration::from_secs(600))
        .build()
    {
        Ok(client) => client,
        Err(error) => {
            let message = format!("No se pudo preparar el cliente local: {error}");
            finish_generation_error(&app, &runtime, message.clone());
            return Err(message);
        }
    };
    let response = match client
        .post(format!("http://127.0.0.1:{port}/v1/chat/completions"))
        .json(&request)
        .send()
    {
        Ok(response) if response.status().is_success() => response,
        Ok(response) => {
            let status = response.status();
            let body = response.text().unwrap_or_default();
            let detail = if status == reqwest::StatusCode::SERVICE_UNAVAILABLE {
                "El modelo local todavía no está listo"
            } else if body.to_ascii_lowercase().contains("chat template") {
                "El GGUF no tiene una plantilla de chat compatible; revisa sus metadatos o usa un GGUF con chat_template embebida."
            } else {
                "llama-server rechazó la solicitud"
            };
            let message = format!("{detail} ({status})");
            finish_generation_error(&app, &runtime, format!("{message}: {body}"));
            return Err(message);
        }
        Err(error) => {
            finish_generation_error(
                &app,
                &runtime,
                format!("Error conectando con llama-server: {error}"),
            );
            return Err(format!("No se pudo conectar con llama-server: {error}"));
        }
    };

    let started = Instant::now();
    let mut first_token_at = None;
    let mut estimated_tokens = 0_u64;
    let mut usage = None;
    let mut finish_reason = "eof".to_string();
    let mut sanitizer =
        RoleplayOutputSanitizer::new(character_name.as_deref(), user_name.as_deref());
    let mut stream_text = StreamTextNormalizer::default();
    let mut visible_chars = 0_u64;
    let mut reasoning_chars = 0_u64;
    let mut raw_chars = 0_u64;
    let mut emitted_visible = false;
    let mut reader = BufReader::new(response);
    loop {
        let mut line = String::new();
        match reader.read_line(&mut line) {
            Ok(0) => break,
            Ok(_) => {}
            Err(error) if error.kind() == std::io::ErrorKind::TimedOut => {
                let should_stop = runtime
                    .lock()
                    .map(|state| state.stop_requested)
                    .unwrap_or(true);
                if should_stop {
                    break;
                }
                continue;
            }
            Err(error) => {
                let message = format!("Error leyendo SSE de llama-server: {error}");
                finish_generation_error(&app, &runtime, message.clone());
                return Err(message);
            }
        }
        let Some(payload) = line.strip_prefix("data:").map(str::trim) else {
            continue;
        };
        if payload == "[DONE]" {
            break;
        }
        let Ok(chunk) = serde_json::from_str::<StreamChunk>(payload) else {
            // SSE comments/metadata are not model content and are ignored.
            continue;
        };
        if chunk.usage.is_some() {
            usage = chunk.usage;
        }
        if let Some(reason) = chunk
            .choices
            .first()
            .and_then(|choice| choice.finish_reason.clone())
        {
            finish_reason = reason;
        }
        let should_stop = runtime
            .lock()
            .map(|state| state.stop_requested)
            .unwrap_or(true);
        if should_stop {
            break;
        }
        let Some(delta) = chunk.choices.first().map(|choice| &choice.delta) else {
            continue;
        };
        if let Some(reasoning) = delta.reasoning_content.as_deref() {
            reasoning_chars += reasoning.chars().count() as u64;
        }
        if let Some(text) = delta.content.as_deref() {
            raw_chars += text.chars().count() as u64;
            let novel = stream_text.push(text);
            for visible in sanitizer.push(&novel) {
                emit_visible_delta(
                    &app,
                    &generation_id,
                    &mut first_token_at,
                    &mut estimated_tokens,
                    &mut visible_chars,
                    &mut emitted_visible,
                    &started,
                    visible,
                )?;
            }
        }
    }

    for visible in sanitizer.finish() {
        emit_visible_delta(
            &app,
            &generation_id,
            &mut first_token_at,
            &mut estimated_tokens,
            &mut visible_chars,
            &mut emitted_visible,
            &started,
            visible,
        )?;
    }

    let elapsed = started.elapsed();
    let was_stopped = {
        let mut state = runtime
            .lock()
            .map_err(|_| "Estado del motor bloqueado".to_string())?;
        let stopped = state.stop_requested;
        state.stop_requested = false;
        state.runtime_state = if server_is_running(&mut state) {
            RuntimeState::Ready
        } else {
            RuntimeState::Error
        };
        state.emit_runtime_state(&app);
        stopped
    };

    let generated_tokens = usage
        .as_ref()
        .map(|value| value.completion_tokens)
        .filter(|value| *value > 0)
        .unwrap_or(estimated_tokens);
    if !emitted_visible && !was_stopped {
        let message = if reasoning_chars > 0 {
            "El modelo solo devolvió razonamiento oculto; se descartó y no hubo respuesta visible."
        } else {
            "El modelo no produjo una respuesta visible. Revisa su plantilla de chat GGUF."
        };
        finish_generation_error(&app, &runtime, message.to_string());
        return Err(message.to_string());
    }
    if was_stopped {
        app.emit(
            "llm://cancelled",
            CancelledEvent {
                generation_id,
                conversation_id,
                message_id,
                finish_reason: "cancelled".into(),
            },
        )
        .map_err(|error| format!("No se pudo emitir cancelación: {error}"))?;
        return Ok(());
    }
    if let Ok(state) = runtime.lock() {
        state.log(
            "debug",
            format!(
                "generation output raw_chars={raw_chars} visible_chars={visible_chars} reasoning_chars={reasoning_chars}"
            ),
        );
    }
    if let Some(value) = usage {
        let _ = app.emit(
            "llm://usage",
            UsageEvent {
                generation_id: generation_id.clone(),
                prompt_tokens: value.prompt_tokens,
                completion_tokens: value.completion_tokens,
                total_tokens: value.total_tokens,
            },
        );
    }
    let stats = GenerationStats {
        time_to_first_token_ms: first_token_at.map(|time| time.as_secs_f64() * 1000.0),
        prompt_tokens_per_second: None,
        generation_tokens_per_second: (elapsed.as_secs_f64() > 0.0)
            .then(|| generated_tokens as f64 / elapsed.as_secs_f64()),
        generated_tokens,
    };
    let _ = was_stopped; // Partial output is still a valid completed local turn.
    app.emit(
        "llm://complete",
        CompleteEvent {
            generation_id,
            conversation_id,
            message_id,
            finish_reason,
            stats,
        },
    )
    .map_err(|error| format!("No se pudo emitir finalización: {error}"))?;
    Ok(())
}

fn emit_visible_delta(
    app: &AppHandle,
    generation_id: &str,
    first_token_at: &mut Option<Duration>,
    estimated_tokens: &mut u64,
    visible_chars: &mut u64,
    emitted_visible: &mut bool,
    started: &Instant,
    text: String,
) -> Result<(), String> {
    if text.is_empty() {
        return Ok(());
    }
    *emitted_visible = true;
    *visible_chars += text.chars().count() as u64;
    first_token_at.get_or_insert_with(|| started.elapsed());
    *estimated_tokens += ((text.chars().count() as u64).saturating_add(3) / 4).max(1);
    app.emit(
        "llm://delta",
        DeltaEvent {
            generation_id: generation_id.to_string(),
            text,
        },
    )
    .map_err(|error| format!("No se pudo emitir streaming: {error}"))
}

fn finish_generation_error(app: &AppHandle, runtime: &Arc<Mutex<EngineRuntime>>, message: String) {
    if let Ok(mut state) = runtime.lock() {
        state.stop_requested = false;
        state.runtime_state = if server_is_running(&mut state) {
            RuntimeState::Ready
        } else {
            RuntimeState::Error
        };
        state.error = Some(message.clone());
        state.log("error", message);
        state.emit_runtime_state(app);
    }
}

fn server_is_running(state: &mut EngineRuntime) -> bool {
    let running = state
        .child
        .as_mut()
        .and_then(|child| child.try_wait().ok())
        .flatten()
        .is_none()
        && state.child.is_some();
    if !running {
        state.child.take();
        state.server_port = None;
    }
    running
}

#[cfg(test)]
mod tests {
    use super::{RoleplayOutputSanitizer, StreamTextNormalizer};

    #[test]
    fn stream_normalizer_accepts_incremental_and_cumulative_chunks() {
        let mut incremental = StreamTextNormalizer::default();
        assert_eq!(incremental.push("*Mira"), "*Mira");
        assert_eq!(incremental.push(" hacia ti*"), " hacia ti*");
        assert_eq!(incremental.accumulated, "*Mira hacia ti*");

        let mut cumulative = StreamTextNormalizer::default();
        assert_eq!(cumulative.push("*Mira"), "*Mira");
        assert_eq!(cumulative.push("*Mira hacia"), " hacia");
        assert_eq!(cumulative.push("*Mira hacia ti*"), " ti*");
        assert_eq!(cumulative.accumulated, "*Mira hacia ti*");
    }

    #[test]
    fn stream_normalizer_drops_long_replays_but_keeps_short_repetition() {
        let mut normalizer = StreamTextNormalizer::default();
        assert_eq!(
            normalizer.push("Una respuesta completa."),
            "Una respuesta completa."
        );
        assert_eq!(normalizer.push("Una respuesta completa."), "");

        let mut stutter = StreamTextNormalizer::default();
        assert_eq!(stutter.push("no"), "no");
        assert_eq!(stutter.push("no"), "no");
        assert_eq!(stutter.accumulated, "nono");
    }

    #[test]
    fn sanitizer_holds_split_reasoning_tags_and_normalizes_actions() {
        let mut sanitizer = RoleplayOutputSanitizer::new(Some("Tsuyu"), Some("Tadeo"));
        assert!(sanitizer.push("<thi").is_empty());
        assert!(sanitizer.push("nking>private</thinking>").is_empty());
        let visible = sanitizer.push("**mira alrededor**\\n\\\"Hola\\\"");
        let mut result = visible.join("");
        result.push_str(&sanitizer.finish().join(""));
        assert_eq!(result, "*mira alrededor*\\n\\\"Hola\\\"");
    }

    #[test]
    fn sanitizer_discards_structural_prefix_and_stop_tokens() {
        let mut sanitizer = RoleplayOutputSanitizer::new(Some("Tsuyu"), Some("Tadeo"));
        let mut result = sanitizer
            .push("Assistant: respuesta<|im_end|>basura")
            .join("");
        result.push_str(&sanitizer.finish().join(""));
        assert_eq!(result, "respuesta");
    }

    #[test]
    fn sanitizer_hides_system_thinking_and_resolves_placeholders() {
        let mut sanitizer = RoleplayOutputSanitizer::new(Some("Tsuyu Asui"), Some("Tadeo"));
        let mut result = sanitizer
            .push("System is thinking... TSUYU ASUI: mira a {{ user }} y saluda a {{CHAR}}")
            .join("");
        result.push_str(&sanitizer.finish().join(""));
        assert_eq!(result, "mira a Tadeo y saluda a Tsuyu Asui");
    }

    #[test]
    fn sanitizer_resolves_placeholders_case_insensitively() {
        let mut sanitizer = RoleplayOutputSanitizer::new(Some("Tsuyu"), Some("Alejandro"));
        let mut result = sanitizer.push("{{USER}} mira a {{ Character }}").join("");
        result.push_str(&sanitizer.finish().join(""));
        assert_eq!(result, "Alejandro mira a Tsuyu");
    }

    #[test]
    fn sanitizer_discards_unopened_reasoning_before_response_wrapper() {
        let mut sanitizer = RoleplayOutputSanitizer::new(Some("Tsuyu"), Some("Tadeo"));
        let mut result = sanitizer
            .push("El usuario está solicitando: '{{Saluda.}}'</thinking><RESPONSE>Tsuyu: ¡Hola")
            .join("");
        result.push_str(&sanitizer.finish().join(""));
        assert_eq!(result, "¡Hola");
    }

    #[test]
    fn sanitizer_hides_spanish_model_thinking_prefix() {
        let mut sanitizer = RoleplayOutputSanitizer::new(Some("Tsuyu"), Some("Tadeo"));
        let mut result = sanitizer
            .push("El modelo está pensando sobre la respuesta. *Tsuyu se acerca.*")
            .join("");
        result.push_str(&sanitizer.finish().join(""));
        assert_eq!(result, "*Tsuyu se acerca.*");
    }

    #[test]
    fn sanitizer_hides_natural_language_reasoning_preamble() {
        let mut sanitizer = RoleplayOutputSanitizer::new(Some("Tsuyu Asui"), Some("Tadeo"));
        let mut result = sanitizer
            .push("El modelo está considerando cómo responder a esta solicitud específica. A continuación, verás cómo se desarrolla esta conversación entre el modelo y el usuario. **Tsuyu se detiene.**")
            .join("");
        result.push_str(&sanitizer.finish().join(""));
        assert_eq!(result, "*Tsuyu se detiene.*");
    }

    #[test]
    fn sanitizer_hides_ai_preamble_when_it_is_split_across_stream_chunks() {
        let mut sanitizer = RoleplayOutputSanitizer::new(Some("Tsuyu Asui"), Some("Tadeo"));
        assert!(sanitizer
            .push("La IA está considerando cómo responder. ")
            .is_empty());
        assert!(sanitizer
            .push("A continuación, verás cómo se desarrolla la escena. ")
            .is_empty());
        let mut result = sanitizer.push("**Tsuyu se detiene y te mira.**").join("");
        result.push_str(&sanitizer.finish().join(""));
        assert_eq!(result, "*Tsuyu se detiene y te mira.*");
    }

    #[test]
    fn sanitizer_hides_step_by_step_reasoning_before_roleplay() {
        let mut sanitizer = RoleplayOutputSanitizer::new(Some("Tsuyu Asui"), Some("Tadeo"));
        let mut result = sanitizer
            .push("Step-by-step reasoning process:\n\n1. Understand the persona and context.\n2. Decide how she should respond.\n\nFollowing these steps, I will respond as the persona described. Tsuyu: **Tsuyu looks at you.** \"What do you mean?\"")
            .join("");
        result.push_str(&sanitizer.finish().join(""));
        assert_eq!(result, "*Tsuyu looks at you.* \"What do you mean?\"");
    }
}
