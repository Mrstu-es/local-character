function canonicalBlock(value: string): string {
  return value
    .toLocaleLowerCase()
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/\{\{\s*(?:user|char|character)\s*\}\}/giu, "")
    .replace(/[\p{P}\p{S}\s]+/gu, " ")
    .trim();
}

/**
 * OpenAI-compatible servers normally emit token deltas, but a few GGUF chat
 * templates and proxies emit the complete accumulated answer on every event.
 * This function accepts either format and keeps one monotonic transcript.
 */
export function mergeStreamText(current: string, incoming: string): string {
  if (!incoming) return current;
  if (!current) return incoming;
  if (incoming.startsWith(current)) return incoming;
  if (current.startsWith(incoming) && incoming.length >= 8) return current;
  if (incoming === current && incoming.length >= 4) return current;
  if (current.endsWith(incoming) && incoming.length >= 12) return current;

  const maximum = Math.min(current.length, incoming.length);
  for (let overlap = maximum; overlap >= 12; overlap -= 1) {
    if (current.endsWith(incoming.slice(0, overlap))) return current + incoming.slice(overlap);
  }
  return current + incoming;
}

/**
 * Removes repeated long paragraphs/lines from a completed roleplay response.
 * Short repetitions are kept because they are often intentional dialogue
 * ("no, no" or a stutter). Only later copies are discarded.
 */
export function deduplicateRoleplayBlocks(value: string): string {
  const paragraphs = value.replace(/\r\n?/g, "\n").split(/\n{2,}/);
  const seen = new Set<string>();
  const kept: string[] = [];
  for (const paragraph of paragraphs) {
    const lines = paragraph.split("\n");
    const keptLines: string[] = [];
    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed) continue;
      const key = canonicalBlock(trimmed);
      if (key.length >= 32 && seen.has(key)) continue;
      if (key.length >= 32) seen.add(key);
      keptLines.push(trimmed);
    }
    const candidate = keptLines.join("\n").trim();
    if (!candidate) continue;
    const key = canonicalBlock(candidate);
    if (key.length >= 48 && seen.has(`paragraph:${key}`)) continue;
    if (key.length >= 48) seen.add(`paragraph:${key}`);
    kept.push(candidate);
  }
  return kept.join("\n\n").trim();
}

export interface MessageLike {
  id: string;
  role: string;
  content: string;
  createdAt?: string;
  metadataJson?: string;
  pinned?: boolean;
}

function messageMetadata(message: MessageLike): Record<string, unknown> {
  try {
    const parsed = JSON.parse(message.metadataJson || "{}");
    return parsed && typeof parsed === "object" ? parsed as Record<string, unknown> : {};
  } catch {
    return {};
  }
}

function safeGeneratedReplay(previous: MessageLike, current: MessageLike): boolean {
  if (previous.pinned || current.pinned) return false;
  const previousMeta = messageMetadata(previous);
  const currentMeta = messageMetadata(current);
  if (previousMeta.greeting || currentMeta.greeting) return false;
  if (previousMeta.generated !== true || currentMeta.generated !== true) return false;
  const previousSender = typeof previousMeta.senderCharacterId === "string" ? previousMeta.senderCharacterId : "";
  const currentSender = typeof currentMeta.senderCharacterId === "string" ? currentMeta.senderCharacterId : "";
  if (!previousSender || previousSender !== currentSender) return false;
  const previousReply = typeof previousMeta.replyToId === "string" ? previousMeta.replyToId : "";
  const currentReply = typeof currentMeta.replyToId === "string" ? currentMeta.replyToId : "";
  if (previousReply || currentReply) return Boolean(previousReply && previousReply === currentReply);
  const previousTime = Date.parse(previous.createdAt ?? "");
  const currentTime = Date.parse(current.createdAt ?? "");
  return Number.isFinite(previousTime) && Number.isFinite(currentTime) && Math.abs(currentTime - previousTime) <= 10_000;
}

/** Hides only exact generated replays with no user turn between. */
export function collapseConsecutiveDuplicateAssistants<T extends MessageLike>(messages: T[]): { messages: T[]; duplicateIds: string[] } {
  const result: T[] = [];
  const duplicateIds: string[] = [];
  for (const message of messages) {
    const previous = result[result.length - 1];
    const currentKey = canonicalBlock(message.content);
    const previousKey = previous ? canonicalBlock(previous.content) : "";
    if (message.role === "assistant" && previous?.role === "assistant" && currentKey.length >= 32 && currentKey === previousKey && safeGeneratedReplay(previous, message)) {
      duplicateIds.push(message.id);
      continue;
    }
    result.push(message);
  }
  return { messages: result, duplicateIds };
}
