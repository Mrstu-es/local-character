import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import {
  Activity,
  ArrowDownToLine,
  BarChart3,
  Bot,
  ChevronRight,
  CircleAlert,
  Cpu,
  Gauge,
  HardDrive,
  ImagePlus,
  Laptop,
  Library,
  MemoryStick,
  MessageSquare,
  PanelLeftClose,
  PanelLeftOpen,
  Play,
  Plus,
  RefreshCw,
  Search,
  Send,
  Settings,
  Square,
  Trash2,
  Upload,
  UserRound,
  WandSparkles,
  Zap,
} from "lucide-react";
import type { CharacterRecord, ChatMessage, ConversationRecord, EngineStatus, GenerationStats, HardwareSnapshot, ModelRecord, View } from "./types";
import {
  addModel,
  chooseCharacterImage,
  chooseCharacterCard,
  chooseGguf,
  getHardwareSnapshot,
  isTauriRuntime,
  listenToGeneration,
  listModels,
  listCharacters,
  importCharacterCard,
  localFileUrl,
  loadModel,
  removeModel,
  runBenchmark,
  sendChatMessage,
  saveCharacter as persistCharacter,
  deleteCharacter as persistDeleteCharacter,
  clearEngineLogs,
  getEngineLogs,
  listConversations,
  listMessages,
  saveConversation,
  saveMessage,
  stopGeneration,
  unloadModel,
} from "./lib/tauri";

const formatBytes = (bytes?: number) => {
  if (!bytes || bytes < 1) return "—";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / 1024 ** index).toFixed(index > 2 ? 1 : 0)} ${units[index]}`;
};

const formatSpeed = (value?: number) => (value && Number.isFinite(value) ? `${value.toFixed(1)} tok/s` : "—");

const navItems: Array<{ id: View; label: string; icon: typeof Laptop }> = [
  { id: "overview", label: "Inicio", icon: Laptop },
  { id: "chat", label: "Chats", icon: MessageSquare },
  { id: "characters", label: "Personajes", icon: UserRound },
  { id: "models", label: "Modelos", icon: Library },
  { id: "benchmarks", label: "Benchmark", icon: BarChart3 },
  { id: "settings", label: "Ajustes", icon: Settings },
];

function App() {
  const [view, setView] = useState<View>("overview");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() =>
    typeof window !== "undefined" && window.matchMedia("(max-width: 1180px)").matches,
  );
  const [hardware, setHardware] = useState<HardwareSnapshot | null>(null);
  const [models, setModels] = useState<ModelRecord[]>([]);
  const [engine, setEngine] = useState<EngineStatus>({ running: false });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");

  const refresh = useCallback(async () => {
    if (!isTauriRuntime()) {
      setError("Abre la aplicación con Tauri para conectar con el hardware y llama.cpp reales.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const [snapshot, knownModels] = await Promise.all([getHardwareSnapshot(), listModels()]);
      setHardware(snapshot);
      setModels(knownModels);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    const media = window.matchMedia("(max-width: 1180px)");
    const handleChange = (event: MediaQueryListEvent) => setSidebarCollapsed(event.matches);
    media.addEventListener("change", handleChange);
    return () => media.removeEventListener("change", handleChange);
  }, []);

  const filteredModels = useMemo(
    () => models.filter((model) => `${model.name} ${model.path} ${model.architecture ?? ""}`.toLowerCase().includes(search.toLowerCase())),
    [models, search],
  );

  async function handleAddModel() {
    if (!isTauriRuntime()) {
      setError("El selector GGUF está disponible dentro del ejecutable Tauri.");
      return;
    }
    try {
      const path = await chooseGguf();
      if (!path) return;
      setBusy(true);
      const model = await addModel(path);
      setModels((current) => [model, ...current.filter((item) => item.path !== model.path)]);
      setView("models");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setBusy(false);
    }
  }

  async function handleLoad(model: ModelRecord) {
    setBusy(true);
    setError(null);
    try {
      setEngine(await loadModel(model.id));
      setView("chat");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setBusy(false);
    }
  }

  async function handleUnload() {
    try {
      await unloadModel();
      setEngine({ running: false });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    }
  }

  async function handleRemove(id: string) {
    try {
      await removeModel(id);
      setModels((current) => current.filter((model) => model.id !== id));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    }
  }

  return (
    <div className={`app-shell ${sidebarCollapsed ? "sidebar-collapsed" : ""}`}>
      <aside className="sidebar">
        <div className="brand-row">
          <div className="brand-mark"><WandSparkles size={18} /></div>
          {!sidebarCollapsed && <div><strong>Local Character</strong><span>Desktop Lab</span></div>}
          <button className="icon-button sidebar-toggle" aria-label="Contraer menú" onClick={() => setSidebarCollapsed((value) => !value)}>
            {sidebarCollapsed ? <PanelLeftOpen size={18} /> : <PanelLeftClose size={18} />}
          </button>
        </div>
        <nav className="main-nav">
          {navItems.map(({ id, label, icon: Icon }) => (
            <button key={id} className={`nav-item ${view === id ? "active" : ""}`} onClick={() => setView(id)} title={sidebarCollapsed ? label : undefined}>
              <Icon size={19} /><span>{!sidebarCollapsed && label}</span>
            </button>
          ))}
        </nav>
        {!sidebarCollapsed && (
          <div className="sidebar-footer">
            <div className={`backend-dot ${engine.executable ? "ready" : ""}`} />
            <div><span>Motor local</span><strong>{engine.executable ? "Disponible" : "No configurado"}</strong></div>
          </div>
        )}
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div><span className="eyebrow">LOCAL-FIRST AI WORKSPACE</span><h1>{navItems.find((item) => item.id === view)?.label}</h1></div>
          <div className="topbar-actions">
            <span className="release-pill">0.1.0-alpha</span>
            <button className="icon-button" onClick={() => void refresh()} aria-label="Actualizar" disabled={busy}><RefreshCw size={17} className={busy ? "spin" : ""} /></button>
          </div>
        </header>
        {error && <div className="error-banner"><CircleAlert size={17} /><span>{error}</span><button onClick={() => setError(null)}>Cerrar</button></div>}
        {view === "overview" && <Overview hardware={hardware} models={models} engine={engine} onAddModel={handleAddModel} onOpenModels={() => setView("models")} onOpenChat={() => setView("chat")} />}
        {view === "models" && <ModelsView models={filteredModels} search={search} setSearch={setSearch} engine={engine} onAdd={handleAddModel} onLoad={handleLoad} onUnload={handleUnload} onRemove={handleRemove} busy={busy} />}
        {view === "chat" && <ChatView engine={engine} setError={setError} />}
        {view === "characters" && <CharactersView2 />}
        {view === "benchmarks" && <BenchmarksView2 models={models} hardware={hardware} />}
        {view === "settings" && <SettingsView hardware={hardware} />}
        {view === "settings" && <DiagnosticsPanel />}
      </main>
    </div>
  );
}

function Overview({ hardware, models, engine, onAddModel, onOpenModels, onOpenChat }: { hardware: HardwareSnapshot | null; models: ModelRecord[]; engine: EngineStatus; onAddModel: () => void; onOpenModels: () => void; onOpenChat: () => void }) {
  const primaryGpu = hardware?.gpus[0];
  return <section className="page-grid">
    <div className="hero-card panel-accent">
      <div className="hero-copy"><span className="eyebrow">LABORATORIO LOCAL</span><h2>Modelos grandes.<br /><em>Control total.</em></h2><p>Prueba GGUF reales en tu PC, elige CPU o GPU y mide cada token sin enviar tus conversaciones a la nube.</p><div className="hero-actions"><button className="primary-button" onClick={onAddModel}><Plus size={17} /> Añadir modelo</button><button className="secondary-button" onClick={onOpenChat}><MessageSquare size={17} /> Abrir chat</button></div></div>
      <div className="hero-orbit"><div className="orbit-ring ring-one" /><div className="orbit-ring ring-two" /><div className="orbit-core"><Zap size={30} /></div></div>
    </div>
    <div className="stat-grid">
      <StatCard icon={<Cpu />} label="CPU" value={hardware?.cpuName ?? "Detectando…"} detail={hardware ? `${hardware.physicalCores} núcleos · ${hardware.logicalThreads} hilos` : "Hardware local"} />
      <StatCard icon={<MemoryStick />} label="RAM disponible" value={formatBytes(hardware?.ramAvailableBytes)} detail={hardware ? `${formatBytes(hardware.ramTotalBytes)} total` : "Sin lectura"} />
      <StatCard icon={<Gauge />} label="GPU" value={primaryGpu?.name ?? "No detectada"} detail={primaryGpu?.vramBytes ? `${formatBytes(primaryGpu.vramBytes)} VRAM` : "CUDA/Vulkan se verifican al iniciar"} />
    </div>
    <div className="panel wide-panel"><div className="panel-heading"><div><span className="eyebrow">RUNTIME</span><h3>Estado del motor</h3></div><span className={`status-badge ${engine.executable ? "success" : "warning"}`}>{engine.executable ? "Listo para usar" : "Requiere llama.cpp"}</span></div><div className="runtime-row"><RuntimeItem label="Modelo" value={engine.loadedModelPath ? engine.loadedModelPath.split(/[\\/]/).pop() ?? "Cargado" : "Ninguno cargado"} /><RuntimeItem label="Backend" value={engine.backend ?? "CPU · selección al cargar"} /><RuntimeItem label="llama.cpp" value="b10218 · fijado" /><RuntimeItem label="Privacidad" value="100% local" /></div></div>
    <div className="panel wide-panel"><div className="panel-heading"><div><span className="eyebrow">BIBLIOTECA</span><h3>Modelos recientes</h3></div><button className="text-button" onClick={onOpenModels}>Ver todos <ChevronRight size={15} /></button></div>{models.length === 0 ? <EmptyState icon={<HardDrive />} title="Todavía no hay modelos" text="Añade un archivo .gguf desde tu SSD, otra unidad o un NAS montado." action={<button className="secondary-button" onClick={onAddModel}><Upload size={16} /> Añadir GGUF</button>} /> : <div className="model-mini-list">{models.slice(0, 3).map((model) => <div className="model-mini" key={model.id}><div className="model-icon"><Bot size={18} /></div><div><strong>{model.name}</strong><span>{formatBytes(model.sizeBytes)} · {model.quantization ?? "metadata parcial"}</span></div><span className={`file-state ${model.exists ? "" : "missing"}`}>{model.exists ? "Disponible" : "Archivo no encontrado"}</span></div>)}</div>}</div>
  </section>;
}

function StatCard({ icon, label, value, detail }: { icon: ReactNode; label: string; value: string; detail: string }) { return <div className="stat-card"><div className="stat-icon">{icon}</div><span className="eyebrow">{label}</span><strong title={value}>{value}</strong><small>{detail}</small></div>; }
function RuntimeItem({ label, value }: { label: string; value: string }) { return <div className="runtime-item"><span>{label}</span><strong title={value}>{value}</strong></div>; }
function EmptyState({ icon, title, text, action }: { icon: ReactNode; title: string; text: string; action?: ReactNode }) { return <div className="empty-state"><div className="empty-icon">{icon}</div><h4>{title}</h4><p>{text}</p>{action}</div>; }

function ModelsView({ models, search, setSearch, engine, onAdd, onLoad, onUnload, onRemove, busy }: { models: ModelRecord[]; search: string; setSearch: (value: string) => void; engine: EngineStatus; onAdd: () => void; onLoad: (model: ModelRecord) => void; onUnload: () => void; onRemove: (id: string) => void; busy: boolean }) {
  return <section className="page-grid single-column"><div className="page-intro"><div><span className="eyebrow">MODEL REGISTRY</span><h2>Modelos GGUF</h2><p>Las rutas permanecen donde las elegiste; la aplicación guarda solo una referencia y metadata local.</p></div><button className="primary-button" onClick={onAdd}><Plus size={17} /> Añadir GGUF</button></div><div className="toolbar"><div className="search-box"><Search size={17} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar por nombre, ruta o arquitectura" /></div><span className="toolbar-count">{models.length} modelos</span></div>{models.length === 0 ? <div className="panel"><EmptyState icon={<HardDrive />} title="Biblioteca vacía" text="Selecciona cualquier .gguf compatible con llama.cpp. El motor decide la compatibilidad real." action={<button className="secondary-button" onClick={onAdd}><Upload size={16} /> Seleccionar archivo</button>} /></div> : <div className="model-grid">{models.map((model) => <ModelCard key={model.id} model={model} loaded={engine.loadedModelId === model.id} onLoad={() => onLoad(model)} onUnload={onUnload} onRemove={() => onRemove(model.id)} busy={busy} />)}</div>}</section>;
}

function ModelCard({ model, loaded, onLoad, onUnload, onRemove, busy }: { model: ModelRecord; loaded: boolean; onLoad: () => void; onUnload: () => void; onRemove: () => void; busy: boolean }) {
  return <article className={`model-card ${loaded ? "loaded" : ""}`}><div className="model-card-top"><div className="model-icon large"><Bot size={21} /></div><div className="model-title"><h3>{model.name}</h3><span>{model.architecture ?? "Arquitectura no leída"} · {model.quantization ?? "cuantización no indicada"}</span></div><span className={`status-dot ${model.exists ? "online" : "offline"}`} title={model.exists ? "Archivo disponible" : "Archivo no encontrado"} /></div><div className="model-path" title={model.path}>{model.path}</div><div className="model-facts"><RuntimeItem label="Tamaño" value={formatBytes(model.sizeBytes)} /><RuntimeItem label="Contexto máx." value={model.contextLength ? `${model.contextLength.toLocaleString()} tokens` : "No declarado"} /><RuntimeItem label="GGUF" value={model.exists ? "Válido" : "Falta archivo"} /></div><div className="model-card-actions">{loaded ? <button className="secondary-button" onClick={onUnload}><ArrowDownToLine size={16} /> Descargar</button> : <button className="primary-button" onClick={onLoad} disabled={busy || !model.exists}><Play size={16} /> Cargar</button>}<button className="icon-button danger" onClick={onRemove} aria-label="Eliminar referencia"><Trash2 size={16} /></button></div></article>;
}

function ChatView({ engine, setError }: { engine: EngineStatus; setError: (value: string | null) => void }) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [generating, setGenerating] = useState(false);
  const [activeGeneration, setActiveGeneration] = useState<string | null>(null);
  const activeGenerationRef = useRef<string | null>(null);
  const assistantForGeneration = useRef(new Map<string, string>());
  const assistantContent = useRef(new Map<string, string>());
  const messagesRef = useRef<ChatMessage[]>([]);

  useEffect(() => { messagesRef.current = messages; }, [messages]);

  useEffect(() => {
    if (!isTauriRuntime()) {
      setConversationId("browser-local-chat");
      return;
    }
    void (async () => {
      try {
        let records = await listConversations();
        if (records.length === 0) {
          const created: ConversationRecord = { id: crypto.randomUUID(), title: "Nuevo chat", pinned: false, archived: false, kind: "direct", lastMessagePreview: "", createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() };
          await saveConversation(created);
          records = [created];
        }
        setConversationId(records[0].id);
        const stored = await listMessages(records[0].id);
        setMessages(stored.map((message) => ({ id: message.id, role: message.role, content: message.content, createdAt: Date.parse(message.createdAt) || Date.now() })));
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : String(cause));
      }
    })();
  }, [setError]);

  useEffect(() => {
    activeGenerationRef.current = activeGeneration;
  }, [activeGeneration]);

  useEffect(() => {
    if (!isTauriRuntime()) return;
    let cleanups: (() => void)[] = [];
    void listenToGeneration((event) => {
      if (event.generationId !== activeGenerationRef.current) return;
      const assistantId = assistantForGeneration.current.get(event.generationId) ?? event.generationId;
      assistantContent.current.set(assistantId, `${assistantContent.current.get(assistantId) ?? ""}${event.text}`);
      setMessages((current) => current.map((message) => message.id === assistantId ? { ...message, content: message.content + event.text } : message));
    }, (event) => {
      if (event.generationId !== activeGenerationRef.current) return;
      setGenerating(false);
      setActiveGeneration(null);
      const assistantId = assistantForGeneration.current.get(event.generationId) ?? event.generationId;
      const assistant = messagesRef.current.find((message) => message.id === assistantId);
      const content = assistantContent.current.get(assistantId) ?? assistant?.content ?? "";
      if (assistant && conversationId) {
        void saveMessage({ id: assistant.id, conversationId, role: "assistant", content, pinned: false, metadataJson: "{}", createdAt: new Date(assistant.createdAt).toISOString() }).catch(() => undefined);
      }
    }, (event) => {
      setGenerating(false);
      setActiveGeneration(null);
      setError(String(event));
    }).then((unlisteners) => { cleanups = unlisteners; });
    return () => cleanups.forEach((cleanup) => cleanup());
  }, [conversationId, setError]);

  async function send() {
    const content = draft.trim();
    if (!content || generating) return;
    if (!engine.loadedModelId) { setError("Carga un modelo GGUF antes de conversar."); return; }
    const userMessage: ChatMessage = { id: crypto.randomUUID(), role: "user", content, createdAt: Date.now() };
    const assistantId = crypto.randomUUID();
    setMessages((current) => [...current, userMessage, { id: assistantId, role: "assistant", content: "", createdAt: Date.now() }]);
    if (isTauriRuntime() && conversationId) {
      const base = { conversationId, pinned: false, metadataJson: "{}" };
      void saveMessage({ ...base, id: userMessage.id, role: "user", content, createdAt: new Date(userMessage.createdAt).toISOString() }).catch(() => undefined);
      void saveMessage({ ...base, id: assistantId, role: "assistant", content: "", createdAt: new Date().toISOString() }).catch(() => undefined);
    }
    setDraft("");
    setGenerating(true);
    try {
      const generationId = await sendChatMessage({ prompt: content, maxOutput: 512, context: 8192, gpuLayers: 0 });
      setActiveGeneration(generationId);
      assistantForGeneration.current.set(generationId, assistantId);
    } catch (cause) {
      setGenerating(false);
      setError(cause instanceof Error ? cause.message : String(cause));
      setMessages((current) => current.filter((message) => message.id !== assistantId));
    }
  }

  return <section className="chat-layout"><div className="chat-header"><div><span className="eyebrow">LOCAL CHAT</span><h2>{engine.loadedModelId ? "Chat de prueba" : "Sin modelo cargado"}</h2></div><div className="chat-runtime"><span className={`status-badge ${engine.loadedModelId ? "success" : "warning"}`}>{engine.loadedModelId ? "Modelo listo" : "Selecciona un GGUF"}</span>{generating && <button className="secondary-button stop-button" onClick={() => void stopGeneration().catch((cause) => setError(String(cause)))}><Square size={14} /> Detener</button>}</div></div><div className="chat-messages">{messages.length === 0 ? <EmptyState icon={<MessageSquare />} title="Prueba tu modelo local" text="Escribe un mensaje y observa el streaming real de llama.cpp. No hay respuestas simuladas." /> : messages.map((message) => <div className={`message-row ${message.role}`} key={message.id}><div className="message-avatar">{message.role === "user" ? <UserRound size={16} /> : <Bot size={16} />}</div><div className="message-bubble">{message.content || (generating ? <span className="typing"><i /> <i /> <i /></span> : "")}</div></div>)}</div><div className="composer"><textarea value={draft} onChange={(event) => setDraft(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) { event.preventDefault(); void send(); } }} placeholder="Escribe un mensaje… · Ctrl + Enter para enviar" rows={2} /><button className="primary-button send-button" onClick={() => void send()} disabled={!draft.trim() || generating}><Send size={17} /></button></div></section>;
}

function CharactersView2() {
  const [characters, setCharacters] = useState<CharacterRecord[]>([]);
  const [editing, setEditing] = useState<CharacterRecord | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!isTauriRuntime()) {
      try { setCharacters(JSON.parse(localStorage.getItem("local-character.desktop.characters") ?? "[]") as CharacterRecord[]); } catch { setCharacters([]); }
      return;
    }
    void listCharacters().then(setCharacters).catch(() => undefined);
  }, []);

  useEffect(() => {
    localStorage.setItem("local-character.desktop.characters", JSON.stringify(characters));
  }, [characters]);

  function startNew() {
    setEditing({ id: crypto.randomUUID(), name: "", description: "", personality: "", greeting: "", createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() });
  }

  async function importCard() {
    if (!isTauriRuntime()) return;
    try {
      const path = await chooseCharacterCard();
      if (!path) return;
      setBusy(true);
      const character = await importCharacterCard(path);
      setCharacters((current) => [character, ...current.filter((item) => item.id !== character.id)]);
    } catch (cause) {
      window.alert(cause instanceof Error ? cause.message : String(cause));
    } finally { setBusy(false); }
  }

  async function save(character: CharacterRecord) {
    const normalized = { ...character, name: character.name.trim() || "Personaje sin nombre", updatedAt: new Date().toISOString() };
    try {
      setBusy(true);
      const saved = isTauriRuntime() ? await persistCharacter(normalized) : normalized;
      setCharacters((current) => [saved, ...current.filter((item) => item.id !== saved.id)]);
      setEditing(null);
    } catch (cause) {
      window.alert(cause instanceof Error ? cause.message : String(cause));
    } finally { setBusy(false); }
  }

  async function remove(id: string) {
    if (!window.confirm("¿Eliminar este personaje local?")) return;
    if (isTauriRuntime()) await persistDeleteCharacter(id).catch(() => undefined);
    setCharacters((current) => current.filter((item) => item.id !== id));
  }

  return <section className="page-grid single-column">
    <div className="page-intro"><div><span className="eyebrow">CHARACTER LIBRARY</span><h2>Personajes</h2><p>Importa Character Cards v2 JSON/PNG o crea una tarjeta compatible con Android.</p></div><div className="hero-actions"><button className="secondary-button" onClick={() => void importCard()} disabled={busy}><Upload size={17} /> Importar Card</button><button className="primary-button" onClick={startNew}><Plus size={17} /> Crear personaje</button></div></div>
    {editing ? <CharacterEditor character={editing} onChange={setEditing} onCancel={() => setEditing(null)} onSave={(character) => void save(character)} /> : characters.length === 0 ? <div className="panel"><EmptyState icon={<UserRound />} title="Tu biblioteca está vacía" text="Añade un personaje o importa una tarjeta desde tu equipo." action={<button className="secondary-button" onClick={startNew}><Plus size={16} /> Crear personaje</button>} /></div> : <div className="character-grid">{characters.map((character) => <CharacterCard key={character.id} character={character} onEdit={() => setEditing(character)} onDelete={() => void remove(character.id)} />)}</div>}
  </section>;
}

function CharactersView() {
  const [characters, setCharacters] = useState<CharacterRecord[]>(() => {
    try {
      return JSON.parse(localStorage.getItem("local-character.desktop.characters") ?? "[]") as CharacterRecord[];
    } catch {
      return [];
    }
  });
  const [editing, setEditing] = useState<CharacterRecord | null>(null);
  const [formOpen, setFormOpen] = useState(false);

  useEffect(() => {
    localStorage.setItem("local-character.desktop.characters", JSON.stringify(characters));
  }, [characters]);

  useEffect(() => {
    if (!isTauriRuntime()) return;
    void listCharacters().then(setCharacters).catch(() => undefined);
  }, []);

  function startNew() {
    setEditing({ id: crypto.randomUUID(), name: "", description: "", personality: "", greeting: "", createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() });
    setFormOpen(true);
  }

  function save(character: CharacterRecord) {
    const normalized = { ...character, name: character.name.trim() || "Personaje sin nombre", updatedAt: new Date().toISOString() };
    setCharacters((current) => [normalized, ...current.filter((item) => item.id !== normalized.id)]);
    if (isTauriRuntime()) void persistCharacter(normalized).catch(() => undefined);
    setEditing(null);
    setFormOpen(false);
  }

  function remove(id: string) {
    setCharacters((current) => current.filter((item) => item.id !== id));
    if (isTauriRuntime()) void persistDeleteCharacter(id).catch(() => undefined);
  }

  return <section className="page-grid single-column"><div className="page-intro"><div><span className="eyebrow">CHARACTER LIBRARY</span><h2>Personajes</h2><p>Crea personajes con personalidad, saludo, descripción y avatar, igual que en la aplicación móvil.</p></div><button className="primary-button" onClick={startNew}><Plus size={17} /> Crear personaje</button></div>{formOpen && editing ? <CharacterEditor character={editing} onChange={setEditing} onCancel={() => { setEditing(null); setFormOpen(false); }} onSave={save} /> : characters.length === 0 ? <div className="panel"><EmptyState icon={<UserRound />} title="Tu biblioteca está vacía" text="Crea tu primer personaje local y asígnale una imagen de perfil desde cualquier carpeta del equipo." action={<button className="secondary-button" onClick={startNew}><Plus size={16} /> Crear personaje</button>} /></div> : <div className="character-grid">{characters.map((character) => <CharacterCard key={character.id} character={character} onEdit={() => { setEditing(character); setFormOpen(true); }} onDelete={() => remove(character.id)} />)}</div>}</section>;
}

function CharacterCard({ character, onEdit, onDelete }: { character: CharacterRecord; onEdit: () => void; onDelete: () => void }) {
  const avatar = localFileUrl(character.avatarPath);
  return <article className="character-card panel"><div className="character-card-heading"><div className="character-avatar">{avatar ? <img src={avatar} alt="" /> : <UserRound size={25} />}</div><div><h3>{character.name}</h3><span>{character.description || "Sin descripción"}</span></div></div><p className="character-preview">{character.personality || "Añade una personalidad para que el personaje tenga una voz propia."}</p><div className="character-card-actions"><button className="secondary-button" onClick={onEdit}>Editar</button><button className="icon-button danger" onClick={onDelete} aria-label="Eliminar personaje"><Trash2 size={16} /></button></div></article>;
}

function CharacterEditor({ character, onChange, onCancel, onSave }: { character: CharacterRecord; onChange: (value: CharacterRecord) => void; onCancel: () => void; onSave: (value: CharacterRecord) => void }) {
  async function pickAvatar() {
    if (!isTauriRuntime()) return;
    const path = await chooseCharacterImage();
    if (path) onChange({ ...character, avatarPath: path });
  }
  const avatar = localFileUrl(character.avatarPath);
  return <div className="panel character-editor"><div className="panel-heading"><div><span className="eyebrow">CHARACTER CARD</span><h3>{character.name ? "Editar personaje" : "Nuevo personaje"}</h3></div><button className="icon-button" onClick={onCancel} aria-label="Cerrar">×</button></div><div className="character-editor-grid"><div className="character-editor-avatar"><div className="character-avatar large">{avatar ? <img src={avatar} alt="" /> : <UserRound size={34} />}</div><button className="secondary-button" onClick={() => void pickAvatar()}><ImagePlus size={16} /> Elegir avatar</button><small>PNG, JPG, WEBP o GIF</small></div><div className="character-fields"><label>Nombre<input value={character.name} onChange={(event) => onChange({ ...character, name: event.target.value })} placeholder="Ej. Sophie" /></label><label>Descripción<input value={character.description} onChange={(event) => onChange({ ...character, description: event.target.value })} placeholder="Quién es este personaje" /></label><label>Personalidad<textarea value={character.personality} onChange={(event) => onChange({ ...character, personality: event.target.value })} rows={3} placeholder="Rasgos, forma de hablar, límites y contexto" /></label><label>Saludo inicial<textarea value={character.greeting} onChange={(event) => onChange({ ...character, greeting: event.target.value })} rows={2} placeholder="El primer mensaje del personaje" /></label></div></div><div className="character-editor-actions"><button className="secondary-button" onClick={onCancel}>Cancelar</button><button className="primary-button" onClick={() => onSave(character)}>Guardar personaje</button></div></div>;
}
function BenchmarksView({ models, hardware }: { models: ModelRecord[]; hardware: HardwareSnapshot | null }) { return <section className="page-grid single-column"><div className="page-intro"><div><span className="eyebrow">MODEL LAB</span><h2>Benchmark</h2><p>Mediciones reproducibles de carga, TTFT, prompt y generación se guardarán localmente.</p></div><button className="primary-button" disabled><Play size={17} /> Ejecutar prueba</button></div><div className="benchmark-grid"><div className="panel"><div className="panel-heading"><h3>Hardware de referencia</h3><Cpu size={18} /></div><RuntimeItem label="CPU" value={hardware?.cpuName ?? "No detectado"} /><RuntimeItem label="RAM" value={formatBytes(hardware?.ramTotalBytes)} /><RuntimeItem label="GPU" value={hardware?.gpus[0]?.name ?? "No detectada"} /></div><div className="panel"><div className="panel-heading"><h3>Historial</h3><BarChart3 size={18} /></div><EmptyState icon={<Activity />} title="Sin benchmarks todavía" text={`${models.length} modelo(s) registrados. Carga uno para habilitar la prueba real.`} /></div></div></section>; }
function BenchmarksView2({ models, hardware }: { models: ModelRecord[]; hardware: HardwareSnapshot | null }) {
  const [selected, setSelected] = useState(models[0]?.id ?? "");
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<GenerationStats | null>(null);
  useEffect(() => { if (!selected && models[0]) setSelected(models[0].id); }, [models, selected]);
  async function execute() {
    if (!selected) return;
    setRunning(true);
    try { setResult(await runBenchmark(selected, Number(localStorage.getItem("local-character.desktop.context") ?? "8192"), Number(localStorage.getItem("local-character.desktop.gpuLayers") ?? "0"))); }
    catch (cause) { window.alert(cause instanceof Error ? cause.message : String(cause)); }
    finally { setRunning(false); }
  }
  return <section className="page-grid single-column"><div className="page-intro"><div><span className="eyebrow">MODEL LAB</span><h2>Benchmark real</h2><p>Ejecuta una prueba corta contra llama.cpp y guarda TTFT y tokens por segundo en SQLite.</p></div><button className="primary-button" onClick={() => void execute()} disabled={running || !selected}><Play size={17} /> {running ? "Ejecutando…" : "Ejecutar prueba"}</button></div><div className="benchmark-grid"><div className="panel"><div className="panel-heading"><h3>Configuración</h3><Cpu size={18} /></div><label>Modelo<select value={selected} onChange={(event) => setSelected(event.target.value)}><option value="">Selecciona un GGUF</option>{models.map((model) => <option key={model.id} value={model.id}>{model.name}</option>)}</select></label><RuntimeItem label="CPU" value={hardware?.cpuName ?? "No detectado"} /><RuntimeItem label="RAM" value={formatBytes(hardware?.ramTotalBytes)} /></div><div className="panel"><div className="panel-heading"><h3>Último resultado</h3><BarChart3 size={18} /></div>{result ? <div className="runtime-row"><RuntimeItem label="Tokens" value={String(result.generatedTokens)} /><RuntimeItem label="Generación" value={formatSpeed(result.generationTokensPerSecond)} /><RuntimeItem label="TTFT" value={result.timeToFirstTokenMs ? `${result.timeToFirstTokenMs.toFixed(0)} ms` : "—"} /></div> : <EmptyState icon={<Activity />} title="Sin benchmark todavía" text="Selecciona un modelo GGUF y ejecuta la prueba." />}</div></div></section>;
}

function SettingsView({ hardware }: { hardware: HardwareSnapshot | null }) {
  const [language, setLanguage] = useState(() => localStorage.getItem("local-character.desktop.language") ?? "es");
  const [theme, setTheme] = useState(() => localStorage.getItem("local-character.desktop.theme") ?? "dark");
  const [context, setContext] = useState(() => Number(localStorage.getItem("local-character.desktop.context") ?? "8192"));
  const [gpuLayers, setGpuLayers] = useState(() => Number(localStorage.getItem("local-character.desktop.gpuLayers") ?? "0"));
  function save(key: string, value: string) { localStorage.setItem(`local-character.desktop.${key}`, value); }
  return <section className="page-grid single-column"><div className="page-intro"><div><span className="eyebrow">PREFERENCES</span><h2>Ajustes</h2><p>Las preferencias se guardan únicamente en este equipo y se aplican a las nuevas conversaciones.</p></div></div><div className="settings-grid"><div className="panel setting-card"><div className="setting-icon"><Settings size={19} /></div><h3>Idioma y apariencia</h3><p>Elige el idioma del catálogo y la densidad visual de la aplicación.</p><div className="setting-control"><label>Idioma del repositorio<select value={language} onChange={(event) => { setLanguage(event.target.value); save("language", event.target.value); }}><option value="es">Español</option><option value="en">English</option><option value="pt">Português</option><option value="fr">Français</option><option value="de">Deutsch</option></select></label><label>Tema<select value={theme} onChange={(event) => { setTheme(event.target.value); save("theme", event.target.value); }}><option value="dark">Oscuro</option><option value="light">Claro (próximamente)</option><option value="system">Seguir sistema</option></select></label></div></div><div className="panel setting-card"><div className="setting-icon"><Gauge size={19} /></div><h3>Generación local</h3><p>Estos controles se usarán al enviar mensajes y permiten ajustar contexto y descarga en GPU.</p><div className="setting-control"><label>Contexto máximo<span className="range-value">{context.toLocaleString()} tokens</span><input type="range" min="1024" max="32768" step="1024" value={context} onChange={(event) => { const value = Number(event.target.value); setContext(value); save("context", String(value)); }} /></label><label>Capas GPU<span className="range-value">{gpuLayers === 0 ? "CPU" : gpuLayers}</span><input type="range" min="0" max="99" value={gpuLayers} onChange={(event) => { const value = Number(event.target.value); setGpuLayers(value); save("gpuLayers", String(value)); }} /></label></div></div><div className="panel setting-card"><div className="setting-icon"><HardDrive size={19} /></div><h3>Datos y privacidad</h3><p>SQLite, personajes, chats y modelos permanecen en rutas locales. Los GGUF no se copian automáticamente.</p><span className="setting-state">{hardware?.os ?? "Windows 10/11 x64"} · Local-first</span></div></div></section>;
}

function DiagnosticsPanel() {
  const [logs, setLogs] = useState<import("./types").EngineLog[]>([]);
  useEffect(() => {
    if (!isTauriRuntime()) return;
    void getEngineLogs().then(setLogs).catch(() => undefined);
  }, []);
  return <section className="page-grid single-column diagnostics-section"><div className="panel setting-card diagnostics-card"><div className="panel-heading"><div><span className="eyebrow">DIAGNOSTICS</span><h3>Diagnóstico del motor</h3></div><button className="secondary-button" onClick={() => void clearEngineLogs().then(() => setLogs([]))}>Limpiar</button></div><p>Salidas de llama.cpp capturadas sin abrir ventanas externas. Estos eventos ayudan a detectar modelos incompatibles.</p><div className="diagnostics-log">{logs.length === 0 ? <span>Sin eventos registrados.</span> : logs.slice(-20).reverse().map((log, index) => <div className="diagnostics-row" key={`${log.timestamp}-${index}`}><code>{log.level}</code><span>{log.message}</span></div>)}</div></div></section>;
}

export default App;
