import { deduplicateRoleplayBlocks } from "./outputGuard";

export interface TemplateVariableContext {
  userName?: string;
  characterName?: string;
}

const META_PREFIX = /^(?:\s*)(?:(?:el\s+(?:modelo|sistema|asistente)|the\s+(?:model|system|assistant)|la\s+ia|the\s+ai|la\s+inteligencia\s+artificial|artificial\s+intelligence|system)\b|step[- ]by[- ]step\s+reasoning|chain[- ]of[- ]thought|reasoning\s+process|razonamiento\s+paso\s+a\s+paso|proceso\s+de\s+razonamiento|pensamiento\s+paso\s+a\s+paso|let(?:'|’)?s\s+think|let\s+me\s+think|voy\s+a\s+analizar)/i;
const META_LINE = /^(?:\s*)(?:(?:el\s+(?:modelo|sistema|asistente)|the\s+(?:model|system|assistant)|la\s+ia|the\s+ai|la\s+inteligencia\s+artificial|artificial\s+intelligence|system)\b|step[- ]by[- ]step\s+reasoning|chain[- ]of[- ]thought|reasoning\s+process|razonamiento\s+paso\s+a\s+paso|proceso\s+de\s+razonamiento|pensamiento\s+paso\s+a\s+paso|let(?:'|’)?s\s+think|let\s+me\s+think|voy\s+a\s+analizar|(?:thinking|reasoning|analysis|prompt|generation|assistant|developer|user)\s*:)/i;

const CONTINUITY_META = /^(?:\s*)(?:continuity\s+context|contexto\s+de\s+continuidad|current\s+(?:topic|situation|location)|earlier\s+conversation\s+summary|recent\s+meaningful\s+actions|unresolved\s+questions|pending\s+events|recent\s+speakers|tema\s+actual|situaci[oó]n\s+actual|resumen\s+de\s+la\s+conversaci[oó]n|eventos\s+pendientes)/i;

const EXTRA_META = /^(?:\s*)(?:following\s+these\s+steps|based\s+on\s+(?:the\s+)?(?:context|instructions)|i\s+will\s+(?:now\s+)?respond|here(?:'|’)?s\s+(?:the\s+)?(?:response|answer)|a\s+continuaci[oó]n\s+(?:responder[eé]|ver[aá]s))/i;

function stripMetaPrefix(value: string): string {
  const leading = value.trimStart();
  if (!META_PREFIX.test(leading) && !CONTINUITY_META.test(leading) && !EXTRA_META.test(leading)) return value;

  // Reasoning models often emit a natural-language preamble instead of
  // <think> tags. Keep only the first action or spoken line.
  const roleplayStart = leading.search(/\*{1,3}(?=\S)|["“«]/);
  if (roleplayStart >= 0) return leading.slice(roleplayStart);

  // If the preamble is on its own line, keep the response that follows it.
  const newline = leading.indexOf("\n");
  return newline >= 0 ? leading.slice(newline + 1) : "";
}

/**
 * Sanitizes model output before it reaches the conversation UI or storage.
 * Character cards and GGUF models are not always consistent about chat
 * templates, so this is deliberately defensive: it removes leaked control
 * blocks/meta commentary while keeping roleplay actions and dialogue.
 */
export function normalizeRoleplayText(value?: string | null): string {
  let cleaned = value ?? "";
  cleaned = cleaned.replace(/\r\n?/g, "\n");
  cleaned = cleaned.replace(/<think(?:ing)?\b[^>]*>[\s\S]*?<\/(?:think|thinking)>/giu, "");
  cleaned = cleaned.replace(/<analysis\b[^>]*>[\s\S]*?<\/analysis>/giu, "");
  cleaned = cleaned.replace(/<\|(?:thinking|thought|analysis)\|>[\s\S]*?<\|\/(?:thinking|thought|analysis)\|>/giu, "");
  cleaned = cleaned.replace(/<\|(?:im_start|im_end|assistant|user|system|eot_id|end_of_turn|end_of_text)\|>/giu, "");
  cleaned = cleaned.replace(/<\/?(?:response|think|thinking|analysis)>/giu, "");

  // Remove model/system preambles in Spanish and English, including variants
  // such as "El modelo está considerando cómo responder…".
  cleaned = stripMetaPrefix(cleaned);
  cleaned = cleaned
    .split("\n")
    .filter((line) => !META_LINE.test(line.trim()) && !CONTINUITY_META.test(line.trim()) && !EXTRA_META.test(line.trim()))
    .join("\n");
  cleaned = cleaned.replace(/(?:^|\n)\s*(?:por favor,? (?:ten|tenga) paciencia|please wait|generating response|generating a response)[^\n]*(?=\n|$)/giu, "\n");
  cleaned = cleaned.replace(/(?:^|\n)\s*(?:system|assistant|user|developer|thinking|reasoning|analysis|prompt|generation)\s*:\s*/giu, "\n");

  // Never expose bold Markdown/action markers from a model. Collapse any run
  // of multiple stars, including unbalanced or streamed `***`/`****` forms.
  cleaned = cleaned.replace(/\*{2,}/g, "*");
  cleaned = cleaned.replace(/\n[ \t]*[-=]{3,}[ \t]*\n/g, "\n");
  cleaned = cleaned.replace(/[ \t]+\n/g, "\n");
  cleaned = cleaned.replace(/\n{3,}/g, "\n\n");

  // Keep action and spoken dialogue legible when a model places them on one
  // line ("*se acerca* \"Hola\"").
  cleaned = cleaned.replace(/(\*[^*\n]+\*)[ \t]+(?=["“])/gu, "$1\n\n");
  cleaned = cleaned.replace(/(["”])[ \t]+(\*[^*\n]+\*)/gu, "$1\n\n$2");
  return deduplicateRoleplayBlocks(cleaned.trim());
}

/**
 * Resolves Character Card placeholders without mutating the portable card.
 * A resolver is created for each conversation so two chats can use different
 * user personas while sharing the same original card.
 */
export class TemplateVariableResolver {
  private readonly userName: string;
  private readonly characterName: string;

  constructor(context: TemplateVariableContext = {}) {
    this.userName = context.userName?.trim() || "Usuario";
    this.characterName = context.characterName?.trim() || "el personaje";
  }

  resolve(value?: string | null): string {
    if (!value) return "";
    return value
      .replace(/\{\{\s*user\s*\}\}/giu, this.userName)
      .replace(/\{\{\s*char\s*\}\}/giu, this.characterName)
      .replace(/\{\{\s*character\s*\}\}/giu, this.characterName);
  }

  resolveList(values?: Array<string | null>): string[] {
    return (values ?? []).map((value) => this.resolve(value)).filter(Boolean);
  }

  cleanGeneratedContent(value?: string | null): string {
    let cleaned = this.resolve(value).trim();
    cleaned = cleaned.replace(/^(?:system\s+is\s+thinking|(?:el\s+modelo|la\s+ia|la\s+inteligencia\s+artificial)\s+est(?:a|\u00e1)\s+(?:pensando|considerando|razonando|generando)|(?:the\s+model|the\s+ai|artificial\s+intelligence)\s+is\s+(?:thinking|considering|reasoning|generating))[\s\S]*?(?=\*{1,3}(?=\S)|["“«]|$)/iu, "");
    const orphanReasoningClose = cleaned.search(/<\/(?:think|thinking|analysis)>/iu);
    if (orphanReasoningClose >= 0 && !/<(?:think|thinking|analysis)>/iu.test(cleaned.slice(0, orphanReasoningClose))) {
      cleaned = cleaned.slice(orphanReasoningClose).replace(/^<\/(?:think|thinking|analysis)>/iu, "").trimStart();
    }
    cleaned = cleaned.replace(/^(?:system|assistant|user|developer|thinking|reasoning|analysis|prompt|generation)\s*:\s*/iu, "");
    cleaned = cleaned.replace(/^<(?:think|thinking|analysis)>[\s\S]*?<\/(?:think|thinking|analysis)>\s*/iu, "");
    cleaned = cleaned.replace(/<\/?(?:response|think|thinking|analysis)>/giu, "");
    const escapedCharacterName = this.characterName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    cleaned = cleaned.replace(new RegExp(`^\\s*${escapedCharacterName}\\s*:\\s*`, "iu"), "");
    return normalizeRoleplayText(cleaned);
  }
}

export function conversationUserName(conversation?: { personaId?: string }, fallback?: string): string {
  const personaId = conversation?.personaId?.trim();
  const personaName = personaId
    ? localStorage.getItem(`local-character.desktop.persona.${personaId}.name`)?.trim()
    : "";
  return personaName || fallback?.trim() || localStorage.getItem("local-character.desktop.displayName")?.trim() || "Usuario";
}
