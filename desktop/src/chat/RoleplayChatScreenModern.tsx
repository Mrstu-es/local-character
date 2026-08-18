import { useEffect, useState } from "react";
import { ArrowLeft, Bookmark, Bot, Globe2, RotateCcw, UserRound, X } from "lucide-react";
import type { CharacterRecord, ConversationRecord, EngineStatus, GroupRecord, ProviderRecord } from "../types";
import { isTauriRuntime, readAvatarData } from "../lib/tauri";
import ChatPanelModern from "./ChatPanelModern";

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
    void readAvatarData(path).then((data) => { if (active) setSource(data); }).catch(() => { if (active) setSource(undefined); });
    return () => { active = false; };
  }, [path]);
  return <div className="character-avatar dedicated-avatar">{source ? <img src={source} alt={name} /> : <span>{name.slice(0, 1).toUpperCase() || <UserRound size={18} />}</span>}</div>;
}

export default function RoleplayChatScreenModern({ chatId, characters, conversations, groups, providers, engine, onBack, onOpenCharacters }: RoleplayChatScreenModernProps) {
  const conversation = conversations.find((item) => item.id === chatId);
  const group = groups.find((item) => item.id === chatId);
  const character = conversation?.characterId ? characters.find((item) => item.id === conversation.characterId) : undefined;
  const [selectedProviderId, setSelectedProviderId] = useState<string | null>(null);
  const [showBrain, setShowBrain] = useState(false);
  const [showMemory, setShowMemory] = useState(false);
  const [memoryTab, setMemoryTab] = useState("Recuerdos");
  const [prefill, setPrefill] = useState<string | undefined>();
  const [notice, setNotice] = useState<string | null>(null);
  const selectedProvider = providers.find((item) => item.id === selectedProviderId);
  const title = character?.name ?? group?.name ?? conversation?.title ?? "Conversación";

  const announce = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(null), 2400);
  };
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
      <ChatPanelModern chatId={chatId} characters={characters} conversations={conversations} groups={groups} providers={selectedProvider ? [selectedProvider] : providers} engine={engine} preferredProviderId={selectedProviderId} prefill={prefill} onPrefillConsumed={() => setPrefill(undefined)} onNotice={announce} />
    </div>
    {notice && <div className="chat-notice" role="status">{notice}</div>}
    {showBrain && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) setShowBrain(false); }}><div className="modal-card brain-picker" role="dialog" aria-modal="true" aria-label="Motor de IA"><div className="panel-heading"><div><span className="eyebrow">MOTOR DE IA</span><h3>Selecciona un modelo</h3></div><button className="icon-button" onClick={() => setShowBrain(false)} aria-label="Cerrar"><X size={18} /></button></div><button className={`brain-option ${selectedProviderId === null ? "selected" : ""}`} onClick={() => { setSelectedProviderId(null); setShowBrain(false); }}><Bot size={17} /><span><strong>Modelo local</strong><small>{engine.executable ? "GGUF cargado" : "Sin modelo local cargado"}</small></span></button>{providers.filter((item) => item.enabled && item.modelName).map((item) => <button className={`brain-option ${selectedProviderId === item.id ? "selected" : ""}`} key={item.id} onClick={() => { setSelectedProviderId(item.id); setShowBrain(false); }}><Globe2 size={17} /><span><strong>{item.name}</strong><small>{item.modelName}</small></span></button>)}</div></div>}
    {showMemory && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) setShowMemory(false); }}><div className="modal-card" role="dialog" aria-modal="true" aria-label="Memoria local"><div className="panel-heading"><div><span className="eyebrow">MEMORIA LOCAL</span><h3>{title}</h3></div><button className="icon-button" onClick={() => setShowMemory(false)} aria-label="Cerrar"><X size={18} /></button></div><p>Los recuerdos permanecen únicamente en este dispositivo.</p><div className="memory-tabs">{["Recuerdos", "Relaciones", "Eventos", "Preferencias"].map((tab) => <button key={tab} className={memoryTab === tab ? "selected" : ""} onClick={() => setMemoryTab(tab)}>{tab}</button>)}</div><div className="memory-tab-content"><Bookmark size={23} /><strong>{memoryTab}</strong><span>{memoryTab === "Recuerdos" ? "Guarda mensajes importantes desde los tres puntos de cada mensaje." : `La sección ${memoryTab.toLowerCase()} estará disponible al añadir información.`}</span></div></div></div>}
  </section>;
}
