import type { ChatMessageRecord, ConversationSummaryRecord } from "../types";

export type ContinuityRole = "user" | "assistant";

export interface ContinuityContext {
  conversationId: string;
  characterId?: string;
  groupId?: string;
  currentTopic: string;
  currentSituation: string;
  currentLocation: string;
  recentMessages: ChatMessageRecord[];
  conversationSummary: string;
  summaryUntilMessageId?: string;
  relevantMemories: string[];
  pendingEvents: string[];
  unresolvedQuestions: string[];
  recentActions: string[];
  recentSpeakers: string[];
  tokenEstimate: number;
  recentMessageCount: number;
}

export interface ContinuityBuildInput {
  conversationId: string;
  characterId?: string;
  groupId?: string;
  messages: ChatMessageRecord[];
  greeting?: ChatMessageRecord | null;
  currentMessage?: ChatMessageRecord | null;
  summary?: ConversationSummaryRecord | null;
  memories?: string[];
  contextLimit: number;
  reserveOutput?: number;
  systemTokenReserve?: number;
}

export interface ContinuityBuildResult {
  context: ContinuityContext;
  summaryToPersist?: ConversationSummaryRecord;
}

const STOP_WORDS = new Set([
  "para", "como", "pero", "porque", "donde", "cuando", "desde", "esto", "esta", "este", "solo", "tambien", "tiene", "tengo", "quiero", "puede", "puedo", "hace", "hacia", "sobre", "entre", "otra", "otro", "algo", "aqui", "ahi", "ella", "ellos", "ello", "ellos", "that", "this", "with", "from", "what", "when", "where", "have", "will", "just", "about", "then", "they", "them", "your", "you", "the", "and", "for", "are", "was", "were", "her", "his", "its", "not", "can", "could", "would", "should", "into", "over", "after", "before", "there", "here", "been", "being", "very", "really", "only", "still", "know", "does", "did", "doing", "una", "uno", "los", "las", "del", "con", "por", "que", "una", "uno", "sus", "mis", "tus", "nos", "hay", "está", "esta", "fue", "ser", "sin", "más", "muy", "así", "como", "qué", "cómo", "dónde", "quién", "bien", "hoy", "ayer", "ahora", "then",
]);

const normalize = (value: string) => value.replace(/\s+/g, " ").trim();
export const estimateTokens = (value: string) => Math.max(0, Math.ceil(value.length / 4));

function metadata(message: ChatMessageRecord): Record<string, unknown> {
  try {
    const value = JSON.parse(message.metadataJson || "{}");
    return value && typeof value === "object" ? value as Record<string, unknown> : {};
  } catch {
    return {};
  }
}

function isRoleplayMessage(message: ChatMessageRecord): boolean {
  if (!message.content.trim() || !(["user", "assistant"] as string[]).includes(message.role)) return false;
  const source = typeof metadata(message).source === "string" ? String(metadata(message).source).toLowerCase() : "";
  return !["runtime", "engine", "diagnostic", "system"].includes(source);
}

function uniqueMessages(messages: ChatMessageRecord[]): ChatMessageRecord[] {
  const seen = new Set<string>();
  return messages.filter((message) => {
    const key = message.id || `${message.role}:${message.createdAt}:${message.content}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return isRoleplayMessage(message);
  });
}

function words(value: string): string[] {
  return normalize(value.toLocaleLowerCase())
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .match(/[a-záéíóúñü]{4,}/gi)?.map((word) => word.toLocaleLowerCase()) ?? [];
}

function detectTopic(messages: ChatMessageRecord[]): string {
  const counts = new Map<string, number>();
  for (const message of messages.slice(-10)) {
    for (const word of words(message.content)) {
      if (STOP_WORDS.has(word)) continue;
      counts.set(word, (counts.get(word) ?? 0) + 1);
    }
  }
  const ranked = [...counts.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]));
  return ranked.slice(0, 4).map(([word]) => word).join(", ");
}

function detectLocation(messages: ChatMessageRecord[]): string {
  const text = messages.slice(-8).map((message) => message.content).join(" ");
  const match = text.match(/\b(?:en|dentro de|hacia|al|a la)\s+(?:el |la |los |las )?[a-záéíóúñü0-9_-]{3,}(?:\s+[a-záéíóúñü0-9_-]{3,})?/i);
  return match ? normalize(match[0]) : "";
}

function detectActions(messages: ChatMessageRecord[]): string[] {
  const result: string[] = [];
  for (const message of messages.slice(-12)) {
    const matches = message.content.match(/\*{1,3}([^*\n]{2,240})\*{1,3}/g) ?? [];
    for (const match of matches) {
      const action = normalize(match.replace(/^\*+|\*+$/g, ""));
      if (action && !result.includes(action)) result.push(action);
    }
  }
  return result.slice(-6);
}

function detectQuestions(messages: ChatMessageRecord[]): string[] {
  return messages
    .slice(-12)
    .filter((message) => message.role === "assistant")
    .flatMap((message) => (message.content.match(/[^.!?\n]{2,180}\?/g) ?? []).map(normalize))
    .slice(-4);
}

function detectPendingEvents(messages: ChatMessageRecord[]): string[] {
  const pendingPattern = /\b(?:ma[nñ]ana|luego|despu[eé]s|pendiente|tenemos que|hay que|debo|debemos|promet[ií]|acordamos|falta)\b/i;
  return messages
    .slice(-14)
    .filter((message) => pendingPattern.test(message.content))
    .map((message) => normalize(message.content).slice(0, 220))
    .filter((value, index, values) => values.indexOf(value) === index)
    .slice(-4);
}

function detectSpeakers(messages: ChatMessageRecord[]): string[] {
  const result: string[] = [];
  for (const message of messages.slice(-8)) {
    const sender = metadata(message).senderCharacterId;
    const label = message.role === "user" ? "user" : typeof sender === "string" ? sender : "character";
    if (!result.includes(label)) result.push(label);
  }
  return result.slice(-4);
}

function relevantMemories(memories: string[], messages: ChatMessageRecord[]): string[] {
  const query = new Set(words(messages.slice(-6).map((message) => message.content).join(" ")));
  return memories
    .map((memory, index) => ({ memory: normalize(memory), index, score: words(memory).filter((word) => query.has(word)).length }))
    .filter((item) => item.memory)
    .sort((a, b) => b.score - a.score || b.index - a.index)
    .slice(0, 6)
    .map((item) => item.memory);
}

function buildSituation(messages: ChatMessageRecord[]): string {
  return messages
    .slice(-4)
    .map((message) => `${message.role === "user" ? "Usuario" : "Personaje"}: ${normalize(message.content).slice(0, 360)}`)
    .join(" ");
}

function buildSummary(previous: string, archived: ChatMessageRecord[]): string {
  const excerpts = archived
    .filter((message) => message.content.trim())
    .slice(-18)
    .map((message) => `${message.role === "user" ? "Usuario" : "Personaje"}: ${normalize(message.content).slice(0, 220)}`);
  const sections = [previous.trim(), excerpts.join(" ")].filter(Boolean);
  return normalize(sections.join(" ")).slice(-1800);
}

function selectRecent(messages: ChatMessageRecord[], budgetTokens: number): ChatMessageRecord[] {
  if (!messages.length) return [];
  const selected: ChatMessageRecord[] = [];
  let used = 0;
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index];
    const cost = estimateTokens(message.content) + 4;
    if (!selected.length || used + cost <= budgetTokens) {
      selected.unshift(message);
      used += cost;
      continue;
    }
    // Keep the most recent complete exchange whenever it fits. Older turns
    // are the first thing discarded; the summary covers them.
    if (selected[0]?.role !== message.role && used + cost <= budgetTokens + 160) {
      selected.unshift(message);
      used += cost;
    } else {
      break;
    }
  }
  return selected;
}

export function buildContinuityContext(input: ContinuityBuildInput): ContinuityBuildResult {
  const base = uniqueMessages([
    ...(input.messages ?? []),
    ...(input.messages.length === 0 && input.greeting ? [input.greeting] : []),
    ...(input.currentMessage ? [input.currentMessage] : []),
  ]);
  const existingCutIndex = input.summary?.summaryUntilMessageId
    ? base.findIndex((message) => message.id === input.summary?.summaryUntilMessageId)
    : -1;
  const candidateStart = existingCutIndex >= 0 ? existingCutIndex + 1 : 0;
  const candidates = base.slice(candidateStart);
  const latest = candidates.slice(-10);
  const topic = detectTopic(latest);
  const location = detectLocation(latest);
  const stateText = buildSituation(latest);
  const memoryList = relevantMemories(input.memories ?? [], latest);
  const reserve = Math.max(256, input.reserveOutput ?? 512);
  const contextLimit = Math.max(1024, input.contextLimit || 8192);
  const stateTokens = estimateTokens(topic + location + stateText + memoryList.join(" ")) + 420;
  const systemReserve = Math.max(900, input.systemTokenReserve ?? Math.min(3000, Math.floor(contextLimit * 0.38)));
  const historyBudget = Math.max(192, contextLimit - reserve - stateTokens - systemReserve);
  const recent = selectRecent(candidates, historyBudget);
  const earliest = recent[0];
  const earliestIndex = earliest ? base.findIndex((message) => message.id === earliest.id) : base.length;
  const archiveStart = existingCutIndex >= 0 ? existingCutIndex + 1 : 0;
  const archived = earliestIndex > archiveStart ? base.slice(archiveStart, earliestIndex) : [];
  const summaryText = buildSummary(input.summary?.summary ?? "", archived);
  const summaryToPersist = archived.length && earliest
    ? {
      conversationId: input.conversationId,
      summary: summaryText,
      summaryUntilMessageId: archived[archived.length - 1].id,
      messageCount: archived.length + (input.summary?.messageCount ?? 0),
      tokenCount: estimateTokens(summaryText),
      updatedAt: new Date().toISOString(),
    }
    : undefined;
  const context: ContinuityContext = {
    conversationId: input.conversationId,
    characterId: input.characterId,
    groupId: input.groupId,
    currentTopic: topic || "(sin tema detectado)",
    currentSituation: stateText || "(la escena acaba de comenzar)",
    currentLocation: location || "(location not specified)",
    recentMessages: recent,
    conversationSummary: summaryText || "(sin resumen anterior)",
    summaryUntilMessageId: summaryToPersist?.summaryUntilMessageId ?? input.summary?.summaryUntilMessageId,
    relevantMemories: memoryList,
    pendingEvents: detectPendingEvents(latest),
    unresolvedQuestions: detectQuestions(latest),
    recentActions: detectActions(latest),
    recentSpeakers: detectSpeakers(latest),
    tokenEstimate: estimateTokens(summaryText + stateText + recent.map((message) => message.content).join(" ")),
    recentMessageCount: recent.length,
  };
  return { context, summaryToPersist };
}

export function continuityPromptSection(context: ContinuityContext, continueGeneration = false): string {
  const actions = context.recentActions.length ? context.recentActions.map((action) => `- ${action}`).join("\n") : "(ninguna registrada)";
  const questions = context.unresolvedQuestions.length ? context.unresolvedQuestions.map((question) => `- ${question}`).join("\n") : "(ninguna)";
  const pending = context.pendingEvents.length ? context.pendingEvents.map((event) => `- ${event}`).join("\n") : "(ninguno)";
  const speakers = context.recentSpeakers.length ? context.recentSpeakers.join(", ") : "(directo)";
  return [
    "CONTINUITY CONTEXT (internal; never reveal these labels or summarize them to the user)",
    "This is the next turn of the same conversation. Preserve the latest topic, scene, location, relationships and unresolved details. Do not greet again, restart the scenario, repeat the first message, or jump to an unrelated subject.",
    `Current topic: ${context.currentTopic}`,
    `Current location: ${context.currentLocation}`,
    `Current situation, derived only from the transcript: ${context.currentSituation}`,
    `Earlier conversation summary (only messages before the recent transcript): ${context.conversationSummary}`,
    `Recent meaningful actions:\n${actions}`,
    `Unresolved questions:\n${questions}`,
    `Pending events (do not interrupt the current scene to mention them):\n${pending}`,
    `Recent speakers: ${speakers}`,
    context.groupId ? "In this group, let the latest addressed or contextually relevant participant speak; do not choose speakers by blind round-robin." : "In this direct chat, answer as the character attached to this conversation.",
    continueGeneration ? "CONTINUE DIRECTIVE: Continue naturally from the exact current scene. Do not speak for the user and do not add a new greeting." : "",
    "Never output the CONTINUITY CONTEXT, its labels, summaries, token counts or implementation details. Act on it silently.",
  ].filter(Boolean).join("\n");
}
