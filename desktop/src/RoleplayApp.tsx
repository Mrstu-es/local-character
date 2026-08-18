// @ts-nocheck Legacy views remain in this file while the modern chat implementation is active.
import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import {
  ArrowDownToLine, ArrowLeft, Bookmark, Bot, Check, ChevronRight, CircleAlert, Copy, Cpu, Edit3, Flag, FolderOpen, Globe2, Home, ImagePlus,
  Library, MessageCircle, MoreHorizontal, PanelLeftClose, PanelLeftOpen,
  Plus, Play, RefreshCw, RotateCcw, Search, Send, Settings, ShieldAlert, Sparkles, Trash2, Upload, UserRound,
  Users, Volume2, WandSparkles, X,
} from "lucide-react";
import type { CharacterRecord, ChatMessage, ConversationRecord, EngineStatus, GroupRecord, HardwareSnapshot, ModelRecord, ProviderRecord, VoiceModelRecord, VoiceRepositoryRecord } from "./types";
import {
  addModel, chooseCharacterCard, chooseCharacterImage, chooseGguf,
  deleteCharacter, deleteConversation, deleteGroup, deleteMessage, deleteProvider, getHardwareSnapshot, importCharacterCard,
  importCharacterRepositoryUrl, isTauriRuntime, listenToGeneration, listCharacters, listConversations,
  getEngineStatus, listGroups, listMessages, listModels, listProviders, listenToRuntimeState, loadModel, readAvatarData, removeModel,
  saveCharacter, saveConversation, saveGroup, saveMessage, saveProvider, sendChatMessage,
  discoverProviderModels,
  stopGeneration, unloadModel, findSuspiciousMessages, deleteSuspiciousMessages,
  syncCharacterRepository, listCharacterRepositories, listRemoteCharacters, getExploreFilterCatalog, deleteCharacterRepository, setCharacterRepositoryEnabled, installRemoteCharacter,
  listVoiceRepositories, listVoiceModels, syncVoiceRepository as syncVoiceRepositoryApi, deleteVoiceRepository as deleteVoiceRepositoryApi,
} from "./lib/tauri";
import ChatPanelModern from "./chat/ChatPanelModern";
import RoleplayChatScreenModern from "./chat/RoleplayChatScreenModern";

type RoleplayView = "home" | "chats" | "explore" | "characters" | "models" | "settings";
const navItems: Array<{ id: RoleplayView; label: string; icon: typeof Home }> = [
  { id: "home", label: "Inicio", icon: Home },
  { id: "chats", label: "Chats", icon: MessageCircle },
  { id: "explore", label: "Explorar", icon: Globe2 },
  { id: "characters", label: "Personajes", icon: UserRound },
  { id: "models", label: "Modelos y APIs", icon: Library },
  { id: "settings", label: "Ajustes", icon: Settings },
];

const apiCatalog = [
  { id: "openai", label: "OpenAI", endpoint: "https://api.openai.com/v1" },
  { id: "openrouter", label: "OpenRouter", endpoint: "https://openrouter.ai/api/v1" },
  { id: "groq", label: "Groq", endpoint: "https://api.groq.com/openai/v1" },
  { id: "gemini", label: "Google Gemini", endpoint: "https://generativelanguage.googleapis.com/v1beta" },
  { id: "anthropic", label: "Anthropic", endpoint: "https://api.anthropic.com/v1" },
  { id: "mistral", label: "Mistral", endpoint: "https://api.mistral.ai/v1" },
];

const now = () => new Date().toISOString();
const newConversation = (characterId?: string, title = "Nuevo chat", kind = "direct"): ConversationRecord => ({ id: crypto.randomUUID(), characterId, title, pinned: false, archived: false, kind, lastMessagePreview: "", createdAt: now(), updatedAt: now() });
const newGroup = (): GroupRecord => ({ id: crypto.randomUUID(), name: "", description: "", participantIds: [], createdAt: now(), updatedAt: now() });
const formatBytes = (bytes?: number) => { if (!bytes) return "—"; const units = ["B", "KB", "MB", "GB"]; const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1); return `${(bytes / 1024 ** i).toFixed(i > 2 ? 1 : 0)} ${units[i]}`; };
const isLoadingRuntime = (state?: EngineStatus["runtimeState"]) => state === "STARTING" || state === "LOADING_MODEL";
const isUnloadingRuntime = (state?: EngineStatus["runtimeState"]) => state === "STOPPING";
const isReadyRuntime = (state?: EngineStatus["runtimeState"]) => state === "READY" || state === "GENERATING";

export default function RoleplayApp() {
  const [view, setView] = useState<RoleplayView>("home");
  const [collapsed, setCollapsed] = useState(() => window.matchMedia("(max-width: 1180px)").matches);
  const [characters, setCharacters] = useState<CharacterRecord[]>([]);
  const [conversations, setConversations] = useState<ConversationRecord[]>([]);
  const [groups, setGroups] = useState<GroupRecord[]>([]);
  const [models, setModels] = useState<ModelRecord[]>([]);
  const [providers, setProviders] = useState<ProviderRecord[]>([]);
  const [repositories, setRepositories] = useState<import("./types").RepositorySourceRecord[]>([]);
  const [remoteCharacters, setRemoteCharacters] = useState<import("./types").RemoteCharacterRecord[]>([]);
  const [filterCatalog, setFilterCatalog] = useState<import("./types").ExploreFilterCatalog>({ sources: [], languages: [], tags: [], categories: [] });
  const [voiceRepositories, setVoiceRepositories] = useState<VoiceRepositoryRecord[]>([]);
  const [voiceModels, setVoiceModels] = useState<VoiceModelRecord[]>([]);
  const [hardware, setHardware] = useState<HardwareSnapshot | null>(null);
  const [engine, setEngine] = useState<EngineStatus>({ running: false });
  const [activeChat, setActiveChat] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function refresh() {
    if (!isTauriRuntime()) return;
    setBusy(true);
    try {
      const [chars, chats, savedGroups, savedModels, savedProviders, savedRepositories, savedRemoteCharacters, savedFilterCatalog, savedVoiceRepositories, savedVoiceModels, snapshot, runtime] = await Promise.all([listCharacters(), listConversations(), listGroups(), listModels(), listProviders(), listCharacterRepositories(), listRemoteCharacters(), getExploreFilterCatalog(), listVoiceRepositories(), listVoiceModels(), getHardwareSnapshot(), getEngineStatus()]);
      setCharacters(chars); setConversations(chats); setGroups(savedGroups); setModels(savedModels); setProviders(savedProviders); setRepositories(savedRepositories); setRemoteCharacters(savedRemoteCharacters); setFilterCatalog(savedFilterCatalog); setVoiceRepositories(savedVoiceRepositories); setVoiceModels(savedVoiceModels); setHardware(snapshot); setEngine(runtime);
    } catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); } finally { setBusy(false); }
  }
  useEffect(() => { void refresh(); }, []);
  useEffect(() => {
    if (!isTauriRuntime()) return;
    let disposed = false;
    let unlisten: (() => void) | undefined;
    void listenToRuntimeState((status) => setEngine(status)).then((cleanup) => {
      if (disposed) cleanup();
      else unlisten = cleanup;
    }).catch((cause) => setError(cause instanceof Error ? cause.message : String(cause)));
    return () => { disposed = true; unlisten?.(); };
  }, []);
  useEffect(() => { const media = window.matchMedia("(max-width: 1180px)"); const change = (event: MediaQueryListEvent) => setCollapsed(event.matches); media.addEventListener("change", change); return () => media.removeEventListener("change", change); }, []);
  useEffect(() => {
    if (!isTauriRuntime() || (view !== "home" && view !== "chats")) return;
    void listConversations().then(setConversations).catch((cause) => setError(String(cause)));
  }, [view]);

  async function startChat(character: CharacterRecord) {
    const existing = conversations.find((conversation) => conversation.characterId === character.id && conversation.kind === "direct");
    const conversation = existing ?? newConversation(character.id, character.name);
    if (!existing && isTauriRuntime()) await saveConversation(conversation);
    setConversations((current) => existing ? current : [conversation, ...current]);
    setActiveChat(conversation.id); setView("chats");
  }
  async function createGroup(group: GroupRecord) {
    const saved = isTauriRuntime() ? await saveGroup({ ...group, name: group.name.trim() || "Grupo sin nombre", updatedAt: now() }) : group;
    const conversation: ConversationRecord = { id: saved.id, title: saved.name, kind: "group", pinned: false, archived: false, lastMessagePreview: "", createdAt: saved.createdAt, updatedAt: saved.updatedAt };
    if (isTauriRuntime()) await saveConversation(conversation);
    setGroups((current) => [saved, ...current.filter((item) => item.id !== saved.id)]);
    setConversations((current) => [conversation, ...current.filter((item) => item.id !== conversation.id)]);
    setActiveChat(saved.id); setView("chats");
  }
  async function addLocalModel() { const path = await chooseGguf(); if (!path) return; try { const model = await addModel(path); setModels((current) => [model, ...current.filter((item) => item.path !== model.path)]); } catch (cause) { setError(String(cause)); } }
  async function syncRepository(url: string, name?: string) {
    const result = await syncCharacterRepository({ url, name });
    setRepositories((current) => [result.source, ...current.filter((item) => item.id !== result.source.id && item.url !== result.source.url)]);
    setRemoteCharacters((current) => {
      const keys = new Set(result.items.map((item) => `${item.providerId}:${item.remoteId}`));
      return [...result.items, ...current.filter((item) => !keys.has(`${item.providerId}:${item.remoteId}`))];
    });
    setFilterCatalog(await getExploreFilterCatalog());
    return result;
  }
  async function removeRepository(id: string) {
    await deleteCharacterRepository(id);
    setRepositories((current) => current.filter((item) => item.id !== id));
    setRemoteCharacters((current) => current.filter((item) => item.sourceId !== id));
    setFilterCatalog(await getExploreFilterCatalog());
  }
  async function toggleRepository(id: string, enabled: boolean) {
    await setCharacterRepositoryEnabled(id, enabled);
    setRepositories((current) => current.map((item) => item.id === id ? { ...item, enabled } : item));
    if (enabled) setRemoteCharacters(await listRemoteCharacters());
    else setRemoteCharacters((current) => current.filter((item) => item.sourceId !== id));
    setFilterCatalog(await getExploreFilterCatalog());
  }
  async function syncVoiceRepository(url: string, name?: string) {
    const result = await syncVoiceRepositoryApi(url, name);
    setVoiceRepositories((current) => [result.repository, ...current.filter((item) => item.id !== result.repository.id && item.endpoint !== result.repository.endpoint)]);
    setVoiceModels((current) => [...result.voices, ...current.filter((item) => item.repositoryId !== result.repository.id)]);
    return result;
  }
  async function removeVoiceRepository(id: string) {
    await deleteVoiceRepositoryApi(id);
    setVoiceRepositories((current) => current.filter((item) => item.id !== id));
    setVoiceModels((current) => current.filter((item) => item.repositoryId !== id));
  }
  async function installRemote(providerId: string, remoteId: string) {
    const character = await installRemoteCharacter(providerId, remoteId);
    setCharacters((current) => [character, ...current.filter((item) => item.id !== character.id)]);
    setRemoteCharacters((current) => current.map((item) => item.providerId === providerId && item.remoteId === remoteId ? { ...item, installedCharacterId: character.id } : item));
    return character;
  }
  async function load(id: string) {
    const model = models.find((item) => item.id === id);
    if (!model || !model.exists || isLoadingRuntime(engine.runtimeState) || isUnloadingRuntime(engine.runtimeState)) return;
    setError(null);
    setEngine((current) => ({ ...current, loadedModelId: model.id, loadedModelPath: model.path, loadedModelName: model.name, runtimeState: "LOADING_MODEL", error: undefined }));
    try {
      // New installations prefer the discrete GPU when one is detected. An
      // explicit saved value (including 0 for CPU-only) always wins.
      const savedGpuLayers = Number(localStorage.getItem("local-character.desktop.gpuLayers") ?? (hardware?.gpus?.length ? "-1" : "0"));
      const gpuLayers = Number.isFinite(savedGpuLayers) ? Math.max(-1, Math.min(999, Math.trunc(savedGpuLayers))) : 0;
      const status = await loadModel(id, gpuLayers);
      setEngine(status);
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : String(cause);
      setEngine((current) => ({ ...current, runtimeState: "ERROR", error: message }));
      setError(message);
    }
  }

  return <div className={`app-shell ${collapsed ? "sidebar-collapsed" : ""}`}>
    <aside className="sidebar"><div className="brand-row"><div className="brand-mark"><WandSparkles size={18} /></div>{!collapsed && <div><strong>Local Character</strong><span>Roleplay con IA</span></div>}<button className="icon-button sidebar-toggle" onClick={() => setCollapsed((value) => !value)} aria-label="Contraer menú">{collapsed ? <PanelLeftOpen size={18} /> : <PanelLeftClose size={18} />}</button></div><nav className="main-nav">{navItems.map(({ id, label, icon: Icon }) => <button key={id} className={`nav-item ${view === id ? "active" : ""}`} onClick={() => setView(id)} title={collapsed ? label : undefined}><Icon size={19} /><span>{!collapsed && label}</span></button>)}</nav>{!collapsed && <div className={`sidebar-footer runtime-${engine.runtimeState?.toLowerCase() ?? "stopped"}`} aria-live="polite"><div className={`backend-dot ${isReadyRuntime(engine.runtimeState) ? "ready" : isLoadingRuntime(engine.runtimeState) ? "loading" : engine.runtimeState === "ERROR" ? "error" : ""}`} />{isLoadingRuntime(engine.runtimeState) && <RefreshCw size={14} className="spin runtime-spinner" aria-hidden="true" />}<div><span>Motor de roleplay</span><strong>{isLoadingRuntime(engine.runtimeState) ? `Cargando ${engine.loadedModelName ?? "modelo"}…` : isReadyRuntime(engine.runtimeState) ? `${engine.loadedModelName ?? "Modelo"} listo` : engine.runtimeState === "ERROR" ? "Error al cargar modelo" : "Carga un modelo"}</strong></div></div>}</aside>
    <main className="main-content"><header className="topbar"><div><span className="eyebrow">ROLEPLAY CON PERSONAJES</span><h1>{navItems.find((item) => item.id === view)?.label}</h1></div><div className="topbar-actions"><span className="release-pill">0.1.0-alpha</span><button className="icon-button" onClick={() => void refresh()} disabled={busy} aria-label="Actualizar"><RefreshCw size={17} className={busy ? "spin" : ""} /></button></div></header>{error && <div className="error-banner"><CircleAlert size={17} /><span>{error}</span><button onClick={() => setError(null)}>Cerrar</button></div>}
      {view === "home" && <HomeView characters={characters} conversations={conversations} groups={groups} onChat={startChat} onOpenChat={(id) => { setActiveChat(id); setView("chats"); }} onOpenChats={() => setView("chats")} onOpenCharacters={() => setView("characters")} />}
      {view === "chats" && <ChatsView characters={characters} conversations={conversations} groups={groups} providers={providers} engine={engine} activeChat={activeChat} setActiveChat={setActiveChat} onNewChat={startChat} onCreateGroup={createGroup} onDeleteGroup={async (id) => { if (isTauriRuntime()) { await deleteGroup(id); await deleteConversation(id); } setGroups((current) => current.filter((group) => group.id !== id)); setConversations((current) => current.filter((conversation) => conversation.id !== id)); if (activeChat === id) setActiveChat(null); }} onOpenCharacters={() => setView("characters")} />}
      {view === "explore" && <ExploreView remoteCharacters={remoteCharacters} filterCatalog={filterCatalog} onInstall={installRemote} onOpenSettings={() => setView("settings")} />}
      {view === "characters" && <CharactersView characters={characters} setCharacters={setCharacters} voices={voiceModels} onChat={startChat} onRepositoryAdded={syncRepository} onExplore={() => setView("explore")} />}
      {view === "models" && <ModelsApisPage models={models} providers={providers} engine={engine} hardware={hardware} onAddModel={() => void addLocalModel()} onLoad={load} onUnload={async () => { try { await unloadModel(); } catch (cause) { const message = cause instanceof Error ? cause.message : String(cause); setEngine((current) => ({ ...current, runtimeState: "ERROR", error: message })); setError(message); } }} onRemove={async (id) => { await removeModel(id); setModels((current) => current.filter((item) => item.id !== id)); }} onProviders={setProviders} />}
      {view === "settings" && <RoleplaySettings providers={providers} setProviders={setProviders} hardware={hardware} repositories={repositories} onSyncRepository={syncRepository} onDeleteRepository={removeRepository} onToggleRepository={toggleRepository} voiceRepositories={voiceRepositories} voiceModels={voiceModels} onSyncVoiceRepository={syncVoiceRepository} onDeleteVoiceRepository={removeVoiceRepository} />}
    </main>
  </div>;
}

function HomeView({ characters, conversations: rawConversations, groups, onChat, onOpenChat, onOpenChats, onOpenCharacters }: { characters: CharacterRecord[]; conversations: ConversationRecord[]; groups: GroupRecord[]; onChat: (character: CharacterRecord) => void; onOpenChat: (id: string) => void; onOpenChats: () => void; onOpenCharacters: () => void }) {
  const names = useMemo(() => new Map(characters.map((character) => [character.id, character])), [characters]);
  const conversations = rawConversations.filter((conversation) => (conversation.kind?.trim().toLowerCase() || "direct") !== "direct" || Boolean(conversation.characterId && names.has(conversation.characterId))).filter((conversation) => !(conversation.title.trim().toLowerCase() === "nuevo chat" && !conversation.characterId && !conversation.lastMessagePreview.trim()));
  return <section className="page-grid"><div className="hero-card panel-accent"><div className="hero-copy"><span className="eyebrow">TU ESPACIO DE ROLEPLAY</span><h2>Historias vivas.<br /><em>Personajes tuyos.</em></h2><p>Crea personajes, forma grupos y conversa con ellos usando tu modelo local o una API compatible.</p><div className="hero-actions"><button className="primary-button" onClick={onOpenCharacters}><UserRound size={17} /> Ver personajes</button><button className="secondary-button" onClick={onOpenChats}><MessageCircle size={17} /> Ver chats</button></div></div><div className="hero-orbit"><div className="orbit-ring ring-one" /><div className="orbit-ring ring-two" /><div className="orbit-core"><Sparkles size={30} /></div></div></div><div className="panel wide-panel"><div className="panel-heading"><div><span className="eyebrow">CONTINUAR</span><h3>Tus conversaciones</h3></div><button className="text-button" onClick={onOpenChats}>Ver todos <ChevronRight size={15} /></button></div>{conversations.length === 0 && groups.length === 0 ? <EmptyState icon={<MessageCircle />} title="Todavía no hay chats" text="Abre un personaje para iniciar tu primera historia o crea un grupo." action={<button className="secondary-button" onClick={onOpenCharacters}><Plus size={16} /> Empezar a rolear</button>} /> : <div className="conversation-list">{conversations.slice(0, 6).map((conversation) => { const character = conversation.characterId ? names.get(conversation.characterId) : undefined; return <button className="conversation-row" key={conversation.id} onClick={() => onOpenChat(conversation.id)}><Avatar path={character?.avatarPath} name={character?.name ?? conversation.title} /><div><strong>{character?.name ?? conversation.title}</strong><span>{conversation.lastMessagePreview || "Sin mensajes todavía"}</span></div><ChevronRight size={16} /></button>; })}</div>}</div></section>;
}

function ChatsView({ characters, conversations, groups, providers, engine, activeChat, setActiveChat, onNewChat, onCreateGroup, onDeleteGroup, onOpenCharacters }: { characters: CharacterRecord[]; conversations: ConversationRecord[]; groups: GroupRecord[]; providers: ProviderRecord[]; engine: EngineStatus; activeChat: string | null; setActiveChat: (id: string) => void; onNewChat: (character: CharacterRecord) => void; onCreateGroup: (group: GroupRecord) => Promise<void>; onDeleteGroup: (id: string) => Promise<void>; onOpenCharacters: () => void }) {
  const [query, setQuery] = useState("");
  const [showGroup, setShowGroup] = useState(false);
  const names = useMemo(() => new Map(characters.map((character) => [character.id, character])), [characters]);
  const filtered = conversations.filter((conversation) => ((conversation.kind?.trim().toLowerCase() || "direct") !== "direct" || Boolean(conversation.characterId && names.has(conversation.characterId))) && !(conversation.title.trim().toLowerCase() === "nuevo chat" && !conversation.characterId && !conversation.lastMessagePreview.trim()) && `${conversation.title} ${conversation.lastMessagePreview}`.toLowerCase().includes(query.toLowerCase()));
  if (activeChat) return <RoleplayChatScreenV2 chatId={activeChat} characters={characters} conversations={conversations} groups={groups} providers={providers} engine={engine} onBack={() => setActiveChat("")} onOpenCharacters={onOpenCharacters} />;
  return <section className="page-grid single-column"><div className="page-intro"><div><span className="eyebrow">ROLEPLAY</span><h2>Chats</h2><p>Elige una historia existente o empieza una conversación con uno de tus personajes.</p></div><div className="hero-actions"><button className="secondary-button" onClick={() => setShowGroup(true)}><Users size={17} /> Nuevo grupo</button><button className="primary-button" onClick={onOpenCharacters}><Plus size={17} /> Nuevo chat</button></div></div><div className="toolbar"><div className="search-box"><Search size={17} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar chats" /></div><span className="toolbar-count">{filtered.length + groups.length} conversaciones</span></div><div className="chat-list-panel panel">{filtered.length === 0 && groups.length === 0 ? <EmptyState icon={<MessageCircle />} title="No hay conversaciones" text="Crea un chat desde Personajes o forma un grupo con el botón +." /> : <>{filtered.map((conversation) => { const character = conversation.characterId ? names.get(conversation.characterId) : undefined; return <button className={`conversation-row ${activeChat === conversation.id ? "selected" : ""}`} key={conversation.id} onClick={() => setActiveChat(conversation.id)}><Avatar path={character?.avatarPath} name={character?.name ?? conversation.title} /><div><strong>{character?.name ?? conversation.title}</strong><span>{conversation.lastMessagePreview || "Sin mensajes todavía"}</span></div><ChevronRight size={16} /></button>; })}{groups.length > 0 && <><h3 className="list-section-title">Grupos</h3>{groups.map((group) => <div className={`conversation-row ${activeChat === group.id ? "selected" : ""}`} key={group.id} role="button" tabIndex={0} onClick={() => setActiveChat(group.id)} onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") setActiveChat(group.id); }}><div className="group-avatar"><Users size={19} /></div><div><strong>{group.name}</strong><span>{group.participantIds.length} personajes · {group.description || "Grupo de roleplay"}</span></div><button className="icon-button danger" onClick={(event) => { event.stopPropagation(); void onDeleteGroup(group.id); }} aria-label="Eliminar grupo"><Trash2 size={16} /></button></div>)}</>}</>}</div>{activeChat && <ChatPanel chatId={activeChat} characters={characters} conversations={conversations} groups={groups} providers={providers} engine={engine} />}{showGroup && <GroupDialog characters={characters} onCancel={() => setShowGroup(false)} onSave={async (group) => { await onCreateGroup(group); setShowGroup(false); }} />}</section>;
}

function ChatPanel({ chatId, characters, conversations, groups, providers, engine, onBack, prefill, onPrefillConsumed }: { chatId: string; characters: CharacterRecord[]; conversations: ConversationRecord[]; groups: GroupRecord[]; providers: ProviderRecord[]; engine: EngineStatus; onBack?: () => void; prefill?: string; onPrefillConsumed?: () => void }) {
  return <ChatPanelModern chatId={chatId} characters={characters} conversations={conversations} groups={groups} providers={providers} engine={engine} prefill={prefill} onPrefillConsumed={onPrefillConsumed} />;
  // @ts-ignore legacy implementation retained for migration; modern panel returned above
  const conversation = conversations.find((item) => item.id === chatId);
  const group = groups.find((item) => item.id === chatId);
  // @ts-ignore legacy implementation retained for migration
  const character = conversation?.characterId ? characters.find((item) => item.id === conversation.characterId) : undefined;
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showBrainPicker, setShowBrainPicker] = useState(false);
  const [showActions, setShowActions] = useState(false);
  const [selectedProviderId, setSelectedProviderId] = useState<string | null>(null);
  const [selectedMessage, setSelectedMessage] = useState<ChatMessage | null>(null);
  const [actionMode, setActionMode] = useState(false);
  const activeGeneration = useRef<string | null>(null);
  const assistantByGeneration = useRef(new Map<string, string>());
  const assistantContentByGeneration = useRef(new Map<string, string>());
  useEffect(() => { if (prefill === undefined) return; setDraft(prefill); onPrefillConsumed?.(); }, [prefill, onPrefillConsumed]);
  useEffect(() => { if (!isTauriRuntime() || !conversation) { setMessages([]); return; } void listMessages(conversation.id).then((stored) => setMessages(stored.map((item) => ({ id: item.id, role: item.role, content: item.content, createdAt: Date.parse(item.createdAt) || Date.now() })))).catch((cause) => setError(String(cause))); }, [chatId, conversation?.id]);
  useEffect(() => { if (!isTauriRuntime()) return; let cleanups: (() => void)[] = []; void listenToGeneration((event) => { if (event.generationId !== activeGeneration.current) return; const id = assistantByGeneration.current.get(event.generationId) ?? event.generationId; const content = `${assistantContentByGeneration.current.get(event.generationId) ?? ""}${event.text}`; assistantContentByGeneration.current.set(event.generationId, content); setMessages((current) => current.map((message) => message.id === id ? { ...message, content } : message)); if (conversation) void saveMessage({ id, conversationId: conversation.id, role: "assistant", content, pinned: false, metadataJson: "{}", createdAt: now() }); }, (event) => { if (event.generationId === activeGeneration.current) { setGenerating(false); activeGeneration.current = null; } }, (event) => { setGenerating(false); setError(String(event)); }).then((items) => { cleanups = items; }); return () => cleanups.forEach((cleanup) => cleanup()); }, [chatId, conversation?.id]);
  async function send() {
    const content = draft.trim();
    if (!content || generating || !conversation) return;
    const userId = crypto.randomUUID();
    const assistantId = crypto.randomUUID();
    const user: ChatMessage = { id: userId, role: "user", content, createdAt: Date.now() };
    setMessages((current) => [...current, user, { id: assistantId, role: "assistant", content: "", createdAt: Date.now() }]);
    setDraft("");
    if (isTauriRuntime()) {
      await saveMessage({ id: userId, conversationId: conversation.id, role: "user", content, pinned: false, metadataJson: "{}", createdAt: now() });
      await saveMessage({ id: assistantId, conversationId: conversation.id, role: "assistant", content: "", pinned: false, metadataJson: "{}", createdAt: now() });
    }
    setGenerating(true);
    const prompt = character ? `${character.systemPrompt ?? ""}\n${character.personality}\n${character.scenario ?? ""}\nUsuario: ${content}` : content;
    const provider = providers.find((item) => item.id === selectedProviderId) ?? providers.find((item) => item.enabled && item.endpoint && item.modelName);
    if (provider && (!engine.executable || selectedProviderId || providers.length === 1)) {
      try {
        const base = provider.endpoint!.replace(/\/+$/, "");
        const endpoint = base.endsWith("/chat/completions") ? base : `${base}/chat/completions`;
        const history = [...messages, user].map((message) => ({ role: message.role, content: message.content }));
        const system = character?.systemPrompt || character?.personality;
        const response = await fetch(endpoint, { method: "POST", headers: { "Content-Type": "application/json", ...(provider.apiKey ? { Authorization: `Bearer ${provider.apiKey}` } : {}) }, body: JSON.stringify({ model: provider.modelName, messages: system ? [{ role: "system", content: system }, ...history] : history, max_tokens: 512 }) });
        if (!response.ok) throw new Error(`La API respondió ${response.status}`);
        const payload = await response.json() as { choices?: Array<{ message?: { content?: string } }> };
        const reply = payload.choices?.[0]?.message?.content?.trim();
        if (!reply) throw new Error("La API no devolvió contenido");
        setMessages((current) => current.map((message) => message.id === assistantId ? { ...message, content: reply } : message));
        if (isTauriRuntime()) await saveMessage({ id: assistantId, conversationId: conversation.id, role: "assistant", content: reply, pinned: false, metadataJson: "{}", createdAt: now() });
      } catch (cause) { setMessages((current) => current.filter((message) => message.id !== assistantId)); setError(String(cause)); }
      finally { setGenerating(false); }
      return;
    }
    try {
      if (!engine.executable) throw new Error("Carga un modelo GGUF o configura una API compatible en Modelos y APIs.");
      const generationId = await sendChatMessage({ prompt, maxOutput: 512, context: Number(localStorage.getItem("local-character.desktop.context") ?? "8192"), gpuLayers: Number(localStorage.getItem("local-character.desktop.gpuLayers") ?? "-1") });
      assistantByGeneration.current.set(generationId, assistantId); activeGeneration.current = generationId;
    } catch (cause) { setGenerating(false); setMessages((current) => current.filter((message) => message.id !== assistantId)); setError(String(cause)); }
  }
  return <div className="panel chat-panel"><div className="chat-header"><div className="chat-identity">{character ? <Avatar path={character.avatarPath} name={character.name} /> : <div className="group-avatar"><Users size={19} /></div>}<div><span className="eyebrow">{group ? "CHAT GRUPAL" : "CHAT"}</span><h3>{character?.name ?? group?.name ?? conversation?.title ?? "Conversación"}</h3><small>{generating ? "Está escribiendo…" : "Tu historia permanece en este dispositivo"}</small></div></div><div className="chat-runtime"><button className="icon-button" title="Más acciones"><MoreHorizontal size={18} /></button></div></div>{error && <div className="inline-error">{error}</div>}<div className="chat-messages compact">{messages.length === 0 ? <EmptyState icon={<Sparkles />} title={character?.greeting ? "Saludo inicial" : "Empieza la historia"} text={character?.greeting || "Escribe un mensaje para comenzar este roleplay."} /> : messages.map((message) => <div className={`message-row ${message.role}`} key={message.id}><div className="message-avatar">{message.role === "user" ? <UserRound size={16} /> : <Bot size={16} />}</div><div className="message-bubble">{message.content || (generating ? "…" : "")}</div></div>)}</div><div className="composer"><textarea value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="Escribe un mensaje…" rows={2} onKeyDown={(event) => { if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) { event.preventDefault(); void send(); } }} /><button className="primary-button send-button" onClick={() => void send()} disabled={!draft.trim() || generating}><Send size={17} /></button>{generating && <button className="icon-button danger" onClick={() => void stopGeneration()}><X size={17} /></button>}</div></div>;
}

function RoleplayChatScreenV2({ chatId, characters, conversations, groups, providers, engine, onBack, onOpenCharacters }: { chatId: string; characters: CharacterRecord[]; conversations: ConversationRecord[]; groups: GroupRecord[]; providers: ProviderRecord[]; engine: EngineStatus; onBack: () => void; onOpenCharacters: () => void }) {
  return <RoleplayChatScreenModern chatId={chatId} characters={characters} conversations={conversations} groups={groups} providers={providers} engine={engine} onBack={onBack} onOpenCharacters={onOpenCharacters} />;
  // @ts-ignore legacy implementation retained for migration; modern screen returned above
  const conversation = conversations.find((item) => item.id === chatId);
  const group = groups.find((item) => item.id === chatId);
  // @ts-ignore legacy implementation retained for migration
  const character = conversation?.characterId ? characters.find((item) => item.id === conversation.characterId) : undefined;
  const [showMemory, setShowMemory] = useState(false);
  const [showBrain, setShowBrain] = useState(false);
  const [showMessageActions, setShowMessageActions] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [selectedProviderId, setSelectedProviderId] = useState<string | null>(null);
  const [prefill, setPrefill] = useState<string | undefined>(undefined);
  const selectedProvider = providers.find((item) => item.id === selectedProviderId);
  const announce = (message: string) => { setNotice(message); window.setTimeout(() => setNotice(null), 2200); };
  const speakGreeting = () => { if ("speechSynthesis" in window) { window.speechSynthesis.cancel(); window.speechSynthesis.speak(new SpeechSynthesisUtterance(character?.greeting || `Hola, soy ${character?.name || "tu personaje"}.`)); } announce("Reproduciendo la voz del personaje"); };
  return <section className="dedicated-chat-screen"><div className="dedicated-chat-topbar"><button className="icon-button" onClick={onBack} aria-label="Volver a chats"><ArrowLeft size={20} /></button><Avatar path={character?.avatarPath} name={character?.name ?? group?.name ?? "Chat"} /><div className="dedicated-chat-title"><strong>{character?.name ?? group?.name ?? conversation?.title ?? "Conversación"}</strong><span>{group ? "Chat grupal" : selectedProvider ? `${selectedProvider.name} · ${selectedProvider.modelName}` : engine.executable ? "Procesamiento local" : "Selecciona una IA"}</span></div><button className="icon-button" onClick={onOpenCharacters} title="Abrir personaje"><UserRound size={18} /></button><button className="icon-button" onClick={() => setShowMemory(true)} title="Memoria"><Bookmark size={18} /></button><button className="icon-button" onClick={() => setPrefill("Regenera la última respuesta manteniendo el contexto.")} title="Regenerar"><RotateCcw size={18} /></button></div><div className="dedicated-chat-actions"><button className="secondary-button" onClick={() => setShowBrain(true)}><Bot size={16} /> Motor de IA</button><button className="secondary-button" onClick={() => setShowMemory(true)}><Bookmark size={16} /> Memoria</button><button className="secondary-button" onClick={onOpenCharacters}><UserRound size={16} /> Personaje</button><span className="dedicated-chat-privacy">{selectedProvider ? "☁ Procesamiento online" : "🔒 Procesamiento local"}</span></div><div className="dedicated-chat-body"><ChatPanel chatId={chatId} characters={characters} conversations={conversations} groups={groups} providers={selectedProvider ? [selectedProvider] : providers} engine={engine} prefill={prefill} onPrefillConsumed={() => setPrefill(undefined)} /></div>{notice && <div className="chat-notice">{notice}</div>}{showBrain && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) setShowBrain(false); }}><div className="modal-card brain-picker" role="dialog" aria-modal="true"><div className="panel-heading"><div><span className="eyebrow">MOTOR DE IA</span><h3>Selecciona un modelo</h3></div><button className="icon-button" onClick={() => setShowBrain(false)}><X size={18} /></button></div><button className={`brain-option ${selectedProviderId === null ? "selected" : ""}`} onClick={() => { setSelectedProviderId(null); setShowBrain(false); }}><Bot size={17} /><span><strong>Modelo local</strong><small>{engine.executable ? "GGUF cargado" : "Sin modelo local cargado"}</small></span></button>{providers.filter((item) => item.enabled && item.modelName).map((item) => <button className={`brain-option ${selectedProviderId === item.id ? "selected" : ""}`} key={item.id} onClick={() => { setSelectedProviderId(item.id); setShowBrain(false); }}><Globe2 size={17} /><span><strong>{item.name}</strong><small>{item.modelName}</small></span></button>)}</div></div>}{showMemory && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) setShowMemory(false); }}><div className="modal-card" role="dialog" aria-modal="true"><div className="panel-heading"><div><span className="eyebrow">MEMORIA LOCAL</span><h3>{character?.name ?? group?.name ?? "Conversación"}</h3></div><button className="icon-button" onClick={() => setShowMemory(false)}><X size={18} /></button></div><p>Los recuerdos, relaciones y eventos permanecen en este dispositivo.</p><div className="memory-tabs"><button className="selected">Recuerdos</button><button>Relaciones</button><button>Eventos</button><button>Preferencias</button></div><EmptyState icon={<Bookmark />} title="Memoria disponible" text="Usa el menú de acciones de un mensaje para guardarlo como recuerdo." /></div></div>}{showMessageActions && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) setShowMessageActions(false); }}><div className="modal-card" role="dialog" aria-modal="true"><div className="panel-heading"><div><span className="eyebrow">MENSAJES</span><h3>Acciones disponibles</h3></div><button className="icon-button" onClick={() => setShowMessageActions(false)}><X size={18} /></button></div><p>Haz clic derecho sobre cualquier mensaje para abrir sus acciones.</p><div className="message-action-list"><button onClick={() => { setShowMessageActions(false); announce("Selecciona un mensaje con clic derecho para copiarlo"); }}><Copy size={16} /> Copiar mensaje</button><button onClick={() => { setShowMessageActions(false); speakGreeting(); }}><Volume2 size={16} /> Leer en voz alta</button><button onClick={() => { setShowMessageActions(false); setShowMemory(true); }}><Bookmark size={16} /> Guardar en memoria</button><button onClick={() => { setShowMessageActions(false); announce("Puedes eliminar mensajes desde su menú contextual"); }}><Trash2 size={16} /> Eliminar mensaje</button></div></div></div>}</section>;
}

function RoleplayChatScreen({ chatId, characters, conversations, groups, providers, engine, onBack, onOpenCharacters }: { chatId: string; characters: CharacterRecord[]; conversations: ConversationRecord[]; groups: GroupRecord[]; providers: ProviderRecord[]; engine: EngineStatus; onBack: () => void; onOpenCharacters: () => void }) {
  const conversation = conversations.find((item) => item.id === chatId);
  const group = groups.find((item) => item.id === chatId);
  const character = conversation?.characterId ? characters.find((item) => item.id === conversation.characterId) : undefined;
  const [showMemory, setShowMemory] = useState(false);
  const [showBrain, setShowBrain] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [selectedProviderId, setSelectedProviderId] = useState<string | null>(null);
  const [prefill, setPrefill] = useState<string | undefined>(undefined);
  const selectedProvider = providers.find((item) => item.id === selectedProviderId) ?? providers.find((item) => item.enabled && item.modelName);
  const announce = (message: string) => { setNotice(message); window.setTimeout(() => setNotice(null), 2200); };
  return <section className="dedicated-chat-screen"><div className="dedicated-chat-topbar"><button className="icon-button" onClick={onBack} aria-label="Volver a chats"><ArrowLeft size={20} /></button><Avatar path={character?.avatarPath} name={character?.name ?? group?.name ?? "Chat"} /><div className="dedicated-chat-title"><strong>{character?.name ?? group?.name ?? conversation?.title ?? "Conversación"}</strong><span>{group ? "Chat grupal" : selectedProvider ? `${selectedProvider.name} · ${selectedProvider.modelName}` : engine.executable ? "Procesamiento local" : "Selecciona una IA"}</span></div><button className="icon-button" onClick={() => announce("Personaje seleccionado") } title="Abrir personaje"><UserRound size={18} /></button><button className="icon-button" onClick={() => setShowMemory(true)} title="Memoria"><Bookmark size={18} /></button><button className="icon-button" onClick={() => announce("La conversación se actualizará al enviar el siguiente mensaje")} title="Regenerar"><RotateCcw size={18} /></button></div><div className="dedicated-chat-actions"><button className="secondary-button" onClick={() => setShowBrain(true)}><Bot size={16} /> Motor de IA</button><button className="secondary-button" onClick={() => setShowMemory(true)}><Bookmark size={16} /> Memoria</button><button className="secondary-button" onClick={() => announce("Las opciones del personaje están disponibles en Personajes")}><MoreHorizontal size={16} /> Opciones del personaje</button><span className="dedicated-chat-privacy">{selectedProvider ? "☁ Procesamiento online" : "🔒 Procesamiento local"}</span></div><div className="dedicated-chat-body"><ChatPanel chatId={chatId} characters={characters} conversations={conversations} groups={groups} providers={selectedProvider ? [selectedProvider] : providers} engine={engine} /></div>{notice && <div className="chat-notice">{notice}</div>}{showBrain && <div className="modal-backdrop"><div className="modal-card brain-picker"><div className="panel-heading"><div><span className="eyebrow">MOTOR DE IA</span><h3>Selecciona un modelo</h3></div><button className="icon-button" onClick={() => setShowBrain(false)}><X size={18} /></button></div><button className={`brain-option ${selectedProviderId === null ? "selected" : ""}`} onClick={() => { setSelectedProviderId(null); setShowBrain(false); }}><Bot size={17} /><span><strong>Modelo local</strong><small>{engine.executable ? "GGUF cargado" : "Sin modelo local cargado"}</small></span></button>{providers.filter((item) => item.enabled && item.modelName).map((item) => <button className={`brain-option ${selectedProviderId === item.id ? "selected" : ""}`} key={item.id} onClick={() => { setSelectedProviderId(item.id); setShowBrain(false); }}><Globe2 size={17} /><span><strong>{item.name}</strong><small>{item.modelName}</small></span></button>)}</div></div>}{showMemory && <div className="modal-backdrop"><div className="modal-card"><div className="panel-heading"><div><span className="eyebrow">MEMORIA LOCAL</span><h3>{character?.name ?? group?.name ?? "Conversación"}</h3></div><button className="icon-button" onClick={() => setShowMemory(false)}><X size={18} /></button></div><p>Los recuerdos, relaciones y eventos permanecen en este dispositivo.</p><div className="memory-tabs"><button className="selected">Recuerdos</button><button>Relaciones</button><button>Eventos</button><button>Preferencias</button></div><EmptyState icon={<Bookmark />} title="Memoria disponible" text="Mantén pulsado un mensaje para guardarlo como recuerdo." /></div></div>}</section>;
}

function GroupDialog({ characters, onCancel, onSave }: { characters: CharacterRecord[]; onCancel: () => void; onSave: (group: GroupRecord) => Promise<void> }) {
  const [group, setGroup] = useState<GroupRecord>(newGroup());
  const toggle = (id: string) => setGroup((current) => ({ ...current, participantIds: current.participantIds.includes(id) ? current.participantIds.filter((item) => item !== id) : [...current.participantIds, id] }));
  return <div className="modal-backdrop"><div className="modal-card"><div className="panel-heading"><div><span className="eyebrow">CHAT GRUPAL</span><h3>Crear grupo</h3></div><button className="icon-button" onClick={onCancel}><X size={18} /></button></div><label>Nombre del grupo<input value={group.name} onChange={(event) => setGroup({ ...group, name: event.target.value })} placeholder="Ej. La taberna" /></label><label>Descripción<textarea value={group.description} onChange={(event) => setGroup({ ...group, description: event.target.value })} rows={2} placeholder="Contexto compartido" /></label><span className="form-label">Personajes</span><div className="participant-picker">{characters.map((character) => <button key={character.id} className={group.participantIds.includes(character.id) ? "selected" : ""} onClick={() => toggle(character.id)}><Avatar path={character.avatarPath} name={character.name} /><span>{character.name}</span>{group.participantIds.includes(character.id) && <Check size={15} />}</button>)}</div><div className="modal-actions"><button className="secondary-button" onClick={onCancel}>Cancelar</button><button className="primary-button" disabled={!group.name.trim() || group.participantIds.length === 0} onClick={() => void onSave(group)}>Crear grupo</button></div></div></div>;
}

function ExploreView({ remoteCharacters, filterCatalog, onInstall, onOpenSettings }: { remoteCharacters: import("./types").RemoteCharacterRecord[]; filterCatalog: import("./types").ExploreFilterCatalog; onInstall: (providerId: string, remoteId: string) => Promise<CharacterRecord>; onOpenSettings: () => void }) {
  const [sourceFilter, setSourceFilter] = useState("all");
  const [languageFilter, setLanguageFilter] = useState("all");
  const [tagFilter, setTagFilter] = useState("all");
  const [categoryFilter, setCategoryFilter] = useState("all");
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState("recent");
  const [safeOnly, setSafeOnly] = useState(true);
  const [installing, setInstalling] = useState("");
  const [notice, setNotice] = useState("");
  useEffect(() => { if (sourceFilter !== "all" && !filterCatalog.sources.some((item) => item.id === sourceFilter)) setSourceFilter("all"); }, [filterCatalog.sources, sourceFilter]);
  useEffect(() => { if (languageFilter !== "all" && !filterCatalog.languages.some((item) => item.id === languageFilter)) setLanguageFilter("all"); }, [filterCatalog.languages, languageFilter]);
  useEffect(() => { if (tagFilter !== "all" && !filterCatalog.tags.some((item) => item.id === tagFilter)) setTagFilter("all"); }, [filterCatalog.tags, tagFilter]);
  useEffect(() => { if (categoryFilter !== "all" && !filterCatalog.categories.some((item) => item.id === categoryFilter)) setCategoryFilter("all"); }, [filterCatalog.categories, categoryFilter]);
  const results = useMemo(() => remoteCharacters.filter((item) => {
    const haystack = [item.name, item.description, item.author ?? "", ...(item.tags ?? []), ...(item.categories ?? [])].join(" ").toLowerCase();
    return (sourceFilter === "all" || item.sourceId === sourceFilter) && haystack.includes(query.trim().toLowerCase()) && (languageFilter === "all" || item.language === languageFilter) && (tagFilter === "all" || (item.tags ?? []).includes(tagFilter)) && (categoryFilter === "all" || (item.categories ?? []).includes(categoryFilter)) && (!safeOnly || !item.isNsfw);
  }).sort((a, b) => sort === "name" ? a.name.localeCompare(b.name) : sort === "popular" ? (b.downloadCount ?? 0) - (a.downloadCount ?? 0) : String(b.updatedAt ?? "").localeCompare(String(a.updatedAt ?? ""))), [remoteCharacters, sourceFilter, languageFilter, tagFilter, categoryFilter, query, safeOnly, sort]);
  async function install(item: import("./types").RemoteCharacterRecord) {
    const key = `${item.providerId}:${item.remoteId}`;
    setInstalling(key); setNotice("");
    try { await onInstall(item.providerId, item.remoteId); setNotice(`${item.name} está listo en Personajes.`); }
    catch (cause) { setNotice(cause instanceof Error ? cause.message : String(cause)); }
    finally { setInstalling(""); }
  }
  return <section className="page-grid single-column"><div className="page-intro"><div><span className="eyebrow">CATÁLOGO DE PERSONAJES</span><h2>Explorar</h2><p>Descubre personajes de tus fuentes activas. Administra las fuentes desde Ajustes.</p></div><button className="secondary-button" onClick={onOpenSettings}><Settings size={17} /> Gestionar fuentes</button></div><div className="character-filters panel"><div className="search-box"><Search size={17} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar nombre, autor o etiqueta" /></div><select value={sourceFilter} onChange={(event) => setSourceFilter(event.target.value)}><option value="all">Todas las fuentes</option>{filterCatalog.sources.map((source) => <option value={source.id} key={source.id}>{source.label} · {source.count}</option>)}</select><select value={languageFilter} onChange={(event) => setLanguageFilter(event.target.value)}><option value="all">Todos los idiomas</option>{filterCatalog.languages.map((language) => <option value={language.id} key={language.id}>{language.label} · {language.count}</option>)}</select><select value={tagFilter} onChange={(event) => setTagFilter(event.target.value)}><option value="all">Todas las etiquetas</option>{filterCatalog.tags.map((tag) => <option value={tag.id} key={tag.id}>{tag.label} · {tag.count}</option>)}</select><select value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}><option value="all">Todas las categorías</option>{filterCatalog.categories.map((category) => <option value={category.id} key={category.id}>{category.label} · {category.count}</option>)}</select><select value={sort} onChange={(event) => setSort(event.target.value)}><option value="recent">Más recientes</option><option value="name">Nombre A-Z</option><option value="popular">Más descargados</option></select><label className="filter-toggle"><input type="checkbox" checked={safeOnly} onChange={(event) => setSafeOnly(event.target.checked)} /> Solo SFW</label><span className="toolbar-count">{results.length} personajes</span></div>{notice && <div className="setting-state" role="status">{notice}</div>}{results.length === 0 ? <div className="panel"><EmptyState icon={<Globe2 />} title="Sin personajes" text="Activa o añade una fuente desde Ajustes para cargar el catálogo." action={<button className="primary-button" onClick={onOpenSettings}><Settings size={16} /> Abrir Ajustes</button>} /></div> : <div className="remote-character-grid">{results.map((item) => { const key = `${item.providerId}:${item.remoteId}`; return <article className="remote-character-card panel" key={key}><Avatar path={item.avatarUrl} name={item.name} large /><div className="remote-character-content"><div className="remote-character-heading"><div><h3>{item.name}</h3><span>{item.author ? `por ${item.author}` : "Personaje de roleplay"}</span></div>{item.language && <span className="status-badge">{item.language.toUpperCase()}</span>}</div><p>{item.description || "Personaje listo para rolear."}</p><div className="remote-character-tags">{[...(item.tags ?? []), ...(item.categories ?? [])].slice(0, 6).map((tag) => <span key={tag}>{tag}</span>)}</div><div className="character-card-actions"><button className="primary-button" onClick={() => void install(item)} disabled={Boolean(item.installedCharacterId) || installing === key}>{installing === key ? "Instalando…" : item.installedCharacterId ? "✓ Instalado" : "Instalar personaje"}</button><a className="secondary-button" href={item.sourceUrl} target="_blank" rel="noreferrer">Ver fuente</a></div></div></article>; })}</div>}</section>;
}

function LegacyExploreSourcesView({ repositories, remoteCharacters, filterCatalog, onSync, onDelete, onToggle, onInstall }: { repositories: import("./types").RepositorySourceRecord[]; remoteCharacters: import("./types").RemoteCharacterRecord[]; filterCatalog: import("./types").ExploreFilterCatalog; onSync: (url: string, name?: string) => Promise<unknown>; onDelete: (id: string) => Promise<void>; onToggle: (id: string, enabled: boolean) => Promise<void>; onInstall: (providerId: string, remoteId: string) => Promise<CharacterRecord> }) {
  const builtIn = [
    { id: "builtin-ai-character-cards", providerId: "ai_character_cards", name: "AI Character Cards", url: "https://aicharactercards.com/", enabled: true, status: "READY", statusMessage: "Fuente pública integrada", updatedAt: "" },
    { id: "builtin-chub", providerId: "chub", name: "Chub", url: "https://chub.ai/", enabled: false, status: "UNSUPPORTED", statusMessage: "Provider pendiente de adapter Android", updatedAt: "" },
    { id: "builtin-character-tavern", providerId: "character_tavern", name: "Character Tavern", url: "https://character-tavern.com/", enabled: false, status: "UNSUPPORTED", statusMessage: "Provider pendiente de adapter Android", updatedAt: "" },
  ];
  const sources = useMemo(() => [...builtIn, ...repositories], [repositories]);
  const activeSources = useMemo(() => [builtIn[0], ...filterCatalog.sources.map((option) => repositories.find((source) => source.id === option.id)).filter(Boolean)], [filterCatalog.sources, repositories]);
  const [sourceFilter, setSourceFilter] = useState("all");
  const [languageFilter, setLanguageFilter] = useState("all");
  const [tagFilter, setTagFilter] = useState("all");
  const [categoryFilter, setCategoryFilter] = useState("all");
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState("recent");
  const [safeOnly, setSafeOnly] = useState(true);
  const [sourceUrl, setSourceUrl] = useState("");
  const [sourceName, setSourceName] = useState("");
  const [busy, setBusy] = useState(false);
  const [installing, setInstalling] = useState("");
  const [notice, setNotice] = useState("");
  const sourceMatches = (item: import("./types").RemoteCharacterRecord) => sourceFilter === "all" || item.sourceId === sourceFilter || item.providerId === sourceFilter;
  const languages = filterCatalog.languages;
  const tags = filterCatalog.tags;
  const categories = filterCatalog.categories;
  useEffect(() => { if (languageFilter !== "all" && !languages.some((item) => item.id === languageFilter)) setLanguageFilter("all"); }, [languageFilter, languages]);
  useEffect(() => { if (tagFilter !== "all" && !tags.some((item) => item.id === tagFilter)) setTagFilter("all"); }, [tagFilter, tags]);
  useEffect(() => { if (categoryFilter !== "all" && !categories.some((item) => item.id === categoryFilter)) setCategoryFilter("all"); }, [categoryFilter, categories]);
  const results = useMemo(() => remoteCharacters.filter((item) => {
    const haystack = [item.name, item.description, item.author ?? "", (item.tags ?? []).join(" ")].join(" ").toLowerCase();
    return sourceMatches(item) && haystack.includes(query.trim().toLowerCase()) && (languageFilter === "all" || item.language === languageFilter) && (tagFilter === "all" || (item.tags ?? []).includes(tagFilter)) && (categoryFilter === "all" || (item.categories ?? []).includes(categoryFilter)) && (!safeOnly || !item.isNsfw);
  }).sort((a, b) => sort === "name" ? a.name.localeCompare(b.name) : sort === "popular" ? (b.downloadCount ?? 0) - (a.downloadCount ?? 0) : String(b.updatedAt ?? "").localeCompare(String(a.updatedAt ?? ""))), [remoteCharacters, sourceFilter, languageFilter, tagFilter, categoryFilter, query, safeOnly, sort]);
  async function sync() {
    const url = sourceUrl.trim();
    if (!url) return;
    setBusy(true); setNotice("");
    try {
      const result = await onSync(url, sourceName.trim() || undefined);
      setSourceUrl(""); setSourceName("");
      setNotice((result as { probe?: { message?: string } })?.probe?.message ?? "Fuente sincronizada.");
    } catch (cause) { setNotice(cause instanceof Error ? cause.message : String(cause)); }
    finally { setBusy(false); }
  }
  async function install(item: import("./types").RemoteCharacterRecord) {
    setInstalling(item.providerId + ":" + item.remoteId); setNotice("");
    try { await onInstall(item.providerId, item.remoteId); setNotice(item.name + " instalado para usarlo sin conexión."); }
    catch (cause) { setNotice(cause instanceof Error ? cause.message : String(cause)); }
    finally { setInstalling(""); }
  }
  return <section className="page-grid single-column"><div className="page-intro"><div><span className="eyebrow">CATÁLOGO FEDERADO</span><h2>Explorar personajes</h2><p>Las fuentes se consultan de forma aislada. El catálogo y sus filtros se guardan localmente; los chats nunca se envían a los repositorios.</p></div><button className="primary-button" onClick={() => { setSourceUrl("https://aicharactercards.com/"); setSourceName("AI Character Cards"); }}><Plus size={17} /> Añadir fuente</button></div><div className="panel repository-sync-panel"><div className="panel-heading"><div><span className="eyebrow">FUENTES</span><h3>Fuentes compatibles</h3></div><span className="toolbar-count">{sources.length} fuentes</span></div><div className="repository-source-list">{sources.map((source) => <div className="repository-source-row" key={source.id}><div><strong>{source.name}</strong><span>{source.statusMessage || source.url}</span></div><span className={"status-badge " + (source.status === "READY" ? "success" : source.status === "ERROR" ? "danger" : "warning")}>{source.status}</span>{!source.id.startsWith("builtin-") && <><button className="secondary-button" onClick={() => void onToggle(source.id, !source.enabled)}>{source.enabled ? "Desactivar" : "Activar"}</button><button className="icon-button danger" onClick={() => void onDelete(source.id)} aria-label="Eliminar fuente"><Trash2 size={15} /></button></>}</div>)}</div><div className="repository-add-grid"><input value={sourceName} onChange={(event) => setSourceName(event.target.value)} placeholder="Nombre opcional de la fuente" /><input value={sourceUrl} onChange={(event) => setSourceUrl(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") void sync(); }} placeholder="https://aicharactercards.com/ o repository.json" type="url" /><button className="primary-button" onClick={() => void sync()} disabled={!sourceUrl.trim() || busy}>{busy ? "Sincronizando…" : "Comprobar y sincronizar"}</button></div>{notice && <div className="setting-state">{notice}</div>}</div><div className="character-filters panel"><div className="search-box"><Search size={17} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar nombre, autor o etiqueta" /></div><select value={sourceFilter} onChange={(event) => setSourceFilter(event.target.value)}><option value="all">Todas las fuentes</option>{activeSources.map((source) => <option value={source.id === "builtin-ai-character-cards" ? source.providerId : source.id} key={source.id}>{source.name}</option>)}</select><select value={languageFilter} onChange={(event) => setLanguageFilter(event.target.value)}><option value="all">Todos los idiomas</option>{languages.map((language) => <option value={language.id} key={language.id}>{language.label}</option>)}</select><select value={tagFilter} onChange={(event) => setTagFilter(event.target.value)}><option value="all">Todas las etiquetas</option>{tags.map((tag) => <option value={tag.id} key={tag.id}>{tag.label}</option>)}</select><select value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}><option value="all">Todas las categorías</option>{categories.map((category) => <option value={category.id} key={category.id}>{category.label}</option>)}</select><select value={sort} onChange={(event) => setSort(event.target.value)}><option value="recent">Más recientes</option><option value="name">Nombre A-Z</option><option value="popular">Más descargados</option></select><label className="filter-toggle"><input type="checkbox" checked={safeOnly} onChange={(event) => setSafeOnly(event.target.checked)} /> Solo SFW</label><span className="toolbar-count">{results.length} personajes</span></div>{results.length === 0 ? <div className="panel"><EmptyState icon={<Globe2 />} title="Sin resultados sincronizados" text="Añade una fuente compatible o actualiza una fuente existente para construir el catálogo." /></div> : <div className="remote-character-grid">{results.map((item) => <article className="remote-character-card panel" key={item.providerId + ":" + item.remoteId}><Avatar path={item.avatarUrl} name={item.name} large /><div className="remote-character-content"><div className="remote-character-heading"><div><h3>{item.name}</h3><span>{item.author ? "por " + item.author : item.providerId}</span></div>{item.language && <span className="status-badge">{item.language.toUpperCase()}</span>}</div><p>{item.description || "Personaje de roleplay"}</p><div className="remote-character-tags">{(item.tags ?? []).slice(0, 5).map((tag) => <span key={tag}>{tag}</span>)}</div><div className="character-card-actions"><button className="primary-button" onClick={() => void install(item)} disabled={Boolean(item.installedCharacterId) || installing === item.providerId + ":" + item.remoteId}>{installing === item.providerId + ":" + item.remoteId ? "Instalando…" : item.installedCharacterId ? "✓ Instalado" : "Instalar para chatear"}</button><a className="secondary-button" href={item.sourceUrl} target="_blank" rel="noreferrer">Fuente</a></div></div></article>)}</div>}</section>;
}

function CharactersView({ characters, setCharacters, voices, onChat, onRepositoryAdded, onExplore }: { characters: CharacterRecord[]; setCharacters: (value: CharacterRecord[] | ((current: CharacterRecord[]) => CharacterRecord[])) => void; voices: VoiceModelRecord[]; onChat: (character: CharacterRecord) => void; onRepositoryAdded?: (url: string, name?: string) => Promise<unknown>; onExplore?: () => void }) {
  const [editing, setEditing] = useState<CharacterRecord | null>(null);
  const [busy, setBusy] = useState(false);
  const [repositoryUrl, setRepositoryUrl] = useState("");
  const [showRepositoryDialog, setShowRepositoryDialog] = useState(false);
  const [query, setQuery] = useState("");
  const [tagFilter, setTagFilter] = useState("all");
  const [languageFilter, setLanguageFilter] = useState("all");
  const [sort, setSort] = useState("recent");
  const allowNsfw = localStorage.getItem("local-character.desktop.nsfw") === "true";
  const availableTags = useMemo(() => [...new Set(characters.flatMap((item) => item.tags ?? []).map((tag) => tag.trim()).filter(Boolean))].sort((left, right) => left.localeCompare(right)), [characters]);
  const languageTags: Record<string, string[]> = { es: ["es", "español", "spanish"], en: ["en", "english", "inglés"], pt: ["pt", "português", "portuguese"], fr: ["fr", "français", "french"] };
  const filteredCharacters = useMemo(() => characters
    .filter((character) => {
      const tags = (character.tags ?? []).map((tag) => tag.toLowerCase());
      const haystack = `${character.name} ${character.description} ${character.personality} ${tags.join(" ")}`.toLowerCase();
      const languageMatches = languageFilter === "all" || (languageTags[languageFilter] ?? []).some((language) => tags.includes(language));
      const nsfwCharacter = tags.some((tag) => tag === "nsfw" || tag.includes("nsfw"));
      return haystack.includes(query.trim().toLowerCase()) && (tagFilter === "all" || tags.includes(tagFilter.toLowerCase())) && languageMatches && (allowNsfw || !nsfwCharacter);
    })
    .sort((left, right) => sort === "name" ? left.name.localeCompare(right.name) : right.updatedAt.localeCompare(left.updatedAt)), [allowNsfw, characters, languageFilter, query, sort, tagFilter]);
  async function importCard() { const path = await chooseCharacterCard(); if (!path) return; setBusy(true); try { const character = await importCharacterCard(path); setCharacters((current) => [character, ...current.filter((item) => item.id !== character.id)]); } catch (cause) { window.alert(String(cause)); } finally { setBusy(false); } }
  async function importRepository() { const url = repositoryUrl.trim(); if (!url) return; setBusy(true); try { if (onRepositoryAdded) { const result = await onRepositoryAdded(url); const message = (result as { probe?: { message?: string } })?.probe?.message ?? "Fuente sincronizada."; window.alert(message); } else { const imported = await importCharacterRepositoryUrl(url); setCharacters((current) => [...imported, ...current.filter((item) => !imported.some((entry) => entry.id === item.id))]); } setRepositoryUrl(""); setShowRepositoryDialog(false); } catch (cause) { window.alert(cause instanceof Error ? cause.message : String(cause)); } finally { setBusy(false); } }
  async function save(character: CharacterRecord) { const normalized = { ...character, name: character.name.trim() || "Personaje sin nombre", updatedAt: now() }; const saved = isTauriRuntime() ? await saveCharacter(normalized) : normalized; setCharacters((current) => [saved, ...current.filter((item) => item.id !== saved.id)]); setEditing(null); }
  async function remove(id: string) { const character = characters.find((item) => item.id === id); if (!character || !window.confirm(`¿Eliminar a ${character.name}?\n\nTambién se eliminarán sus conversaciones privadas, mensajes y recuerdos asociados. Los grupos se conservarán sin este participante. Esta acción no se puede deshacer.`)) return; try { if (isTauriRuntime()) await deleteCharacter(id); setCharacters((current) => current.filter((item) => item.id !== id)); } catch (cause) { window.alert(cause instanceof Error ? cause.message : String(cause)); } }
  return <section className="page-grid single-column"><div className="page-intro"><div><span className="eyebrow">BIBLIOTECA DE ROLEPLAY</span><h2>Personajes</h2><p>Crea personajes, importa Character Cards y añade repositorios por URL.</p></div><div className="hero-actions"><button className="secondary-button" onClick={() => void importCard()} disabled={busy}><Upload size={17} /> Importar Card</button><button className="primary-button" onClick={() => setEditing({ id: crypto.randomUUID(), name: "", description: "", personality: "", greeting: "", createdAt: now(), updatedAt: now() })}><Plus size={17} /> Crear personaje</button></div></div><div className="character-filters panel"><div className="search-box"><Search size={17} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar personajes" aria-label="Buscar personajes" /></div><select value={languageFilter} onChange={(event) => setLanguageFilter(event.target.value)} aria-label="Filtrar por idioma"><option value="all">Todos los idiomas</option><option value="es">Español</option><option value="en">English</option><option value="pt">Português</option><option value="fr">Français</option></select><select value={tagFilter} onChange={(event) => setTagFilter(event.target.value)} aria-label="Filtrar por etiqueta"><option value="all">Todas las etiquetas</option>{availableTags.map((tag) => <option key={tag} value={tag}>{tag}</option>)}</select><select value={sort} onChange={(event) => setSort(event.target.value)} aria-label="Ordenar personajes"><option value="recent">Más recientes</option><option value="name">Nombre A-Z</option></select><span className="toolbar-count">{filteredCharacters.length} personajes</span></div>{editing ? <RoleplayCharacterEditor character={editing} voices={voices} onChange={setEditing} onCancel={() => setEditing(null)} onSave={(value) => void save(value)} /> : filteredCharacters.length === 0 ? <div className="panel"><EmptyState icon={<UserRound />} title={characters.length === 0 ? "Aún no tienes personajes" : "No hay coincidencias"} text={characters.length === 0 ? "Crea uno o importa un repositorio para empezar a rolear." : "Prueba otro texto, idioma o etiqueta."} /></div> : <div className="character-grid">{filteredCharacters.map((character) => <CharacterCardRoleplay key={character.id} character={character} onChat={() => onChat(character)} onEdit={() => setEditing(character)} onDelete={() => void remove(character.id)} />)}</div>}{showRepositoryDialog && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) setShowRepositoryDialog(false); }}><div className="modal-card" role="dialog" aria-modal="true" aria-label="Añadir repositorio de personajes"><div className="panel-heading"><div><span className="eyebrow">REPOSITORIO</span><h3>Añadir repositorio</h3></div><button className="icon-button" onClick={() => setShowRepositoryDialog(false)} aria-label="Cerrar"><X size={18} /></button></div><p className="modal-help">Pega la URL pública de un JSON con una lista de personajes, `characters` o `cards`.</p><label>URL del repositorio<input autoFocus value={repositoryUrl} onChange={(event) => setRepositoryUrl(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") void importRepository(); }} placeholder="https://ejemplo.com/personajes.json" type="url" /></label><div className="modal-actions"><button className="secondary-button" onClick={() => setShowRepositoryDialog(false)}>Cancelar</button><button className="primary-button" onClick={() => void importRepository()} disabled={!repositoryUrl.trim() || busy}>{busy ? "Descargando…" : "Descargar personajes"}</button></div></div></div>}</section>;
}

function CharacterCardRoleplay({ character, onChat, onEdit, onDelete }: { character: CharacterRecord; onChat: () => void; onEdit: () => void; onDelete: () => void }) { return <article className="character-card panel"><div className="character-card-heading"><Avatar path={character.avatarPath} name={character.name} large /><div><h3>{character.name}</h3><span>{character.description || "Personaje de roleplay"}</span></div></div><p className="character-preview">{character.personality || "Sin personalidad descrita todavía."}</p><div className="character-card-actions"><button className="primary-button" onClick={onChat}><MessageCircle size={16} /> Chatear</button><button className="secondary-button" onClick={onEdit}><Edit3 size={16} /> Editar</button><button className="icon-button danger" onClick={onDelete} aria-label="Eliminar"><Trash2 size={16} /></button></div></article>; }

function RoleplayCharacterEditor({ character, voices, onChange, onCancel, onSave }: { character: CharacterRecord; voices: VoiceModelRecord[]; onChange: (value: CharacterRecord) => void; onCancel: () => void; onSave: (value: CharacterRecord) => void }) {
  async function pickAvatar() { const path = await chooseCharacterImage(); if (path) onChange({ ...character, avatarPath: path }); }
  return <div className="panel character-editor"><div className="panel-heading"><div><span className="eyebrow">PERSONAJE</span><h3>{character.name ? "Editar personaje" : "Crear personaje"}</h3></div><button className="icon-button" onClick={onCancel}><X size={18} /></button></div><div className="character-editor-grid"><div className="character-editor-avatar"><Avatar path={character.avatarPath} name={character.name} large /><button className="secondary-button" onClick={() => void pickAvatar()}><ImagePlus size={16} /> Elegir imagen</button><small>La vista previa se carga desde el archivo local.</small></div><div className="character-fields"><label>Nombre<input value={character.name} onChange={(event) => onChange({ ...character, name: event.target.value })} placeholder="Ej. Sophie" /></label><label>Descripción<input value={character.description} onChange={(event) => onChange({ ...character, description: event.target.value })} placeholder="Quién es este personaje" /></label><label>Personalidad<textarea value={character.personality} onChange={(event) => onChange({ ...character, personality: event.target.value })} rows={3} placeholder="Rasgos y forma de hablar" /></label><label>Escenario<textarea value={character.scenario ?? ""} onChange={(event) => onChange({ ...character, scenario: event.target.value })} rows={2} placeholder="Dónde ocurre el roleplay" /></label><label>Saludo inicial<textarea value={character.greeting} onChange={(event) => onChange({ ...character, greeting: event.target.value })} rows={2} placeholder="Primer mensaje" /></label><label>Voz del personaje<select value={character.voiceId ?? ""} onChange={(event) => onChange({ ...character, voiceId: event.target.value || undefined })}><option value="">Voz predeterminada del sistema</option>{voices.map((voice) => <option key={voice.id} value={voice.name}>{voice.name}{voice.language ? ` · ${voice.language.toUpperCase()}` : ""}</option>)}</select></label></div></div><div className="character-editor-actions"><button className="secondary-button" onClick={onCancel}>Cancelar</button><button className="primary-button" onClick={() => onSave(character)} disabled={!character.name.trim()}>Guardar personaje</button></div></div>;
}

function ModelsApisPage({ models, providers, engine, hardware, onAddModel, onLoad, onUnload, onRemove, onProviders }: { models: ModelRecord[]; providers: ProviderRecord[]; engine: EngineStatus; hardware: HardwareSnapshot | null; onAddModel: () => void; onLoad: (id: string) => Promise<void>; onUnload: () => Promise<void>; onRemove: (id: string) => Promise<void>; onProviders: (providers: ProviderRecord[]) => void }) {
  const [showProvider, setShowProvider] = useState(false);
  const [providerKind, setProviderKind] = useState(apiCatalog[0].id);
  const [displayName, setDisplayName] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const definition = apiCatalog.find((item) => item.id === providerKind) ?? apiCatalog[0];
  async function addProvider() {
    if (!apiKey.trim()) return;
    const draft: ProviderRecord = { id: crypto.randomUUID(), kind: definition.id, name: displayName.trim() || definition.label, endpoint: definition.endpoint, apiKey: apiKey.trim(), modelName: "", availableModels: [], enabled: true, createdAt: now(), updatedAt: now() };
    setBusy(true); setError(null);
    try {
      const availableModels = await discoverProviderModels(draft);
      const saved = await saveProvider({ ...draft, availableModels, modelName: availableModels[0], updatedAt: now() });
      onProviders([saved, ...providers.filter((item) => item.id !== saved.id)]); setShowProvider(false); setApiKey(""); setDisplayName("");
    } catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); } finally { setBusy(false); }
  }
  async function refreshProvider(item: ProviderRecord) {
    setBusy(true); setError(null);
    try {
      const availableModels = await discoverProviderModels(item);
      const saved = await saveProvider({ ...item, availableModels, modelName: item.modelName && availableModels.includes(item.modelName) ? item.modelName : availableModels[0], updatedAt: now() });
      onProviders(providers.map((current) => current.id === saved.id ? saved : current));
    } catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); } finally { setBusy(false); }
  }
  async function selectProviderModel(item: ProviderRecord, modelName: string) { const saved = { ...item, modelName, updatedAt: now() }; if (isTauriRuntime()) await saveProvider(saved); onProviders(providers.map((current) => current.id === saved.id ? saved : current)); }
  const loadingModel = isLoadingRuntime(engine.runtimeState);
  const unloadingModel = isUnloadingRuntime(engine.runtimeState);
  const activeModel = engine.loadedModelId ? models.find((item) => item.id === engine.loadedModelId) : undefined;
  const runtimeName = engine.loadedModelName ?? activeModel?.name ?? "modelo local";
  const runtimeTitle = loadingModel ? `Cargando ${runtimeName}…` : unloadingModel ? `Descargando ${runtimeName}…` : engine.runtimeState === "ERROR" ? "Error al cargar modelo" : activeModel?.name ?? "Ningún modelo local cargado";
  const runtimeLabel = loadingModel ? "Cargando…" : unloadingModel ? "Descargando…" : isReadyRuntime(engine.runtimeState) ? "Listo" : engine.runtimeState === "ERROR" ? "Error · Reintentar" : "API o GGUF pendiente";
  return <section className="page-grid single-column"><div className="page-intro"><div><span className="eyebrow">MOTORES DE ROLEPLAY</span><h2>Modelos y APIs</h2><p>Los modelos de cada API se descubren automáticamente a partir de tu clave.</p></div><div className="hero-actions"><button className="secondary-button" onClick={() => setShowProvider(true)}><Globe2 size={17} /> Añadir API</button><button className="primary-button" onClick={onAddModel} disabled={loadingModel || unloadingModel}><Plus size={17} /> Añadir GGUF</button></div></div><div className={`panel model-runtime-summary runtime-summary-${engine.runtimeState?.toLowerCase() ?? "stopped"}`} aria-live="polite" aria-busy={loadingModel || unloadingModel}><div className="panel-heading"><div><span className="eyebrow">MOTOR ACTUAL</span><h3>{runtimeTitle}</h3><span className="runtime-state-copy">{loadingModel ? "Preparando el motor local…" : unloadingModel ? "Liberando el modelo actual…" : isReadyRuntime(engine.runtimeState) ? "Listo para conversar" : engine.runtimeState === "ERROR" ? (engine.error ?? "No se pudo cargar el modelo") : "Selecciona un GGUF para comenzar"}</span></div><span className={`status-badge ${isReadyRuntime(engine.runtimeState) ? "success" : engine.runtimeState === "ERROR" ? "danger" : loadingModel || unloadingModel ? "warning" : "warning"}`}>{loadingModel || unloadingModel ? <><RefreshCw size={13} className="spin" /> {runtimeLabel}</> : engine.runtimeState === "ERROR" ? "Error" : runtimeLabel}</span></div><div className="runtime-row"><RuntimeItem label="CPU" value={hardware?.cpuName ?? "Detectando"} /><RuntimeItem label="RAM" value={formatBytes(hardware?.ramAvailableBytes)} /><RuntimeItem label="GPU" value={hardware?.gpus[0]?.name ?? "No detectada"} /></div></div><div className="model-grid">{models.map((model) => { const isThisLoading = loadingModel && engine.loadedModelId === model.id; const isThisUnloading = unloadingModel && engine.loadedModelId === model.id; const isThisActive = isReadyRuntime(engine.runtimeState) && engine.loadedModelId === model.id; return <article className={`model-card ${isThisActive ? "loaded" : ""} ${isThisLoading ? "loading" : ""}`} key={model.id} aria-busy={isThisLoading || isThisUnloading}><div className="model-card-top"><div className="model-icon large"><Bot size={21} /></div><div className="model-title"><h3>{model.name}</h3><span>{model.architecture ?? "GGUF"} · {formatBytes(model.sizeBytes)}</span></div>{isThisLoading && <RefreshCw size={16} className="spin model-loading-icon" aria-label={`Cargando ${model.name}`} />}{isThisUnloading && <RefreshCw size={16} className="spin model-loading-icon" aria-label={`Descargando ${model.name}`} />}{isThisActive && <span className="model-ready-label">✓ En uso</span>}</div><div className="model-path">{model.path}</div><div className="model-card-actions">{isThisLoading ? <button className="primary-button" disabled aria-label={`Cargando ${model.name}`}><RefreshCw size={16} className="spin" /> Cargando…</button> : isThisUnloading ? <button className="secondary-button" disabled aria-label={`Descargando ${model.name}`}><RefreshCw size={16} className="spin" /> Descargando…</button> : isThisActive ? <button className="secondary-button" onClick={() => void onUnload()} disabled={unloadingModel}><ArrowDownToLine size={16} /> {unloadingModel ? "Descargando…" : "Descargar"}</button> : <button className="primary-button" onClick={() => void onLoad(model.id)} disabled={!model.exists || loadingModel || unloadingModel}><Play size={16} /> {engine.runtimeState === "ERROR" && engine.loadedModelId === model.id ? "Reintentar" : "Cargar"}</button>}<button className="icon-button danger" onClick={() => void onRemove(model.id)} disabled={loadingModel || unloadingModel} aria-label="Eliminar referencia"><Trash2 size={16} /></button></div></article>; })}</div>{providers.length > 0 && <div className="provider-grid">{providers.map((item) => <article className="panel provider-card" key={item.id}><div className="setting-icon"><Globe2 size={19} /></div><h3>{item.name}</h3><span>{item.kind} · {item.availableModels?.length ?? 0} modelos disponibles</span><select value={item.modelName ?? ""} onChange={(event) => void selectProviderModel(item, event.target.value)} disabled={!item.availableModels?.length}><option value="">Selecciona un modelo</option>{(item.availableModels ?? []).map((model) => <option key={model} value={model}>{model}</option>)}</select><button className="secondary-button provider-refresh" onClick={() => void refreshProvider(item)} disabled={busy || loadingModel || unloadingModel}><RefreshCw size={14} /> Actualizar modelos</button><button className="icon-button danger" onClick={() => void deleteProvider(item.id).then(() => onProviders(providers.filter((current) => current.id !== item.id)))} aria-label="Eliminar API"><Trash2 size={16} /></button></article>)}</div>}{error && <div className="inline-error">{error}</div>}{showProvider && <div className="modal-backdrop"><div className="modal-card"><div className="panel-heading"><div><span className="eyebrow">PROVEEDOR ONLINE</span><h3>Añadir API</h3></div><button className="icon-button" onClick={() => setShowProvider(false)}><X size={18} /></button></div><p className="modal-help">Escribe el nombre que quieras y tu API key. La aplicación consultará automáticamente todos los modelos disponibles.</p><label>Proveedor<select value={providerKind} onChange={(event) => setProviderKind(event.target.value)}>{apiCatalog.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}</select></label><label>Nombre visible<input value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder={definition.label} /></label><label>API key<input type="password" value={apiKey} onChange={(event) => setApiKey(event.target.value)} placeholder="Pega aquí tu clave" /></label>{error && <div className="inline-error">{error}</div>}<div className="modal-actions"><button className="secondary-button" onClick={() => setShowProvider(false)}>Cancelar</button><button className="primary-button" onClick={() => void addProvider()} disabled={!apiKey.trim() || busy}>{busy ? "Consultando modelos…" : "Guardar y descubrir modelos"}</button></div></div></div>}</section>;
}

function ModelsApisView({ models, providers, engine, hardware, onAddModel, onLoad, onUnload, onRemove, onProviders }: { models: ModelRecord[]; providers: ProviderRecord[]; engine: EngineStatus; hardware: HardwareSnapshot | null; onAddModel: () => void; onLoad: (id: string) => Promise<void>; onUnload: () => Promise<void>; onRemove: (id: string) => Promise<void>; onProviders: (providers: ProviderRecord[]) => void }) {
  const [showProvider, setShowProvider] = useState(false);
  const [provider, setProvider] = useState<ProviderRecord>({ id: crypto.randomUUID(), kind: "openai", name: "", endpoint: apiCatalog[0].endpoint, apiKey: "", modelName: "", availableModels: [], enabled: true, createdAt: now(), updatedAt: now() });
  const [providerBusy, setProviderBusy] = useState(false);
  const [providerError, setProviderError] = useState<string | null>(null);
  function selectProviderKind(kind: string) { const definition = apiCatalog.find((item) => item.id === kind) ?? apiCatalog[0]; setProvider((current) => ({ ...current, kind: definition.id, endpoint: definition.endpoint, name: current.name || definition.label })); }
  async function discover(providerToSave: ProviderRecord) { setProviderBusy(true); setProviderError(null); try { const models = isTauriRuntime() ? await discoverProviderModels(providerToSave) : []; const saved = isTauriRuntime() ? await saveProvider({ ...providerToSave, availableModels: models, modelName: providerToSave.modelName || models[0], updatedAt: now() }) : { ...providerToSave, availableModels: models, modelName: providerToSave.modelName || models[0] }; onProviders([saved, ...providers.filter((item) => item.id !== saved.id)]); setProvider(saved); setShowProvider(false); } catch (cause) { setProviderError(cause instanceof Error ? cause.message : String(cause)); } finally { setProviderBusy(false); } }
  async function refreshProvider(item: ProviderRecord) { setProviderBusy(true); setProviderError(null); try { const models = await discoverProviderModels(item); const saved = await saveProvider({ ...item, availableModels: models, modelName: item.modelName && models.includes(item.modelName) ? item.modelName : models[0], updatedAt: now() }); onProviders(providers.map((current) => current.id === saved.id ? saved : current)); } catch (cause) { setProviderError(cause instanceof Error ? cause.message : String(cause)); } finally { setProviderBusy(false); } }
  async function chooseProviderModel(item: ProviderRecord, modelName: string) { const saved = { ...item, modelName, updatedAt: now() }; if (isTauriRuntime()) await saveProvider(saved); onProviders(providers.map((current) => current.id === saved.id ? saved : current)); }
  async function save() { await discover(provider); }
  return <section className="page-grid single-column"><div className="page-intro"><div><span className="eyebrow">MOTORES DE ROLEPLAY</span><h2>Modelos y APIs</h2><p>Elige un modelo GGUF local o configura una API compatible para tus personajes.</p></div><div className="hero-actions"><button className="secondary-button" onClick={() => setShowProvider(true)}><Globe2 size={17} /> Añadir API</button><button className="primary-button" onClick={onAddModel}><Plus size={17} /> Añadir GGUF</button></div></div><div className="panel model-runtime-summary"><div className="panel-heading"><div><span className="eyebrow">MOTOR ACTUAL</span><h3>{engine.loadedModelPath?.split(/[\\/]/).pop() ?? "Ningún modelo local cargado"}</h3></div><span className={`status-badge ${engine.executable ? "success" : "warning"}`}>{engine.executable ? "Listo" : "Solo API / carga un GGUF"}</span></div><div className="runtime-row"><RuntimeItem label="CPU" value={hardware?.cpuName ?? "Detectando"} /><RuntimeItem label="RAM" value={formatBytes(hardware?.ramAvailableBytes)} /><RuntimeItem label="GPU" value={hardware?.gpus[0]?.name ?? "No detectada"} /></div></div><div className="model-grid">{models.map((model) => <article className={`model-card ${engine.loadedModelId === model.id ? "loaded" : ""}`} key={model.id}><div className="model-card-top"><div className="model-icon large"><Bot size={21} /></div><div className="model-title"><h3>{model.name}</h3><span>{model.architecture ?? "GGUF"} · {formatBytes(model.sizeBytes)}</span></div></div><div className="model-path">{model.path}</div><div className="model-card-actions">{engine.loadedModelId === model.id ? <button className="secondary-button" onClick={() => void onUnload()}>Descargar</button> : <button className="primary-button" onClick={() => void onLoad(model.id)} disabled={!model.exists}>Cargar</button>}<button className="icon-button danger" onClick={() => void onRemove(model.id)}><Trash2 size={16} /></button></div></article>)}</div>{providers.length > 0 && <div className="provider-grid">{providers.map((item) => <article className="panel provider-card" key={item.id}><div className="setting-icon"><Globe2 size={19} /></div><h3>{item.name}</h3><span>{item.modelName || "Modelo remoto no indicado"}</span><small>{item.endpoint}</small><button className="icon-button danger" onClick={() => void deleteProvider(item.id).then(() => onProviders(providers.filter((providerItem) => providerItem.id !== item.id)))}><Trash2 size={16} /></button></article>)}</div>}{showProvider && <div className="modal-backdrop"><div className="modal-card"><div className="panel-heading"><h3>Configurar API</h3><button className="icon-button" onClick={() => setShowProvider(false)}><X size={18} /></button></div><label>Nombre<input value={provider.name} onChange={(event) => setProvider({ ...provider, name: event.target.value })} placeholder="OpenAI, OpenRouter…" /></label><label>Endpoint<input value={provider.endpoint} onChange={(event) => setProvider({ ...provider, endpoint: event.target.value })} /></label><label>Modelo<input value={provider.modelName} onChange={(event) => setProvider({ ...provider, modelName: event.target.value })} placeholder="gpt-4o-mini" /></label><label>API key<input type="password" value={provider.apiKey} onChange={(event) => setProvider({ ...provider, apiKey: event.target.value })} /></label><div className="modal-actions"><button className="secondary-button" onClick={() => setShowProvider(false)}>Cancelar</button><button className="primary-button" onClick={() => void save()} disabled={!provider.name.trim()}>Guardar API</button></div></div></div>}</section>;
}

function RepositorySettingsCard({ repositories, onSync, onDelete, onToggle }: { repositories: import("./types").RepositorySourceRecord[]; onSync: (url: string, name?: string) => Promise<unknown>; onDelete: (id: string) => Promise<void>; onToggle: (id: string, enabled: boolean) => Promise<void> }) {
  const [url, setUrl] = useState("");
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  async function add() {
    if (!url.trim()) return;
    setBusy(true); setNotice("");
    try { const result = await onSync(url.trim(), name.trim() || undefined); setNotice((result as { probe?: { message?: string } })?.probe?.message ?? "Fuente sincronizada."); setUrl(""); setName(""); }
    catch (cause) { setNotice(cause instanceof Error ? cause.message : String(cause)); }
    finally { setBusy(false); }
  }
  return <div className="panel setting-card repository-settings-card"><div className="setting-icon"><FolderOpen size={19} /></div><h3>Fuentes de personajes</h3><p>Añade, activa o quita repositorios. Explorar solo muestra los personajes de las fuentes activas.</p><div className="settings-repository-list">{repositories.map((repository) => <div className="settings-repository-row" key={repository.id}><div><strong>{repository.name}</strong><span>{repository.url}</span></div><span className={`status-badge ${repository.status === "READY" ? "success" : repository.status === "ERROR" ? "danger" : "warning"}`}>{repository.status}</span><button className="secondary-button compact-button" onClick={() => void onToggle(repository.id, !repository.enabled)}>{repository.enabled ? "Desactivar" : "Activar"}</button><button className="icon-button danger" onClick={() => void onDelete(repository.id)} aria-label={`Eliminar ${repository.name}`}><Trash2 size={15} /></button></div>)}</div><div className="settings-repository-add"><input value={name} onChange={(event) => setName(event.target.value)} placeholder="Nombre opcional" /><input value={url} onChange={(event) => setUrl(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") void add(); }} placeholder="URL de AI Character Cards o repository.json" type="url" /><button className="primary-button" onClick={() => void add()} disabled={busy || !url.trim()}>{busy ? "Comprobando…" : "Añadir fuente"}</button></div>{notice && <span className="setting-state" role="status">{notice}</span>}</div>;
}

function VoiceSettingsCard({ repositories, voices, onSync, onDelete }: { repositories: VoiceRepositoryRecord[]; voices: VoiceModelRecord[]; onSync: (url: string, name?: string) => Promise<unknown>; onDelete: (id: string) => Promise<void> }) {
  const [url, setUrl] = useState(() => localStorage.getItem("local-character.desktop.ttsRepository") ?? "");
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  async function sync() {
    if (!url.trim()) return;
    setBusy(true); setNotice("");
    try {
      const result = await onSync(url.trim(), name.trim() || undefined) as { voices?: VoiceModelRecord[] };
      localStorage.setItem("local-character.desktop.ttsRepository", url.trim());
      setName("");
      setNotice(`${result.voices?.length ?? 0} voces disponibles para asignar a tus personajes.`);
    } catch (cause) { setNotice(cause instanceof Error ? cause.message : String(cause)); }
    finally { setBusy(false); }
  }
  return <div className="panel setting-card voice-settings-card"><div className="setting-icon"><Volume2 size={19} /></div><h3>Voces y TTS</h3><p>Sincroniza un repositorio de voces y asigna una voz diferente a cada personaje desde su editor.</p><div className="voice-repository-list">{repositories.map((repository) => <div className="voice-repository-row" key={repository.id}><div><strong>{repository.name}</strong><span>{repository.endpoint}</span></div><span className="status-badge success">{voices.filter((voice) => voice.repositoryId === repository.id).length} voces</span><button className="icon-button danger" onClick={() => void onDelete(repository.id)} aria-label={`Eliminar ${repository.name}`}><Trash2 size={15} /></button></div>)}</div><div className="settings-repository-add"><input value={name} onChange={(event) => setName(event.target.value)} placeholder="Nombre opcional" /><input value={url} onChange={(event) => setUrl(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") void sync(); }} placeholder="https://…/voices.json" type="url" /><button className="primary-button" onClick={() => void sync()} disabled={busy || !url.trim()}>{busy ? "Sincronizando…" : "Sincronizar voces"}</button></div>{notice && <span className="setting-state" role="status">{notice}</span>}{voices.length > 0 && <div className="voice-catalog-preview"><span className="eyebrow">VOCES DISPONIBLES</span><div>{voices.slice(0, 12).map((voice) => <span key={voice.id}>{voice.name}{voice.language ? ` · ${voice.language.toUpperCase()}` : ""}</span>)}</div></div>}</div>;
}

function RoleplaySettings({ providers, setProviders, hardware, repositories, onSyncRepository, onDeleteRepository, onToggleRepository, voiceRepositories, voiceModels, onSyncVoiceRepository, onDeleteVoiceRepository }: { providers: ProviderRecord[]; setProviders: (items: ProviderRecord[]) => void; hardware: HardwareSnapshot | null; repositories: import("./types").RepositorySourceRecord[]; onSyncRepository: (url: string, name?: string) => Promise<unknown>; onDeleteRepository: (id: string) => Promise<void>; onToggleRepository: (id: string, enabled: boolean) => Promise<void>; voiceRepositories: VoiceRepositoryRecord[]; voiceModels: VoiceModelRecord[]; onSyncVoiceRepository: (url: string, name?: string) => Promise<unknown>; onDeleteVoiceRepository: (id: string) => Promise<void> }) {
  const [displayName, setDisplayName] = useState(() => localStorage.getItem("local-character.desktop.displayName") ?? "");
  const [nsfw, setNsfw] = useState(() => localStorage.getItem("local-character.desktop.nsfw") === "true");
  const [gpuLayers, setGpuLayers] = useState(() => Number(localStorage.getItem("local-character.desktop.gpuLayers") ?? (hardware?.gpus?.length ? "-1" : "0")));
  const [gpuMode, setGpuMode] = useState<"cpu" | "gpu" | "hybrid">(() => {
    const saved = localStorage.getItem("local-character.desktop.gpuMode");
    if (saved === "cpu" || saved === "gpu" || saved === "hybrid") return saved;
    const layers = Number(localStorage.getItem("local-character.desktop.gpuLayers") ?? (hardware?.gpus?.length ? "-1" : "0"));
    return layers === 0 ? "cpu" : layers < 0 ? "gpu" : "hybrid";
  });
  const [language, setLanguage] = useState(() => localStorage.getItem("local-character.desktop.language") ?? "es");
  const [suspects, setSuspects] = useState<import("./types").SuspiciousMessageRecord[]>([]);
  const [repairStatus, setRepairStatus] = useState("");
  const [promptDebug, setPromptDebug] = useState("");
  const save = (key: string, value: string) => localStorage.setItem(`local-character.desktop.${key}`, value);
  const inspectHistory = async () => {
    try { setSuspects(await findSuspiciousMessages()); setRepairStatus(""); } catch (cause) { setRepairStatus(String(cause)); }
  };
  const repairHistory = async () => {
    if (suspects.length === 0 || !window.confirm(`Se marcaron ${suspects.length} mensajes técnicos. ¿Quieres eliminarlos y recalcular las vistas previas?`)) return;
    try { const deleted = await deleteSuspiciousMessages(suspects.map((item) => item.id)); setSuspects([]); setRepairStatus(`${deleted} mensajes técnicos eliminados. El historial legítimo se conservó.`); } catch (cause) { setRepairStatus(String(cause)); }
  };
  const showLastPrompt = () => setPromptDebug(localStorage.getItem("local-character.desktop.lastPrompt") ?? "No hay un prompt de depuración guardado. Se registra en compilaciones de desarrollo.");
  return <section className="page-grid single-column">
    <div className="page-intro"><div><span className="eyebrow">PREFERENCIAS DE ROLEPLAY</span><h2>Ajustes</h2><p>Personaliza tu identidad, contenido, repositorios y voces de los personajes.</p></div></div>
    <div className="settings-grid">
      <RepositorySettingsCard repositories={repositories} onSync={onSyncRepository} onDelete={onDeleteRepository} onToggle={onToggleRepository} />
      <VoiceSettingsCard repositories={voiceRepositories} voices={voiceModels} onSync={onSyncVoiceRepository} onDelete={onDeleteVoiceRepository} />
      <div className="panel setting-card gpu-settings-card"><div className="setting-icon"><Cpu size={19} /></div><h3>Uso de CPU y GPU</h3><p>Elige si el GGUF se ejecuta en CPU, GPU o de forma híbrida. En híbrido, las capas seleccionadas usan VRAM y el resto continúa en CPU.</p><span className="setting-state">{hardware?.gpus?.[0] ? `${hardware.gpus[0].name} · ${formatBytes(hardware.gpus[0].vramBytes)} VRAM` : "No se detectó una GPU compatible; se usará la CPU."}</span><label>Modo de aceleración<select value={gpuMode} onChange={(event) => { const value = event.target.value as "cpu" | "gpu" | "hybrid"; setGpuMode(value); save("gpuMode", value); const layers = value === "cpu" ? 0 : value === "gpu" ? -1 : (gpuLayers > 0 ? gpuLayers : 32); setGpuLayers(layers); save("gpuLayers", String(layers)); }} disabled={!hardware?.gpus?.length}><option value="cpu">Solo CPU · sin VRAM</option><option value="gpu">GPU preferida · todas las capas posibles</option><option value="hybrid">GPU + CPU · modo híbrido</option></select></label>{gpuMode === "hybrid" && <label>Capas en VRAM<span className="range-value">{gpuLayers > 0 ? gpuLayers : 32}</span><input type="range" min="1" max="99" value={gpuLayers > 0 ? gpuLayers : 32} onChange={(event) => { const value = Number(event.target.value); setGpuLayers(value); save("gpuLayers", String(value)); }} /></label>}{hardware?.gpus?.length ? <span className="setting-state">La próxima carga usará {gpuMode === "cpu" ? "CPU" : gpuMode === "gpu" ? "-ngl -1 (GPU preferida)" : `-ngl ${gpuLayers > 0 ? gpuLayers : 32} (GPU + CPU)`}.</span> : null}</div>
      <div className="panel setting-card"><div className="setting-icon"><UserRound size={19} /></div><h3>Tu perfil</h3><p>Este nombre se usa en los mensajes del usuario.</p><label>Nombre visible<input value={displayName} onChange={(event) => { setDisplayName(event.target.value); save("displayName", event.target.value); }} placeholder="Cómo quieres que te llamen" /></label><label>Idioma<select value={language} onChange={(event) => { setLanguage(event.target.value); save("language", event.target.value); }}><option value="es">Español</option><option value="en">English</option><option value="pt">Português</option><option value="fr">Français</option></select></label></div>
      <div className="panel setting-card"><div className="setting-icon"><ShieldAlert size={19} /></div><h3>Contenido</h3><p>Controla si los personajes y repositorios marcados como NSFW pueden aparecer.</p><label className="switch-row"><span>Permitir NSFW</span><input type="checkbox" checked={nsfw} onChange={(event) => { setNsfw(event.target.checked); save("nsfw", String(event.target.checked)); }} /></label></div>
      <div className="panel setting-card"><div className="setting-icon"><Globe2 size={19} /></div><h3>APIs configuradas</h3><p>{providers.length === 0 ? "Todavía no hay proveedores. Añádelos desde Modelos y APIs." : `${providers.length} proveedor(es) configurado(s).`}</p></div>
      {import.meta.env.DEV && <div className="panel setting-card diagnostics-card"><div className="setting-icon"><CircleAlert size={19} /></div><h3>Diagnóstico de conversaciones</h3><p>Revisa mensajes técnicos heredados y el último prompt estructurado. Nada se elimina sin confirmación.</p><div className="hero-actions"><button className="secondary-button" onClick={() => void inspectHistory()}>Buscar artefactos ({suspects.length})</button><button className="secondary-button" onClick={() => void repairHistory()} disabled={suspects.length === 0}>Reparar historial</button><button className="secondary-button" onClick={showLastPrompt}>Ver último prompt</button></div>{repairStatus && <span className="setting-state">{repairStatus}</span>}{suspects.length > 0 && <div className="diagnostics-log">{suspects.slice(0, 20).map((item) => <div className="diagnostics-row" key={item.id}><code>{item.role}</code><span>{item.reason}: {item.content.slice(0, 180)}</span></div>)}</div>}{promptDebug && <pre className="prompt-debug">{promptDebug}</pre>}</div>}
    </div>
  </section>;
}

function LegacyRoleplaySettings({ providers, setProviders, hardware }: { providers: ProviderRecord[]; setProviders: (items: ProviderRecord[]) => void; hardware: HardwareSnapshot | null }) {
  const [displayName, setDisplayName] = useState(() => localStorage.getItem("local-character.desktop.displayName") ?? "");
  const [nsfw, setNsfw] = useState(() => localStorage.getItem("local-character.desktop.nsfw") === "true");
  const [characterRepo, setCharacterRepo] = useState(() => localStorage.getItem("local-character.desktop.characterRepository") ?? "");
  const [ttsRepo, setTtsRepo] = useState(() => localStorage.getItem("local-character.desktop.ttsRepository") ?? "");
  const [language, setLanguage] = useState(() => localStorage.getItem("local-character.desktop.language") ?? "es");
  const [suspects, setSuspects] = useState<import("./types").SuspiciousMessageRecord[]>([]);
  const [repairStatus, setRepairStatus] = useState("");
  const [promptDebug, setPromptDebug] = useState("");
  const save = (key: string, value: string) => localStorage.setItem(`local-character.desktop.${key}`, value);
  const inspectHistory = async () => {
    try { setSuspects(await findSuspiciousMessages()); setRepairStatus(""); } catch (cause) { setRepairStatus(String(cause)); }
  };
  const repairHistory = async () => {
    if (suspects.length === 0 || !window.confirm(`Se marcaron ${suspects.length} mensajes técnicos. ¿Quieres eliminarlos y recalcular las vistas previas?`)) return;
    try { const deleted = await deleteSuspiciousMessages(suspects.map((item) => item.id)); setSuspects([]); setRepairStatus(`${deleted} mensajes técnicos eliminados. El historial legítimo se conservó.`); } catch (cause) { setRepairStatus(String(cause)); }
  };
  const showLastPrompt = () => setPromptDebug(localStorage.getItem("local-character.desktop.lastPrompt") ?? "No hay un prompt de depuración guardado. Se registra en compilaciones de desarrollo.");
  return <section className="page-grid single-column"><div className="page-intro"><div><span className="eyebrow">PREFERENCIAS DE ROLEPLAY</span><h2>Ajustes</h2><p>Personaliza tu identidad, contenido, repositorios y voces de los personajes.</p></div></div><div className="settings-grid"><div className="panel setting-card"><div className="setting-icon"><UserRound size={19} /></div><h3>Tu perfil</h3><p>Este nombre se usa en los mensajes del usuario.</p><label>Nombre visible<input value={displayName} onChange={(event) => { setDisplayName(event.target.value); save("displayName", event.target.value); }} placeholder="Cómo quieres que te llamen" /></label><label>Idioma<select value={language} onChange={(event) => { setLanguage(event.target.value); save("language", event.target.value); }}><option value="es">Español</option><option value="en">English</option><option value="pt">Português</option><option value="fr">Français</option></select></label></div><div className="panel setting-card"><div className="setting-icon"><ShieldAlert size={19} /></div><h3>Contenido</h3><p>Controla si los personajes y repositorios marcados como NSFW pueden aparecer.</p><label className="switch-row"><span>Permitir NSFW</span><input type="checkbox" checked={nsfw} onChange={(event) => { setNsfw(event.target.checked); save("nsfw", String(event.target.checked)); }} /></label></div><div className="panel setting-card"><div className="setting-icon"><FolderOpen size={19} /></div><h3>Repositorios de personajes</h3><p>Un repositorio JSON puede contener un array `characters` o `cards`.</p><label>Ruta o URL de repositorio<input value={characterRepo} onChange={(event) => { setCharacterRepo(event.target.value); save("characterRepository", event.target.value); }} placeholder="https://…/characters.json" /></label></div><div className="panel setting-card"><div className="setting-icon"><Volume2 size={19} /></div><h3>Voces y TTS</h3><p>Guarda el repositorio de voces que usará el sistema de personajes.</p><label>Repositorio TTS<input value={ttsRepo} onChange={(event) => { setTtsRepo(event.target.value); save("ttsRepository", event.target.value); }} placeholder="https://…/voices.json" /></label><span className="setting-state">TTS local preparado · {hardware?.os ?? "Windows"}</span></div><div className="panel setting-card"><div className="setting-icon"><Globe2 size={19} /></div><h3>APIs configuradas</h3><p>{providers.length === 0 ? "Todavía no hay proveedores. Añádelos desde Modelos y APIs." : `${providers.length} proveedor(es) configurado(s).`}</p></div></div></section>;
}

function CharacterCard({ character, onEdit, onDelete }: { character: CharacterRecord; onEdit: () => void; onDelete: () => void }) { return <article className="character-card panel"><div className="character-card-heading"><Avatar path={character.avatarPath} name={character.name} /><div><h3>{character.name}</h3><span>{character.description}</span></div></div><p>{character.personality}</p><div className="character-card-actions"><button className="secondary-button" onClick={onEdit}>Editar</button><button className="icon-button danger" onClick={onDelete}><Trash2 size={16} /></button></div></article>; }
function Avatar({ path, name, large = false }: { path?: string; name: string; large?: boolean }) { const [source, setSource] = useState<string>(); useEffect(() => { let active = true; if (!path) { setSource(undefined); return; } if (/^(https?:|data:|blob:)/i.test(path) || !isTauriRuntime()) { setSource(path); return; } void readAvatarData(path).then((data) => { if (active) setSource(data); }).catch(() => { if (active) setSource(undefined); }); return () => { active = false; }; }, [path]); return <div className={`character-avatar ${large ? "large" : ""}`}>{source ? <img src={source} alt={name} loading="lazy" onError={() => setSource(undefined)} /> : <span>{name.slice(0, 1).toUpperCase() || <UserRound size={18} />}</span>}</div>; }
function RuntimeItem({ label, value }: { label: string; value: string }) { return <div className="runtime-item"><span>{label}</span><strong title={value}>{value}</strong></div>; }
function EmptyState({ icon, title, text, action }: { icon: ReactNode; title: string; text: string; action?: ReactNode }) { return <div className="empty-state"><div className="empty-icon">{icon}</div><h4>{title}</h4><p>{text}</p>{action}</div>; }
