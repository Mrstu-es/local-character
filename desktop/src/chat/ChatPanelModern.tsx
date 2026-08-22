import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from "react";
import { Bookmark, ChevronDown, MessageCircle, RotateCcw, Send, Sparkles, Square, UserRound } from "lucide-react";
import type { CharacterRecord, ChatMessageRecord, ConversationRecord, EngineStatus, GroupRecord, ProviderRecord, SemanticMemoryRecord } from "../types";
import {
  branchFromMessage,
  deleteMessage,
  isTauriRuntime,
  listMessages,
  getConversationSummary,
  listSemanticMemories,
  saveSemanticMemory,
  saveConversationSummary,
  listenToGeneration,
  rewindToMessage,
  saveMessage,
  sendChatMessage,
  stopGeneration,
} from "../lib/tauri";
import MessageActionsMenu from "./MessageActionsMenu";
import MessageItem, { CharacterStreamingItem } from "./MessageItem";
import { buildRoleplaySystemPrompt, roleplayHistory } from "./promptBuilder";
import { buildContinuityContext, estimateTokens, type ContinuityContext } from "./continuity";
import { conversationUserName, TemplateVariableResolver } from "./templateVariables";
import { extractSemanticMemories, mergeSemanticMemories, semanticMemoryPromptText } from "./semanticMemory";

interface ChatPanelModernProps {
  chatId: string;
  characters: CharacterRecord[];
  conversations: ConversationRecord[];
  groups: GroupRecord[];
  providers: ProviderRecord[];
  engine: EngineStatus;
  preferredProviderId?: string | null;
  prefill?: string;
  onPrefillConsumed?: () => void;
  onNotice?: (message: string) => void;
  onGeneratingChange?: (generating: boolean) => void;
  onMemoryChanged?: () => void;
}

const now = () => new Date().toISOString();

type GenerationStatus = "WAITING_FIRST_TOKEN" | "STREAMING" | "COMPLETING" | "COMPLETED" | "CANCELLED" | "ERROR";
interface GenerationState {
  generationId: string;
  conversationId: string;
  messageId: string;
  status: GenerationStatus;
  startedAt: string;
  firstTokenAt?: string;
  completedAt?: string;
  finishReason?: string;
  error?: string;
}

function metadataFor(message: ChatMessageRecord): Record<string, unknown> {
  try {
    const parsed = JSON.parse(message.metadataJson || "{}");
    return parsed && typeof parsed === "object" ? parsed as Record<string, unknown> : {};
  } catch {
    return {};
  }
}

function senderCharacterId(message: ChatMessageRecord, fallback?: string) {
  const value = metadataFor(message).senderCharacterId;
  return typeof value === "string" && value ? value : fallback;
}

function isRenderableMessage(message: ChatMessageRecord): boolean {
  if (!message.content.trim() || !["user", "assistant"].includes(message.role)) return false;
  try {
    const metadata = JSON.parse(message.metadataJson || "{}") as Record<string, unknown>;
    const source = typeof metadata.source === "string" ? metadata.source.toLowerCase() : "";
    if (["runtime", "engine", "diagnostic", "system"].includes(source)) return false;
    if (source === "user") return true;
  } catch {
    // Continue with the conservative legacy check.
  }
  if (message.role === "user") return true;
  const lower = message.content.trim().toLowerCase();
  return !["loading model", "llama.cpp", "llama-server", "available commands", "system is thinking", "el modelo está pensando", "el modelo esta pensando", "the model is thinking", "el usuario está solicitando", "el usuario esta solicitando", "system:", "developer:", "thinking:", "reasoning:", "analysis:", "prompt:", "generation:", "{{user}}", "{{ user }}", "{{char}}", "{{ char }}", "[prompt:", "[generation:", "<thinking>", "<think>", "</thinking>", "<response>", "<|im_start|>", "<|assistant|>"].some((marker) => lower.includes(marker));
}

export default function ChatPanelModern({ chatId, characters, conversations, groups, providers, engine, preferredProviderId, prefill, onPrefillConsumed, onNotice, onGeneratingChange, onMemoryChanged }: ChatPanelModernProps) {
  const conversation = conversations.find((item) => item.id === chatId);
  const group = groups.find((item) => item.id === chatId);
  const conversationId = conversation?.id ?? group?.id ?? chatId;
  const character = conversation?.characterId ? characters.find((item) => item.id === conversation.characterId) : undefined;
  const fallbackGroupCharacter = group?.participantIds.map((id) => characters.find((item) => item.id === id)).find(Boolean);
  const userName = useMemo(() => conversationUserName(conversation), [conversation]);
  const templateResolver = useMemo(() => new TemplateVariableResolver({ userName, characterName: character?.name ?? fallbackGroupCharacter?.name }), [userName, character?.name, fallbackGroupCharacter?.name]);
  const [messages, setMessages] = useState<ChatMessageRecord[]>([]);
  const [memories, setMemories] = useState<string[]>([]);
  const [semanticMemories, setSemanticMemories] = useState<SemanticMemoryRecord[]>([]);
  const [conversationSummary, setConversationSummary] = useState<import("../types").ConversationSummaryRecord | null>(null);
  const [draft, setDraft] = useState("");
  const [actionMode, setActionMode] = useState(false);
  const [generationState, setGenerationState] = useState<GenerationState | null>(null);
  const generationStateRef = useRef<GenerationState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [retryAvailable, setRetryAvailable] = useState(false);
  const [selectedMessageId, setSelectedMessageId] = useState<string | null>(null);
  const [menuAnchor, setMenuAnchor] = useState<DOMRect | null>(null);
  const [showJumpToEnd, setShowJumpToEnd] = useState(false);
  const [greetingHidden, setGreetingHidden] = useState(false);
  const messagesRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const activeGeneration = useRef<string | null>(null);
  const assistantIds = useRef(new Map<string, string>());
  const assistantContent = useRef(new Map<string, string>());
  const assistantRecords = useRef(new Map<string, ChatMessageRecord>());
  const messageRecordsRef = useRef<ChatMessageRecord[]>([]);
  const summaryRef = useRef<import("../types").ConversationSummaryRecord | null>(null);
  const lastUserMessage = useRef<ChatMessageRecord | null>(null);
  const lastGenerationMode = useRef<"send" | "continue">("send");
  const [activePlaybackMessageId, setActivePlaybackMessageId] = useState<string | null>(null);
  const playbackToken = useRef(0);
  const nearBottom = useRef(true);
  const isGenerating = generationState?.status === "WAITING_FIRST_TOKEN" || generationState?.status === "STREAMING" || generationState?.status === "COMPLETING";
  const waitingFirstToken = generationState?.status === "WAITING_FIRST_TOKEN";
  const contextMemories = useMemo(
    // relevantMemories uses the latest item as the tie-breaker. Semantic
    // records are sorted strongest-first, so reverse them before combining
    // to keep the highest-confidence record at the newest end.
    () => [...memories, ...[...semanticMemories].reverse().map(semanticMemoryPromptText)],
    [memories, semanticMemories],
  );

  useEffect(() => {
    onGeneratingChange?.(Boolean(isGenerating));
  }, [isGenerating, onGeneratingChange]);

  useEffect(() => {
    messageRecordsRef.current = messages;
  }, [messages]);

  useEffect(() => {
    summaryRef.current = conversationSummary;
  }, [conversationSummary]);

  // A chat screen can be reused for another character while a previous turn
  // is still mounted. Never let its assistant maps or typing state leak into
  // the newly selected conversation.
  useEffect(() => {
    activeGeneration.current = null;
    if (generationStateRef.current && isTauriRuntime()) void stopGeneration().catch(() => undefined);
    generationStateRef.current = null;
    setGenerationState(null);
    assistantIds.current.clear();
    assistantContent.current.clear();
    assistantRecords.current.clear();
    setError(null);
    setRetryAvailable(false);
    setConversationSummary(null);
    setSemanticMemories([]);
    summaryRef.current = null;
    messageRecordsRef.current = [];
    lastUserMessage.current = null;
    setGreetingHidden(false);
  }, [chatId]);

  const notify = useCallback((message: string) => {
    onNotice?.(message);
  }, [onNotice]);

  const persistContinuitySummary = useCallback(async (transcript: ChatMessageRecord[]) => {
    if (!isTauriRuntime() || !conversationId || transcript.length < 8) return;
    const configuredContext = Number(localStorage.getItem("local-character.desktop.context") ?? "8192");
    const modelContext = Number(engine.contextLength ?? configuredContext);
    const contextLimit = Math.max(1024, Math.min(Number.isFinite(configuredContext) ? configuredContext : 8192, Number.isFinite(modelContext) && modelContext > 0 ? modelContext : 8192));
    const result = buildContinuityContext({
      conversationId,
      characterId: character?.id ?? fallbackGroupCharacter?.id,
      groupId: group?.id,
      messages: transcript,
      summary: summaryRef.current,
      memories: contextMemories,
      contextLimit,
      reserveOutput: 512,
      systemTokenReserve: 2200,
    });
    if (result.summaryToPersist) {
      summaryRef.current = result.summaryToPersist;
      setConversationSummary(result.summaryToPersist);
      await saveConversationSummary(result.summaryToPersist).catch(() => undefined);
    }
  }, [character?.id, contextMemories, conversationId, engine.contextLength, fallbackGroupCharacter?.id, group?.id]);

  const persistSemanticRecords = useCallback(async (sourceMessages: ChatMessageRecord[]) => {
    const characterId = character?.id ?? fallbackGroupCharacter?.id;
    if (!isTauriRuntime() || !characterId || !conversationId || sourceMessages.length === 0) return;
    const extracted = extractSemanticMemories({
      messages: sourceMessages,
      characterId,
      conversationId,
      characterName: character?.name ?? fallbackGroupCharacter?.name ?? "Personaje",
      userName,
    });
    if (!extracted.length) return;
    await Promise.all(extracted.map((memory) => saveSemanticMemory(memory)));
    setSemanticMemories((current) => mergeSemanticMemories(current, extracted));
    onMemoryChanged?.();
  }, [character?.id, character?.name, conversationId, fallbackGroupCharacter?.id, fallbackGroupCharacter?.name, onMemoryChanged, userName]);

  const finishGeneration = useCallback((status: "COMPLETED" | "CANCELLED" | "ERROR", failure?: string, finishReason?: string) => {
    const current = generationStateRef.current;
    if (!current || ["COMPLETED", "CANCELLED", "ERROR"].includes(current.status)) return;
    const assistantId = current.messageId;
    const content = templateResolver.cleanGeneratedContent(assistantContent.current.get(current.generationId) ?? "");
    const record = assistantRecords.current.get(current.generationId);
    const finalError = failure ?? (status === "ERROR" && !content.trim() ? "No se pudo generar la respuesta." : undefined);
    const finalMetadata = record ? { ...metadataFor(record), generationStatus: status.toLowerCase(), finishReason: finishReason ?? status.toLowerCase(), completedAt: now() } : undefined;
    if (status === "COMPLETED" && content.trim() && record) {
      const completed = { ...record, content, metadataJson: JSON.stringify(finalMetadata) };
      const nextMessages = messageRecordsRef.current.map((item) => item.id === assistantId ? completed : item);
      messageRecordsRef.current = nextMessages;
      setMessages(nextMessages);
      if (isTauriRuntime()) {
        // Persist the source turn before its semantic memories because SQLite
        // links each extracted record to that message with a foreign key.
        void saveMessage(completed).then(() => persistSemanticRecords([completed]));
      }
      void persistContinuitySummary(nextMessages);
    } else if (content.trim() && record) {
      const partial = { ...record, content, metadataJson: JSON.stringify(finalMetadata) };
      const nextMessages = messageRecordsRef.current.map((item) => item.id === assistantId ? partial : item);
      messageRecordsRef.current = nextMessages;
      setMessages(nextMessages);
      if (isTauriRuntime()) void saveMessage(partial);
    } else {
      setMessages((items) => items.filter((item) => item.id !== assistantId));
      if (isTauriRuntime()) void deleteMessage(assistantId).catch(() => undefined);
    }
    const completedState: GenerationState = { ...current, status, completedAt: now(), error: finalError };
    if (import.meta.env.DEV) {
      console.debug("[generation] finalized", {
        generationId: current.generationId,
        status,
        finishReason: finishReason ?? status.toLowerCase(),
        visibleCharacters: content.length,
      });
    }
    generationStateRef.current = completedState;
    setGenerationState(completedState);
    activeGeneration.current = null;
    assistantRecords.current.delete(current.generationId);
    assistantIds.current.delete(current.generationId);
    assistantContent.current.delete(current.generationId);
    if (finalError) setError(finalError);
    setRetryAvailable(Boolean(finalError));
  }, [persistContinuitySummary, persistSemanticRecords, templateResolver]);

  // Safety net only: normal completion comes from the backend terminal event.
  // A stalled provider must never leave the UI in an infinite writing state.
  useEffect(() => {
    const current = generationState;
    if (!current || ["COMPLETED", "CANCELLED", "ERROR"].includes(current.status)) return;
    const timeoutMs = current.status === "WAITING_FIRST_TOKEN" ? 120_000 : 15 * 60_000;
    const timer = window.setTimeout(() => finishGeneration("ERROR", "La generación no terminó a tiempo."), timeoutMs);
    return () => window.clearTimeout(timer);
  }, [finishGeneration, generationState]);

  const reloadMessages = useCallback(async () => {
    if (!isTauriRuntime() || !conversationId) {
      setMessages([]);
      setMemories([]);
      setSemanticMemories([]);
      setConversationSummary(null);
      return;
    }
    try {
      const characterId = character?.id ?? fallbackGroupCharacter?.id;
      const [loaded, savedSummary, savedSemantic] = await Promise.all([
        listMessages(conversationId),
        getConversationSummary(conversationId),
        characterId ? listSemanticMemories(characterId, conversationId) : Promise.resolve([] as SemanticMemoryRecord[]),
      ]);
      const normalized = loaded.map((message) => ({ ...message, content: message.role === "assistant" ? templateResolver.cleanGeneratedContent(message.content) : templateResolver.resolve(message.content) }));
      const renderable = normalized.filter(isRenderableMessage);
      const changed = normalized.filter((message, index) => message.content !== loaded[index].content);
      if (isTauriRuntime()) await Promise.all(changed.filter((message) => message.content.trim()).map((message) => saveMessage(message)));
      setMemories(renderable.filter((message) => message.pinned).map((message) => message.content));
      const extracted = characterId ? extractSemanticMemories({
        messages: renderable.slice(-40),
        characterId,
        conversationId,
        characterName: character?.name ?? fallbackGroupCharacter?.name ?? "Personaje",
        userName,
      }) : [];
      const savedByKey = new Map(savedSemantic.map((memory) => [memory.memoryKey, memory]));
      const changedSemantic = extracted.filter((memory) => {
        const saved = savedByKey.get(memory.memoryKey);
        return !saved || saved.content !== memory.content || saved.sourceMessageId !== memory.sourceMessageId;
      });
      if (isTauriRuntime() && changedSemantic.length) await Promise.all(changedSemantic.map((memory) => saveSemanticMemory(memory)));
      setSemanticMemories(mergeSemanticMemories(savedSemantic, extracted));
      // Si una respuesta antigua solo contenía razonamiento/metadatos, la
      // normalización la deja vacía. Trátala como mensaje fantasma también
      // para que no reaparezca al volver a abrir la conversación.
      const ghosts = normalized.filter((message) => message.role === "assistant" && !message.content.trim());
      if (isTauriRuntime()) await Promise.all(ghosts.map((message) => deleteMessage(message.id).catch(() => undefined)));
      messageRecordsRef.current = renderable;
      setMessages(renderable);
      setConversationSummary(savedSummary);
      summaryRef.current = savedSummary;
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    }
  }, [character?.id, character?.name, conversationId, fallbackGroupCharacter?.id, fallbackGroupCharacter?.name, templateResolver, userName]);

  useEffect(() => { void reloadMessages(); }, [reloadMessages]);

  useEffect(() => {
    if (prefill === undefined) return;
    if (prefill === "__LOCAL_CHARACTER_CONTINUE__") {
      setActionMode(false);
      setDraft("");
    } else if (prefill === "**") {
      setActionMode(true);
      setDraft("");
    } else {
      setActionMode(false);
      setDraft(prefill);
    }
    onPrefillConsumed?.();
    requestAnimationFrame(() => textareaRef.current?.focus());
  }, [isGenerating, prefill, onPrefillConsumed]);

  useEffect(() => {
    if (!isTauriRuntime()) return;
    let cleanups: Array<() => void> = [];
    let disposed = false;
    void listenToGeneration(
      (event) => {
        if (event.generationId !== activeGeneration.current) return;
        const id = assistantIds.current.get(event.generationId);
        if (!id) return;
        const content = `${assistantContent.current.get(event.generationId) ?? ""}${event.text}`;
        assistantContent.current.set(event.generationId, content);
        const currentState = generationStateRef.current;
        if (currentState?.status === "WAITING_FIRST_TOKEN") {
          const streamingState = { ...currentState, status: "STREAMING" as const, firstTokenAt: now() };
          generationStateRef.current = streamingState;
          setGenerationState(streamingState);
        }
        setMessages((current) => current.map((message) => message.id === id ? { ...message, content: templateResolver.cleanGeneratedContent(content) } : message));
      },
      (event) => {
        if (event.generationId !== activeGeneration.current) return;
        const completing = generationStateRef.current;
        if (completing) {
          const next = { ...completing, status: "COMPLETING" as const };
          generationStateRef.current = next;
          setGenerationState(next);
        }
        finishGeneration("COMPLETED", undefined, event.finishReason);
      },
      (event) => {
        const payload = event && typeof event === "object" ? event as { generationId?: string; error?: string } : undefined;
        if (payload?.generationId && payload.generationId !== activeGeneration.current) return;
        if (activeGeneration.current) finishGeneration("ERROR", payload?.error ?? String(event));
      },
      (event) => {
        if (event.generationId === activeGeneration.current) finishGeneration("CANCELLED", undefined, event.finishReason);
      },
    ).then((items) => {
      if (disposed) items.forEach((cleanup) => cleanup());
      else cleanups = items;
    });
    return () => { disposed = true; cleanups.forEach((cleanup) => cleanup()); };
  }, [chatId, finishGeneration, templateResolver]);

  useEffect(() => {
    const node = textareaRef.current;
    if (!node) return;
    node.style.height = "auto";
    const lineHeight = Number.parseFloat(getComputedStyle(node).lineHeight) || 21;
    node.style.height = `${Math.min(node.scrollHeight, lineHeight * 7 + 20)}px`;
    node.style.overflowY = node.scrollHeight > lineHeight * 7 + 20 ? "auto" : "hidden";
  }, [draft]);

  const greetingMessage = useMemo<ChatMessageRecord | null>(() => character?.greeting ? {
    id: `greeting-${conversation?.id ?? chatId}`,
    conversationId: conversation?.id ?? chatId,
    role: "assistant",
    content: templateResolver.resolve(character.greeting),
    pinned: false,
    metadataJson: JSON.stringify({ senderCharacterId: character.id, greeting: true, source: "character", messageType: "character" }),
    createdAt: conversation?.createdAt ?? now(),
  } : null, [character, conversation, chatId, templateResolver]);
  const visibleMessages = messages.length > 0 ? messages : greetingMessage && !greetingHidden ? [greetingMessage] : [];
  const selectedMessage = messages.find((message) => message.id === selectedMessageId) ?? (greetingMessage?.id === selectedMessageId ? greetingMessage : undefined);
  const scrollToEnd = useCallback((behavior: ScrollBehavior = "smooth") => {
    const node = messagesRef.current;
    if (!node) return;
    node.scrollTo({ top: node.scrollHeight, behavior });
    nearBottom.current = true;
    setShowJumpToEnd(false);
  }, []);

  useLayoutEffect(() => {
    if (nearBottom.current) requestAnimationFrame(() => scrollToEnd("auto"));
  }, [visibleMessages.length, generationState?.status, scrollToEnd]);

  const openActions = useCallback((messageId: string, anchor: DOMRect) => {
    setSelectedMessageId(messageId);
    setMenuAnchor(anchor);
  }, []);
  const closeActions = useCallback(() => {
    setSelectedMessageId(null);
    setMenuAnchor(null);
  }, []);

  const updateTextarea = (value: string) => setDraft(value);

  const copyMessage = useCallback(async (messageId: string) => {
    const message = messages.find((item) => item.id === messageId) ?? (greetingMessage?.id === messageId ? greetingMessage : undefined);
    if (!message) return;
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(message.content);
      } else {
        const helper = document.createElement("textarea");
        helper.value = message.content;
        helper.style.position = "fixed";
        helper.style.opacity = "0";
        document.body.appendChild(helper);
        helper.select();
        document.execCommand("copy");
        helper.remove();
      }
      notify("Mensaje copiado");
    } catch {
      notify("No se pudo acceder al portapapeles");
    }
  }, [greetingMessage, messages, notify]);

  const playMessageVoice = useCallback((messageId: string) => {
    const message = messages.find((item) => item.id === messageId) ?? (greetingMessage?.id === messageId ? greetingMessage : undefined);
    if (!message) return;
    if (!("speechSynthesis" in window)) {
      notify("No se pudo reproducir la voz.");
      return;
    }
    const characterId = senderCharacterId(message, character?.id ?? fallbackGroupCharacter?.id);
    const voiceCharacter = characterId ? characters.find((item) => item.id === characterId) : character ?? fallbackGroupCharacter;
    if (activePlaybackMessageId === messageId) {
      playbackToken.current += 1;
      window.speechSynthesis.cancel();
      setActivePlaybackMessageId(null);
      return;
    }
    const spoken = message.content.replace(/<think>[\s\S]*?<\/think>|<thinking>[\s\S]*?<\/thinking>/gi, "").replace(/\*{1,3}/g, "").trim();
    if (!spoken) {
      notify("Este mensaje no contiene texto para reproducir.");
      return;
    }
    const token = ++playbackToken.current;
    const utterance = new SpeechSynthesisUtterance(spoken);
    const selectedVoice = voiceCharacter?.voiceId ? window.speechSynthesis.getVoices().find((voice) => voice.name === voiceCharacter.voiceId || voice.voiceURI === voiceCharacter.voiceId || voice.lang === voiceCharacter.voiceId) : undefined;
    if (selectedVoice) utterance.voice = selectedVoice;
    utterance.onend = () => { if (playbackToken.current === token) setActivePlaybackMessageId(null); };
    utterance.onerror = () => { if (playbackToken.current === token) { setActivePlaybackMessageId(null); notify("No se pudo reproducir la voz."); } };
    window.speechSynthesis.cancel();
    setActivePlaybackMessageId(messageId);
    window.speechSynthesis.speak(utterance);
    notify(`Reproduciendo la voz de ${voiceCharacter?.name ?? "el personaje"}`);
  }, [activePlaybackMessageId, character, characters, fallbackGroupCharacter, greetingMessage, messages, notify]);

  const stopCurrentGeneration = useCallback(() => {
    if (!generationStateRef.current || !isGenerating) return;
    void stopGeneration().catch(() => undefined);
    finishGeneration("CANCELLED");
  }, [finishGeneration, isGenerating]);

  const toggleMemory = useCallback(async (messageId: string) => {
    const message = messages.find((item) => item.id === messageId) ?? (greetingMessage?.id === messageId ? greetingMessage : undefined);
    if (!message || !conversation) return;
    const nextPinned = !message.pinned;
    const metadata = { ...metadataFor(message), sourceMessageId: message.id, memory: nextPinned };
    const saved = { ...message, pinned: nextPinned, metadataJson: JSON.stringify(metadata) };
    if (isTauriRuntime()) await saveMessage(saved);
    setMessages((current) => current.some((item) => item.id === message.id) ? current.map((item) => item.id === message.id ? saved : item) : [...current, saved]);
    setMemories((current) => nextPinned ? [...current.filter((item) => item !== message.content), message.content] : current.filter((item) => item !== message.content));
    onMemoryChanged?.();
    notify(nextPinned ? "Mensaje guardado en memoria" : "Mensaje quitado de memoria");
  }, [conversation, greetingMessage, messages, notify, onMemoryChanged]);

  const branchMessage = useCallback(async (messageId: string) => {
    if (!conversation || !isTauriRuntime()) {
      notify("Las ramas requieren la aplicación de escritorio");
      return;
    }
    try {
      if (greetingMessage?.id === messageId) await saveMessage(greetingMessage);
      const branch = await branchFromMessage(conversation.id, messageId);
      notify(`Nueva rama creada: ${branch.title}`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    }
  }, [conversation, greetingMessage, notify]);

  const rewindMessage = useCallback(async (messageId: string) => {
    if (!conversation || !window.confirm("¿Eliminar los mensajes posteriores a este punto?")) return;
    if (!isTauriRuntime()) return;
    try {
      if (greetingMessage?.id === messageId) await saveMessage(greetingMessage);
      await rewindToMessage(conversation.id, messageId);
      await reloadMessages();
      notify("Conversación rebobinada");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    }
  }, [conversation, greetingMessage, notify, reloadMessages]);

  const removeMessage = useCallback(async (messageId: string) => {
    if (greetingMessage?.id === messageId && messages.length === 0) {
      setGreetingHidden(true);
      notify("Saludo inicial eliminado");
      return;
    }
    const message = messages.find((item) => item.id === messageId);
    const confirmation = message?.pinned ? "Este mensaje está guardado en memoria. ¿Eliminarlo también?" : "¿Eliminar este mensaje?";
    if (!window.confirm(confirmation)) return;
    if (isTauriRuntime()) await deleteMessage(messageId);
    setMessages((current) => current.filter((item) => item.id !== messageId));
    notify("Mensaje eliminado");
  }, [greetingMessage, messages, notify]);

  const regenerateMessage = useCallback((messageId: string) => {
    const message = messages.find((item) => item.id === messageId);
    if (!message) return;
    setDraft("Regenera la respuesta manteniendo el contexto.");
    setActionMode(false);
    notify("Regeneración preparada en el compositor");
  }, [messages, notify]);

  async function send(options?: { retry?: boolean }) {
    const retry = options?.retry === true;
    const retryUser = retry ? lastUserMessage.current : null;
    const raw = retry ? retryUser?.content.trim() ?? "" : draft.trim();
    if (isGenerating || !conversationId) return;
    const wantsContinue = retry ? lastGenerationMode.current === "continue" : raw.length === 0;
    const explicitProvider = preferredProviderId ? providers.find((item) => item.id === preferredProviderId && item.enabled && item.endpoint && item.modelName) : undefined;
    // The Rust runtime is the source of truth for its private loopback port.
    // The WebView only gates on the published READY state; the command performs
    // the authoritative child/port/model preflight before starting a request.
    // `runtimeState` and `loadedModelId` are the published UI state. The
    // backend command performs the authoritative child/health preflight, so
    // a stale `running` boolean must not block an otherwise loaded model.
    const localReady = engine.runtimeState === "READY" && Boolean(engine.loadedModelId);
    const provider = explicitProvider;
    if (!provider && !localReady) {
      const message = engine.runtimeState === "STARTING" || engine.runtimeState === "LOADING_MODEL"
        ? "El modelo todavía se está cargando."
        : "Carga un modelo antes de conversar.";
      setError(message);
      onNotice?.(message);
      return;
    }
    const content = wantsContinue ? "" : actionMode ? `**${raw.replace(/^\*\*|\*\*$/g, "")}**` : raw;
    const userId = retry || wantsContinue ? undefined : crypto.randomUUID();
    const assistantId = crypto.randomUUID();
    const generationId = crypto.randomUUID();
    const initial = messages.length === 0 && greetingMessage ? greetingMessage : null;
    const user: ChatMessageRecord | null = userId ? { id: userId, conversationId, role: "user", content, pinned: false, metadataJson: JSON.stringify({ source: "user", messageType: "user" }), createdAt: now() } : null;
    const assistantMetadata = { senderCharacterId: character?.id ?? fallbackGroupCharacter?.id, source: "character", messageType: "character", generated: true };
    const assistant: ChatMessageRecord = { id: assistantId, conversationId, role: "assistant", content: "", pinned: false, metadataJson: JSON.stringify(assistantMetadata), createdAt: now() };
    const optimisticMessages = [...(initial ? [initial] : messageRecordsRef.current), ...(user ? [user] : []), assistant];
    messageRecordsRef.current = optimisticMessages;
    setMessages(optimisticMessages);
    setDraft("");
    setActionMode(false);
    setError(null);
    setRetryAvailable(false);
    if (!retry) {
      lastUserMessage.current = user;
      lastGenerationMode.current = wantsContinue ? "continue" : "send";
    }
    assistantIds.current.set(generationId, assistantId);
    assistantContent.current.set(generationId, "");
    assistantRecords.current.set(generationId, assistant);
    activeGeneration.current = generationId;
    const startedState: GenerationState = { generationId, conversationId, messageId: assistantId, status: "WAITING_FIRST_TOKEN", startedAt: now() };
    generationStateRef.current = startedState;
    setGenerationState(startedState);
    try {
      if (isTauriRuntime()) {
        if (initial) await saveMessage(initial);
        if (user) {
          await saveMessage(user);
          void persistSemanticRecords([user]);
        }
      }
    } catch (cause) {
      finishGeneration("ERROR", cause instanceof Error ? cause.message : String(cause));
      return;
    }
    const configuredContext = Number(localStorage.getItem("local-character.desktop.context") ?? "8192");
    const modelContext = Number(engine.contextLength ?? configuredContext);
    const contextLimit = Math.max(1024, Math.min(Number.isFinite(configuredContext) ? configuredContext : 8192, Number.isFinite(modelContext) && modelContext > 0 ? modelContext : 8192));
    const continuityInput = {
      conversationId,
      characterId: character?.id ?? fallbackGroupCharacter?.id,
      groupId: group?.id,
      messages,
      greeting: initial,
      currentMessage: user,
      summary: summaryRef.current,
      memories: contextMemories,
      contextLimit,
      reserveOutput: 512,
      systemTokenReserve: 2200,
    } as const;
    let continuityBuild = buildContinuityContext(continuityInput);
    let continuity: ContinuityContext = continuityBuild.context;
    let roleplaySystemPrompt = buildRoleplaySystemPrompt({
      character,
      group,
      characters,
      userName,
      language: localStorage.getItem("local-character.desktop.language") ?? "es",
      memories: continuity.relevantMemories,
      continuity,
      continueGeneration: wantsContinue,
    });
    // The character card can be much larger than a normal prompt. Rebuild the
    // history selection once with the real system size so the context window
    // is never consumed by a blind fixed message count.
    const systemTokens = estimateTokens(roleplaySystemPrompt);
    if (systemTokens > continuityInput.systemTokenReserve && systemTokens + 512 + continuity.tokenEstimate > contextLimit) {
      continuityBuild = buildContinuityContext({ ...continuityInput, systemTokenReserve: systemTokens + 96 });
      continuity = continuityBuild.context;
      roleplaySystemPrompt = buildRoleplaySystemPrompt({
        character,
        group,
        characters,
        userName,
        language: localStorage.getItem("local-character.desktop.language") ?? "es",
        memories: continuity.relevantMemories,
        continuity,
        continueGeneration: wantsContinue,
      });
    }
    if (continuityBuild.summaryToPersist && isTauriRuntime()) {
      summaryRef.current = continuityBuild.summaryToPersist;
      setConversationSummary(continuityBuild.summaryToPersist);
      void saveConversationSummary(continuityBuild.summaryToPersist).catch(() => undefined);
    }
    const history = roleplayHistory(continuity.recentMessages, null, undefined, templateResolver);
    // Continue is a generation mode, not a fake user turn. The exact
    // instruction lives in the system context and the transcript keeps its
    // real user/assistant roles intact for local and remote providers.
    const protocolMessages = [{ role: "system" as const, content: roleplaySystemPrompt }, ...history];
    if (import.meta.env.DEV) {
      localStorage.setItem("local-character.desktop.lastPrompt", JSON.stringify({
        characterId: character?.id ?? null,
        conversationId,
        provider: provider?.kind ?? "local",
        model: engine.loadedModelId ?? null,
        modelName: engine.loadedModelName ?? null,
        architecture: engine.modelArchitecture ?? null,
        template: engine.chatTemplate ? "embedded" : "missing",
        contextLength: engine.contextLength ?? null,
        system: roleplaySystemPrompt,
        messages: protocolMessages,
        memoryCount: contextMemories.length,
        loreCount: character?.lore?.length ?? 0,
        continuity: {
          currentTopic: continuity.currentTopic,
          currentSituation: continuity.currentSituation,
          currentLocation: continuity.currentLocation,
          summaryUntilMessageId: continuity.summaryUntilMessageId ?? null,
          recentMessagesCount: continuity.recentMessageCount,
          relevantMemoryCount: continuity.relevantMemories.length,
          estimatedTokens: continuity.tokenEstimate,
          continueGeneration: wantsContinue,
        },
      }));
    }
    if (provider) {
      try {
        const base = provider.endpoint!.replace(/\/+$/, "");
        const endpoint = base.endsWith("/chat/completions") ? base : `${base}/chat/completions`;
        const response = await fetch(endpoint, { method: "POST", headers: { "Content-Type": "application/json", ...(provider.apiKey ? { Authorization: `Bearer ${provider.apiKey}` } : {}) }, body: JSON.stringify({ model: provider.modelName, messages: protocolMessages, max_tokens: 512 }) });
        if (!response.ok) throw new Error(`La API respondió ${response.status}`);
        const payload = await response.json() as { choices?: Array<{ message?: { content?: string } }> };
        const reply = templateResolver.cleanGeneratedContent(payload.choices?.[0]?.message?.content);
        if (!reply) throw new Error("La API no devolvió contenido");
        assistantContent.current.set(generationId, reply);
        setMessages((current) => current.map((message) => message.id === assistantId ? { ...message, content: reply } : message));
        finishGeneration("COMPLETED");
      } catch (cause) {
        const message = cause instanceof TypeError ? "No se pudo conectar con la API seleccionada." : cause instanceof Error ? cause.message : String(cause);
        finishGeneration("ERROR", message);
      }
      return;
    }
    try {
      if (!localReady) {
        finishGeneration("ERROR", "El modelo local todavía no está listo.");
        return;
      }
      // GPU is the default when the user has not explicitly selected CPU in
      // Ajustes. -1 tells llama.cpp to offload every layer that fits in VRAM;
      // any remainder is handled by the CPU automatically.
      await sendChatMessage({ prompt: roleplaySystemPrompt, messages: protocolMessages, characterName: character?.name ?? fallbackGroupCharacter?.name, userName, generationId, conversationId, messageId: assistantId, maxOutput: 512, context: contextLimit, gpuLayers: Number(localStorage.getItem("local-character.desktop.gpuLayers") ?? "-1") });
    } catch (cause) {
      finishGeneration("ERROR", cause instanceof Error ? cause.message : String(cause));
    }
  }
  const onComposerKeyDown = (event: ReactKeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey) {
      if (!draft.trim()) return;
      event.preventDefault();
      void send();
    }
  };

  const composerAction = isGenerating ? "stop" : draft.trim() ? "send" : "continue";
  const toggleActionMode = () => {
    setActionMode((current) => !current);
    requestAnimationFrame(() => textareaRef.current?.focus());
  };
  const retryLastGeneration = () => {
    if (!retryAvailable || isGenerating) return;
    setDraft("");
    void send({ retry: true });
  };

  return <div className="panel chat-panel chat-panel-modern">
    <div className="chat-content-shell">
    <div className="chat-messages message-list" ref={messagesRef} onScroll={(event) => { const node = event.currentTarget; nearBottom.current = node.scrollHeight - node.scrollTop - node.clientHeight < 80; setShowJumpToEnd(!nearBottom.current); }}>
      <div className="message-thread-container">
      {visibleMessages.length === 0 ? <div className="chat-empty-compact"><MessageCircle size={20} /><span>Escribe un mensaje para comenzar la historia.</span></div> : visibleMessages.map((message) => {
        if (!message.content.trim() && message.id === generationState?.messageId) return null;
        const characterId = senderCharacterId(message, message.role === "assistant" ? character?.id ?? fallbackGroupCharacter?.id : undefined);
        const senderCharacter = message.role === "user" ? undefined : characters.find((item) => item.id === characterId) ?? character ?? fallbackGroupCharacter;
        const senderName = message.role === "user" ? userName : senderCharacter?.name ?? (message.role === "assistant" ? "Personaje" : "Sistema");
        const messageIsStreaming = message.id === generationState?.messageId && ["WAITING_FIRST_TOKEN", "STREAMING", "COMPLETING"].includes(generationState.status);
        return <MessageItem key={message.id} message={message} senderName={senderName} avatarPath={senderCharacter?.avatarPath} isStreaming={messageIsStreaming} isPlaying={activePlaybackMessageId === message.id} onPlayVoice={message.role === "assistant" && message.content.trim() ? playMessageVoice : undefined} onOpenActions={openActions} />;
      })}
      {waitingFirstToken && <CharacterStreamingItem senderName={character?.name ?? fallbackGroupCharacter?.name ?? "Personaje"} avatarPath={character?.avatarPath ?? fallbackGroupCharacter?.avatarPath} onStop={stopCurrentGeneration} />}
      </div>
    </div>
    {showJumpToEnd && <button className="jump-to-end" onClick={() => scrollToEnd()}><ChevronDown size={15} /> Ir al final</button>}
    <div className="composer composer-modern">
      <textarea ref={textareaRef} value={draft} onChange={(event) => updateTextarea(event.target.value)} onKeyDown={onComposerKeyDown} placeholder={actionMode ? "Escribe una acción…" : "Escribe un mensaje…"} rows={1} aria-label="Mensaje" />
      <div className="composer-controls"><button className={`composer-action-toggle ${actionMode ? "selected" : ""}`} onClick={toggleActionMode} type="button" aria-label={actionMode ? "Desactivar modo acción" : "Activar modo acción"} title="Modo acción"><span aria-hidden="true">**</span></button>{composerAction === "stop" ? <button className="primary-button send-button stop-generation-button" onClick={stopCurrentGeneration} aria-label="Detener generación" title="Detener generación"><Square size={16} /></button> : <button className="primary-button send-button" onClick={() => void send()} disabled={!conversationId} aria-label={composerAction === "continue" ? "Continuar escena" : "Enviar mensaje"} title={composerAction === "continue" ? "Continuar escena" : "Enviar mensaje"}>{composerAction === "continue" ? <RotateCcw size={17} /> : <Send size={17} />}</button>}</div>
    </div>
    {error && <div className="inline-error"><span>{error}</span>{retryAvailable && <button className="text-button" onClick={retryLastGeneration}>Reintentar</button>}</div>}
    </div>
    {selectedMessage && menuAnchor && <MessageActionsMenu messageId={selectedMessage.id} senderType={selectedMessage.role === "assistant" ? "assistant" : selectedMessage.role === "user" ? "user" : "system"} isPinned={selectedMessage.pinned} anchor={menuAnchor} onCopy={copyMessage} onToggleMemory={toggleMemory} onBranch={branchMessage} onRewind={rewindMessage} onDelete={removeMessage} onRegenerate={selectedMessage.role === "assistant" ? regenerateMessage : undefined} onClose={closeActions} />}
  </div>;
}
