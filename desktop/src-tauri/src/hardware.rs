use crate::native_process;
use serde::Serialize;
use sysinfo::System;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GpuInfo {
    pub name: String,
    pub vendor: Option<String>,
    pub vram_bytes: Option<u64>,
    pub driver: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BackendInfo {
    pub id: String,
    pub name: String,
    pub available: bool,
    pub detail: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct HardwareSnapshot {
    pub os: String,
    pub hostname: String,
    pub cpu_name: String,
    pub physical_cores: usize,
    pub logical_threads: usize,
    pub ram_total_bytes: u64,
    pub ram_available_bytes: u64,
    pub gpus: Vec<GpuInfo>,
    pub backends: Vec<BackendInfo>,
}

pub fn detect() -> HardwareSnapshot {
    let mut system = System::new_all();
    system.refresh_all();
    let cpu_name = system
        .cpus()
        .first()
        .map(|cpu| cpu.brand().trim().to_string())
        .filter(|name| !name.is_empty())
        .unwrap_or_else(|| "CPU no identificado".to_string());
    let logical_threads = system.cpus().len().max(1);
    let physical_cores = System::physical_core_count()
        .unwrap_or(logical_threads)
        .max(1);
    let gpus = detect_nvidia_gpus();
    let cuda_available = !gpus.is_empty();
    let vulkan_available = command_succeeds("vulkaninfo", &["--summary"]);
    let backends = vec![
        BackendInfo {
            id: "cpu".into(),
            name: "CPU".into(),
            available: true,
            detail: "Siempre disponible".into(),
        },
        BackendInfo {
            id: "cuda".into(),
            name: "CUDA".into(),
            available: cuda_available,
            detail: if cuda_available {
                "GPU NVIDIA detectada; falta verificar que el binario de llama.cpp tenga CUDA"
                    .into()
            } else {
                "No se detectó nvidia-smi".into()
            },
        },
        BackendInfo {
            id: "vulkan".into(),
            name: "Vulkan".into(),
            available: vulkan_available,
            detail: if vulkan_available {
                "vulkaninfo respondió; falta verificar el backend del binario".into()
            } else {
                "vulkaninfo no está disponible o no enumeró un dispositivo".into()
            },
        },
    ];
    HardwareSnapshot {
        os: format!(
            "{} {}",
            System::name().unwrap_or_else(|| "Windows".into()),
            System::os_version().unwrap_or_default()
        ),
        hostname: System::host_name().unwrap_or_else(|| "PC".into()),
        cpu_name,
        physical_cores,
        logical_threads,
        ram_total_bytes: system.total_memory(),
        ram_available_bytes: system.available_memory(),
        gpus,
        backends,
    }
}

fn command_succeeds(command: &str, args: &[&str]) -> bool {
    native_process::command(command)
        .args(args)
        .output()
        .map(|output| output.status.success())
        .unwrap_or(false)
}

fn detect_nvidia_gpus() -> Vec<GpuInfo> {
    let output = match native_process::command("nvidia-smi")
        .args([
            "--query-gpu=name,memory.total,driver_version",
            "--format=csv,noheader,nounits",
        ])
        .output()
    {
        Ok(output) if output.status.success() => output,
        _ => return Vec::new(),
    };
    String::from_utf8_lossy(&output.stdout)
        .lines()
        .filter_map(|line| {
            let mut fields = line.split(',').map(str::trim);
            let name = fields.next()?.to_string();
            let vram_mib = fields.next()?.parse::<u64>().ok();
            let driver = fields.next().map(str::to_string);
            Some(GpuInfo {
                name,
                vendor: Some("NVIDIA".into()),
                vram_bytes: vram_mib.map(|value| value * 1024 * 1024),
                driver,
            })
        })
        .collect()
}
