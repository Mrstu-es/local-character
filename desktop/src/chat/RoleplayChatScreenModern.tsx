import { useEffect, useMemo, useState } from "react";
import { ArrowLeft, Bookmark, Bot, Globe2, RotateCcw, Trash2, UserRound, X } from "lucide-react";
import type {
  CharacterRecord,
  ChatMessageRecord,
  ConversationRecord,
  EngineStatus,
  GroupRecord,
  ProviderRecord,
  SemanticMemoryRecord,
} from "../types";
import {
  deleteSemanticMemory,
  isTauriRuntime,
  listMessages,
  listSemanticMemories,
  readAvatarData,
} from "../lib/tauri";
import ChatPanelModern from "./ChatPanelModern";
import { mergeSemanticMemories } from "./semanticMemory";

interface RoleplayChatScreenModernProps {
  chatId: string;
  characters: CharacterRecord[];
  conversations: ConversationRecord[];
  groups: GroupRecord[];
  providers: ProviderRecord[];
  engine: EngineStatus;
  onBack: () => void;
  onOpenCharacters: () => void;
}

function Avatar({ path, name }: { path?: string; name: string }) {
  const [source, setSource] = useState<string>();
  useEffect(() => {
    let active = true;
    if (!path) { setSource(undefined); return () => { active = false; }; }
    if (!isTauriRuntime()) { setSource(path); return () => { active = false; }; }
    void readAvatarData(path)
      .then((data) => { if (active) setSource(data); })
      .catch(() => { if (active) setSource(undefined); });
    return () => { active = false; };
  }, [path]);
  return <div className="character-avatar dedicated-avatar">{source ? <img src={source} alt={name} /> : <span>{name.slice(0, 1).toUpperCase() || <UserRound size={18} />}</span>}</div>;
}

export default function RoleplayChatScreenModern({ chatId, characters, conversations, groups, providers, engine, onBack, onOpenCharacters }: RoleplayChatScreenModernProps) {
  const conversation = conversations.find((item) => item.id === chatId);
  const group = groups.find((item) => item.id === chatId);
  const character = conversation?.characterId ? characters.find((item) => item.id === conversation.characterId) : undefined;
  const fallbackGroupCharacter = group?.participantIds.map((id) => characters.find((item) => item.id === id)).find(Boolean);
  const memoryCharacterId = character?.id ?? fallbackGroupCharacter?.id;
  const [selectedProviderId, setSelectedProviderId] = useState<string | null>(null);
  const [showBrain, setShowBrain] = useState(false);
  const [showMemory, setShowMemory] = useState(false);
  const [memoryTab, setMemoryTab] = useState("Recuerdos");
  const [semanticMemoryItems, setSemanticMemoryItems] = useState<SemanticMemoryRecord[]>([]);
  const [pinnedMemoryItems, setPinnedMemoryItems] = useState<ChatMessageRecord[]>([]);
  const [memoryRefresh, setMemoryRefresh] = useState(0);
  const [memoryLoading, setMemoryLoading] = useState(false);
  const [prefill, setPrefill] = useState<string | undefined>();
  const [notice, setNotice] = useState<string | null>(null);
  const selectedProvider = providers.find((item) => item.id === selectedProviderId);
  const title = character?.name ?? group?.name ?? conversation?.title ?? "Conversación";

  useEffect(() => {
    if (!showMemory || !isTauriRuntime() || !memoryCharacterId) return;
    let active = true;
    setMemoryLoading(true);
    void Promise.all([
      listSemanticMemories(memoryCharacterId, chatId),
      listMessages(chatId),
    ]).then(([semantic, messages]) => {
      if (!active) return;
      setSemanticMemoryItems(mergeSemanticMemories(semantic, []));
      setPinnedMemoryItems(messages.filter((message) => message.pinned));
    }).catch(() => {
      if (!active) return;
      setSemanticMemoryItems([]);
      setPinnedMemoryItems([]);
    }).finally(() => { if (active) setMemoryLoading(false); });
    return () => { active = false; };
  }, [chatId, memoryCharacterId, memoryRefresh, showMemory]);

  const semanticForTab = useMemo(() => semanticMemoryItems.filter((memory) => {
    if (memoryTab === "Relaciones") return memory.kind === "relationship";
    if (memoryTab === "Eventos") return memory.kind === "event";
    if (memoryTab === "Preferencias") return memory.kind === "preference" || memory.kind === "opinion";
    return memory.kind === "fact";
  }), [memoryTab, semanticMemoryItems]);

  const announce = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(null), 2400);
  };

  const removeSemanticMemory = async (memory: SemanticMemoryRecord) => {
    await deleteSemanticMemory(memory.id);
    setSemanticMemoryItems((current) => current.filter((item) => item.id !== memory.id));
    setMemoryRefresh((current) => current + 1);
  };

  const noMemory = semanticForTab.length === 0 && (memoryTab !== "Recuerdos" || pinnedMemoryItems.length === 0);

  return <section className="dedicated-chat-screen dedicated-chat-screen-modern">
    <div className="dedicated-chat-topbar">
      <button className="icon-button" onClick={onBack} aria-label="Volver a chats"><ArrowLeft size={20} /></button>
      <Avatar path={character?.avatarPath} name={title} />
      <div className="dedicated-chat-title"><strong>{title}</strong><span>{group ? "Chat grupal" : selectedProvider ? `${selectedProvider.name} · ${selectedProvider.modelName}` : engine.executable ? "Procesamiento local" : "Selecciona una IA"}</span></div>
      <button className="icon-button" onClick={onOpenCharacters} title="Abrir personaje" aria-label="Abrir personaje"><UserRound size={18} /></button>
      <button className="icon-button" onClick={() => setShowMemory(true)} title="Memoria" aria-label="Memoria"><Bookmark size={18} /></button>
      <button className="icon-button" onClick={() => setPrefill("Regenera la última respuesta manteniendo el contexto.")} title="Regenerar" aria-label="Regenerar"><RotateCcw size={18} /></button>
    </div>
    <div className="dedicated-chat-actions">
      <button className="secondary-button" onClick={() => setShowBrain(true)}><Bot size={16} /> Motor de IA</button>
      <button className="secondary-button" onClick={() => setShowMemory(true)}><Bookmark size={16} /> Memoria</button>
      <button className="secondary-button" onClick={onOpenCharacters}><UserRound size={16} /> Personaje</button>
      <span className="dedicated-chat-privacy">{selectedProvider ? "☁ Procesamiento online" : "🔒 Procesamiento local"}</span>
    </div>
    <div className="dedicated-chat-body">
      <ChatPanelModern
        chatId={chatId}
        characters={characters}
        conversations={conversations}
        groups={groups}
        providers={selectedProvider ? [selectedProvider] : providers}
        engine={engine}
        preferredProviderId={selectedProviderId}
        prefill={prefill}
        onPrefillConsumed={() => setPrefill(undefined)}
        onNotice={announce}
        onMemoryChanged={() => setMemoryRefresh((current) => current + 1)}
      />
    </div>
    {notice && <div className="chat-notice" role="status">{notice}</div>}
    {showBrain && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) setShowBrain(false); }}>
      <div className="modal-card brain-picker" role="dialog" aria-modal="true" aria-label="Motor de IA">
        <div className="panel-heading"><div><span className="eyebrow">MOTOR DE IA</span><h3>Selecciona un modelo</h3></div><button className="icon-button" onClick={() => setShowBrain(false)} aria-label="Cerrar"><X size={18} /></button></div>
        <button className={`brain-option ${selectedProviderId === null ? "selected" : ""}`} onClick={() => { setSelectedProviderId(null); setShowBrain(false); }}><Bot size={17} /><span><strong>Modelo local</strong><small>{engine.executable ? "GGUF cargado" : "Sin modelo local cargado"}</small></span></button>
        {providers.filter((item) => item.enabled && item.modelName).map((item) => <button className={`brain-option ${selectedProviderId === item.id ? "selected" : ""}`} key={item.id} onClick={() => { setSelectedProviderId(item.id); setShowBrain(false); }}><Globe2 size={17} /><span><strong>{item.name}</strong><small>{item.modelName}</small></span></button>)}
      </div>
    </div>}
    {showMemory && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) setShowMemory(false); }}>
      <div className="modal-card" role="dialog" aria-modal="true" aria-label="Memoria local">
        <div className="panel-heading"><div><span className="eyebrow">MEMORIA LOCAL</span><h3>{title}</h3></div><button className="icon-button" onClick={() => setShowMemory(false)} aria-label="Cerrar"><X size={18} /></button></div>
        <p>Preferencias, opiniones, relaciones y recuerdos se extraen de forma conservadora y permanecen únicamente en este dispositivo.</p>
        <div className="memory-tabs">{["Recuerdos", "Relaciones", "Eventos", "Preferencias"].map((tab) => <button key={tab} className={memoryTab === tab ? "selected" : ""} onClick={() => setMemoryTab(tab)}>{tab}</button>)}</div>
        <div className="memory-record-list">
          {memoryLoading ? <div className="memory-tab-content"><Bookmark size={23} /><strong>Cargando memoria…</strong></div> : <>
            {memoryTab === "Recuerdos" && pinnedMemoryItems.map((message) => <div className="memory-record" key={`pinned-${message.id}`}><span className="memory-kind">Mensaje guardado</span><p>{message.content}</p></div>)}
            {semanticForTab.map((memory) => <div className="memory-record" key={memory.id}><div><span className="memory-kind">{memory.kind === "opinion" ? "Opinión" : memory.kind === "preference" ? "Preferencia" : memory.kind === "relationship" ? "Relación" : memory.kind === "event" ? "Evento" : "Hecho"} · {memory.subject}</span><p>{memory.content}</p></div><button className="icon-button" aria-label="Eliminar memoria" title="Eliminar memoria" onClick={() => void removeSemanticMemory(memory)}><Trash2 size={15} /></button></div>)}
            {noMemory && <div className="memory-tab-content"><Bookmark size={23} /><strong>Sin datos en {memoryTab.toLowerCase()}</strong><span>Aparecerán aquí cuando se expresen de forma clara durante la conversación.</span></div>}
          </>}
        </div>
      </div>
    </div>}
  </section>;
}
