import { useEffect, useRef } from "react";
import { Bookmark, Copy, GitBranch, RotateCcw, Trash2, Volume2, X } from "lucide-react";

export type MessageSenderType = "assistant" | "user" | "system";

export interface MessageActionsMenuProps {
  messageId: string;
  senderType: MessageSenderType;
  isPinned: boolean;
  anchor: DOMRect;
  onCopy: (messageId: string) => void | Promise<void>;
  onPlayVoice?: (messageId: string) => void | Promise<void>;
  onToggleMemory: (messageId: string) => void | Promise<void>;
  onBranch: (messageId: string) => void | Promise<void>;
  onRewind: (messageId: string) => void | Promise<void>;
  onDelete: (messageId: string) => void | Promise<void>;
  onRegenerate?: (messageId: string) => void | Promise<void>;
  onClose: () => void;
}

const menuWidth = 232;

export default function MessageActionsMenu({
  messageId,
  senderType,
  isPinned,
  anchor,
  onCopy,
  onPlayVoice,
  onToggleMemory,
  onBranch,
  onRewind,
  onDelete,
  onRegenerate,
  onClose,
}: MessageActionsMenuProps) {
  const menuRef = useRef<HTMLDivElement>(null);
  const below = anchor.bottom + 8 + 300 <= window.innerHeight;
  const top = below ? anchor.bottom + 8 : Math.max(10, anchor.top - 300 - 8);
  const left = Math.min(Math.max(10, anchor.right - menuWidth), Math.max(10, window.innerWidth - menuWidth - 10));

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
      }
    };
    const onPointerDown = (event: PointerEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("pointerdown", onPointerDown);
    menuRef.current?.querySelector<HTMLButtonElement>("button")?.focus();
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("pointerdown", onPointerDown);
    };
  }, [onClose]);

  const run = (action: () => void | Promise<void>) => {
    onClose();
    void action();
  };

  return <div ref={menuRef} className="message-actions-popover" role="menu" aria-label="Acciones del mensaje" style={{ top, left, width: menuWidth }}>
    <button role="menuitem" onClick={() => run(() => onCopy(messageId))}><Copy size={15} /> Copiar</button>
    {senderType === "assistant" && onPlayVoice && <button role="menuitem" onClick={() => run(() => onPlayVoice(messageId))}><Volume2 size={15} /> Reproducir voz</button>}
    <button role="menuitem" onClick={() => run(() => onToggleMemory(messageId))}><Bookmark size={15} /> {isPinned ? "Desmarcar recuerdo" : "Guardar en memoria"}</button>
    <div className="message-actions-divider" role="separator" />
    <button role="menuitem" onClick={() => run(() => onBranch(messageId))}><GitBranch size={15} /> Nuevo chat desde aquí</button>
    <button role="menuitem" onClick={() => run(() => onRewind(messageId))}><RotateCcw size={15} /> Rebobinar hasta aquí</button>
    {senderType === "assistant" && onRegenerate && <button role="menuitem" onClick={() => run(() => onRegenerate(messageId))}><RotateCcw size={15} /> Regenerar respuesta</button>}
    <div className="message-actions-divider" role="separator" />
    <button className="message-action-danger" role="menuitem" onClick={() => run(() => onDelete(messageId))}><Trash2 size={15} /> Eliminar</button>
    <button className="message-actions-close" role="menuitem" onClick={onClose}><X size={14} /> Cerrar</button>
  </div>;
}
