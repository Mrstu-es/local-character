import { useEffect, useState } from "react";
import { Bookmark, MoreHorizontal, Play, Square, UserRound } from "lucide-react";
import type { ChatMessageRecord } from "../types";
import { isTauriRuntime, readAvatarData } from "../lib/tauri";

export interface MessageItemProps {
  message: ChatMessageRecord;
  senderName: string;
  avatarPath?: string;
  isStreaming?: boolean;
  isPlaying?: boolean;
  onPlayVoice?: (messageId: string) => void;
  onOpenActions: (messageId: string, anchor: DOMRect) => void;
}

export function MessageAvatar({ path, name }: { path?: string; name: string }) {
  const [source, setSource] = useState<string>();
  useEffect(() => {
    let active = true;
    if (!path) {
      setSource(undefined);
      return () => { active = false; };
    }
    if (!isTauriRuntime()) {
      setSource(path);
      return () => { active = false; };
    }
    void readAvatarData(path).then((data) => { if (active) setSource(data); }).catch(() => { if (active) setSource(undefined); });
    return () => { active = false; };
  }, [path]);
  return <div className="message-avatar">{source ? <img src={source} alt={name} /> : <span>{name.slice(0, 1).toUpperCase() || <UserRound size={16} />}</span>}</div>;
}

function formatTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

export function CharacterStreamingItem({ senderName, avatarPath, onStop }: { senderName: string; avatarPath?: string; onStop: () => void }) {
  return <article className="message-row-modern assistant character-streaming-item">
    <MessageAvatar path={avatarPath} name={senderName} />
    <div className="message-content-wrap"><div className="message-meta"><strong>{senderName}</strong></div><div className="typing-bubble"><span className="typing-dots"><i /><i /><i /></span><span>{senderName} está escribiendo…</span><button className="icon-button" onClick={onStop} title="Detener generación" aria-label="Detener generación"><Square size={14} /></button></div></div>
  </article>;
}

export default function MessageItem({ message, senderName, avatarPath, isStreaming, isPlaying, onPlayVoice, onOpenActions }: MessageItemProps) {
  const open = (target: HTMLElement) => onOpenActions(message.id, target.getBoundingClientRect());
  return <article className={`message-row-modern ${message.role} ${message.pinned ? "message-pinned" : ""}`} onContextMenu={(event) => { event.preventDefault(); open(event.currentTarget); }}>
    {message.role !== "user" && <MessageAvatar path={avatarPath} name={senderName} />}
    <div className="message-content-wrap">
      <div className="message-meta">
        <strong>{senderName}</strong>
        <time>{formatTime(message.createdAt)}</time>
        {message.pinned && <span className="message-memory-indicator" title="Guardado en memoria"><Bookmark size={13} /></span>}
        <button className="message-menu-trigger" type="button" aria-label={`Acciones del mensaje de ${senderName}`} aria-haspopup="menu" title="Acciones del mensaje" onClick={(event) => { event.stopPropagation(); open(event.currentTarget); }}><MoreHorizontal size={17} /></button>
      </div>
      <div className="message-bubble">{message.content}</div>
      {message.role === "assistant" && message.content.trim() && !isStreaming && onPlayVoice && <div className="message-footer"><button className={`message-play-button ${isPlaying ? "playing" : ""}`} type="button" onClick={() => onPlayVoice(message.id)} title={isPlaying ? "Detener reproducción" : "Reproducir mensaje"} aria-label={isPlaying ? "Detener reproducción" : "Reproducir mensaje"}>{isPlaying ? <Square size={14} /> : <Play size={14} />}</button></div>}
    </div>
    {message.role === "user" && <MessageAvatar name={senderName} />}
  </article>;
}
