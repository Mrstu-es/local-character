import type { ChatMessageRecord, SemanticMemoryKind, SemanticMemoryRecord } from "../types";

export interface SemanticExtractionInput {
  messages: ChatMessageRecord[];
  characterId: string;
  conversationId: string;
  characterName: string;
  userName: string;
  at?: string;
}

type Candidate = {
  kind: SemanticMemoryKind;
  subject: string;
  topic: string;
  content: string;
  confidence: number;
  sourceMessageId: string;
};

const META_OR_SECRET = /(?:step[- ]by[- ]step|chain of thought|reasoning|analysis|system prompt|developer message|el modelo|la ia esta|la ia está|razonamiento|solicitud del usuario|api[_ -]?key|password|contrase(?:ñ|n)a|bearer\s+[a-z0-9._-]{12,}|sk-[a-z0-9_-]{12,})/iu;
const QUESTION = /[?¿]/u;

function compact(value: string): string {
  return value.replace(/\s+/gu, " ").replace(/^[\s"“”'«»:-]+|[\s"“”'«»:-]+$/gu, "").trim();
}

function canonical(value: string): string {
  return compact(value)
    .toLocaleLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/gu, "")
    .replace(/\b(?:el|la|los|las|un|una|unos|unas|mucho|muchos|muchas|bastante|realmente)\b/gu, " ")
    .replace(/[^a-z0-9]+/gu, " ")
    .trim()
    .split(" ")
    .filter(Boolean)
    .slice(0, 9)
    .join("-");
}

function hash(value: string): string {
  let result = 0x811c9dc5;
  for (let index = 0; index < value.length; index += 1) {
    result ^= value.charCodeAt(index);
    result = Math.imul(result, 0x01000193);
  }
  return (result >>> 0).toString(36);
}

function visibleStatements(content: string): string[] {
  const withoutActions = content
    .replace(/<think>[\s\S]*?<\/think>|<thinking>[\s\S]*?<\/thinking>|<analysis>[\s\S]*?<\/analysis>/giu, " ")
    .replace(/\*{1,3}[^*\n]{1,500}\*{1,3}/gu, " ")
    .replace(/```[\s\S]*?```/gu, " ");
  return withoutActions
    .split(/(?:\r?\n)+|(?<=[.!;])\s+/gu)
    .map(compact)
    .filter((statement) => statement.length >= 5 && statement.length <= 280 && !QUESTION.test(statement) && !META_OR_SECRET.test(statement));
}

function addCandidate(target: Candidate[], candidate: Candidate) {
  const content = compact(candidate.content).slice(0, 280);
  const topic = canonical(candidate.topic);
  if (!content || !topic || META_OR_SECRET.test(content)) return;
  target.push({ ...candidate, content, topic });
}

function candidatesForMessage(
  message: ChatMessageRecord,
  characterName: string,
  userName: string,
): Candidate[] {
  if (!message.content.trim() || !["user", "assistant"].includes(message.role)) return [];
  const subject = message.role === "assistant" ? characterName : userName;
  const candidates: Candidate[] = [];
  for (const statement of visibleStatements(message.content)) {
    const preference = statement.match(/\b(no\s+me\s+gustan?|no\s+me\s+encantan?|no\s+soporto|me\s+gustan?|me\s+encantan?|prefiero|odio|detesto)\s+(.{2,160})$/iu);
    if (preference) {
      addCandidate(candidates, {
        kind: "preference",
        subject,
        topic: preference[2],
        content: statement,
        confidence: 0.92,
        sourceMessageId: message.id,
      });
    }

    if (message.role === "assistant") {
      const opinion = statement.match(/\b(?:creo|pienso|considero|opino)\s+que\s+(.{3,180})$/iu)
        ?? statement.match(/\bpara\s+m[ií][, ]+(.{3,180})$/iu);
      if (opinion) {
        addCandidate(candidates, {
          kind: "opinion",
          subject,
          topic: opinion[1],
          content: statement,
          confidence: 0.86,
          sourceMessageId: message.id,
        });
      }
    }

    const relationshipPatterns: Array<[RegExp, string]> = [
      [/\b(?:no\s+conf[ií]o\s+en\s+ti|no\s+me\s+f[ií]o\s+de\s+ti|conf[ií]o\s+en\s+ti|me\s+f[ií]o\s+de\s+ti)\b/iu, "trust"],
      [/\b(?:te\s+amo|te\s+quiero|te\s+odio|me\s+caes\s+bien|me\s+caes\s+mal)\b/iu, "affection"],
      [/\b(?:somos\s+amig(?:os|as)|eres\s+mi\s+amig[oa]|eres\s+mi\s+pareja|somos\s+pareja)\b/iu, "bond"],
    ];
    for (const [pattern, relationKey] of relationshipPatterns) {
      if (!pattern.test(statement)) continue;
      addCandidate(candidates, {
        kind: "relationship",
        subject,
        topic: `${relationKey}-${message.role === "assistant" ? userName : characterName}`,
        content: statement,
        confidence: 0.94,
        sourceMessageId: message.id,
      });
    }

    if (message.role === "user") {
      const fact = statement.match(/\b(?:me\s+llamo|mi\s+nombre\s+es|vivo\s+en|trabajo\s+como|estudio|soy\s+al[eé]rgic[oa]\s+a|mi\s+cumplea[nñ]os\s+es)\s+(.{2,150})$/iu);
      if (fact) {
        addCandidate(candidates, {
          kind: "fact",
          subject,
          topic: statement.match(/^\s*([^,.:]{2,42})/u)?.[1] ?? fact[1],
          content: statement,
          confidence: 0.9,
          sourceMessageId: message.id,
        });
      }
    }

    const event = statement.match(/\b(?:te\s+prometo|prometimos|acordamos)\s+(.{3,170})$/iu);
    if (event) {
      addCandidate(candidates, {
        kind: "event",
        subject,
        topic: event[1],
        content: statement,
        confidence: 0.88,
        sourceMessageId: message.id,
      });
    }
  }
  return candidates;
}

export function extractSemanticMemories(input: SemanticExtractionInput): SemanticMemoryRecord[] {
  if (!input.characterId || !input.conversationId) return [];
  const timestamp = input.at ?? new Date().toISOString();
  const byKey = new Map<string, SemanticMemoryRecord>();
  for (const message of input.messages.slice(-40)) {
    for (const candidate of candidatesForMessage(message, input.characterName, input.userName)) {
      const memoryKey = `${candidate.kind}:${canonical(candidate.subject)}:${candidate.topic}`;
      const id = `semantic-${hash(`${input.characterId}|${input.conversationId}|${memoryKey}`)}`;
      byKey.set(memoryKey, {
        id,
        characterId: input.characterId,
        conversationId: input.conversationId,
        kind: candidate.kind,
        subject: candidate.subject,
        memoryKey,
        content: candidate.content,
        confidence: candidate.confidence,
        sourceMessageId: candidate.sourceMessageId,
        createdAt: timestamp,
        updatedAt: timestamp,
      });
    }
  }
  return [...byKey.values()];
}

export function mergeSemanticMemories(current: SemanticMemoryRecord[], incoming: SemanticMemoryRecord[]): SemanticMemoryRecord[] {
  // Database reads put the current conversation first. Keep that first record
  // when the same opinion also exists in an older chat; newly extracted turns
  // still replace it explicitly through `incoming`.
  const byKey = new Map<string, SemanticMemoryRecord>();
  for (const memory of current) {
    if (!byKey.has(memory.memoryKey)) byKey.set(memory.memoryKey, memory);
  }
  for (const memory of incoming) byKey.set(memory.memoryKey, memory);
  return [...byKey.values()]
    .sort((left, right) => right.confidence - left.confidence || right.updatedAt.localeCompare(left.updatedAt))
    .slice(0, 120);
}

export function semanticMemoryPromptText(memory: SemanticMemoryRecord): string {
  const labels: Record<SemanticMemoryKind, string> = {
    fact: "hecho estable",
    preference: "preferencia estable",
    opinion: "opinion propia",
    relationship: "relacion",
    event: "compromiso pendiente",
  };
  return `[${labels[memory.kind]} de ${memory.subject}] ${memory.content}`;
}
