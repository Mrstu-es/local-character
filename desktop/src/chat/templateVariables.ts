export interface TemplateVariableContext {
  userName?: string;
  characterName?: string;
}

/**
 * Sanitizes model output before it reaches the conversation UI or storage.
 * Character cards and GGUF models are not always consistent about chat
 * templates, so this is deliberately defensive: it removes leaked control
 * blocks/meta commentary while keeping the roleplay actions and dialogue.
 */
export function normalizeRoleplayText(value?: string | null): string {
  let cleaned = value ?? "";
  cleaned = cleaned.replace(/\r\n?/g, "\n");
  cleaned = cleaned.replace(/<think(?:ing)?\b[^>]*>[\s\S]*?<\/(?:think|thinking)>/gi, "");
  cleaned = cleaned.replace(/<analysis\b[^>]*>[\s\S]*?<\/analysis>/gi, "");
  cleaned = cleaned.replace(/<\|(?:thinking|thought|analysis)\|>[\s\S]*?<\|\/(?:thinking|thought|analysis)\|>/gi, "");
  cleaned = cleaned.replace(/<\|(?:im_start|im_end|assistant|user|system|eot_id|end_of_turn)\|>/gi, "");
  cleaned = cleaned.replace(/<\/?(?:response|think|thinking|analysis)>/gi, "");

  // These lines are runtime/meta text, never part of a character response.
  cleaned = cleaned.replace(/(?:^|\n)\s*(?:el modelo (?:est[áa] )?(?:pensando|razonando|generando)[^\n]*|the model is (?:thinking|reasoning|generating)[^\n]*|system (?:is )?thinking[^\n]*|por favor,? (?:ten|tenga) paciencia[^\n]*)(?=\n|$)/gi, "\n");
  cleaned = cleaned.replace(/(?:^|\n)\s*(?:system|assistant|user|developer|thinking|reasoning|analysis|prompt|generation)\s*:\s*/gi, "\n");

  // Character cards commonly use bold markdown for actions. Render them as
  // single-star roleplay actions, which avoids the distracting ** markers.
  cleaned = cleaned.replace(/\*\*([^*\n]+)\*\*/g, "*$1*");
  cleaned = cleaned.replace(/\n[ \t]*[-=]{3,}[ \t]*\n/g, "\n");
  cleaned = cleaned.replace(/[ \t]+\n/g, "\n");
  cleaned = cleaned.replace(/\n{3,}/g, "\n\n");

  // Keep action and spoken dialogue legible when a model places them on one
  // line ("*se acerca* \"Hola\"").
  cleaned = cleaned.replace(/(\*[^*\n]+\*)[ \t]+(?=["“])/g, "$1\n\n");
  cleaned = cleaned.replace(/(["”])[ \t]+(\*[^*\n]+\*)/g, "$1\n\n$2");
  return cleaned.trim();
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
      .replace(/\{\{\s*user\s*\}\}/gi, this.userName)
      .replace(/\{\{\s*char\s*\}\}/gi, this.characterName)
      .replace(/\{\{\s*character\s*\}\}/gi, this.characterName);
  }

  resolveList(values?: Array<string | null>): string[] {
    return (values ?? []).map((value) => this.resolve(value)).filter(Boolean);
  }

  cleanGeneratedContent(value?: string | null): string {
    let cleaned = this.resolve(value).trim();
    cleaned = cleaned.replace(/^(?:system\s+is\s+thinking|el\s+modelo\s+est(?:a|\u00e1)\s+pensando|the\s+model\s+is\s+thinking)[\s\S]*?(?=\*|<response>|<RESPONSE>|<\/thinking>|<\/think>|$)/i, "");
    const orphanReasoningClose = cleaned.search(/<\/(?:think|thinking|analysis)>/i);
    if (orphanReasoningClose >= 0 && !/<(?:think|thinking|analysis)>/i.test(cleaned.slice(0, orphanReasoningClose))) {
      cleaned = cleaned.slice(orphanReasoningClose).replace(/^<\/(?:think|thinking|analysis)>/i, "").trimStart();
    }
    cleaned = cleaned.replace(/^(?:system\s+is\s+thinking|system|assistant|user|developer|thinking|reasoning|analysis|prompt|generation)\s*:\s*/i, "");
    cleaned = cleaned.replace(/^<think>[\s\S]*?<\/think>\s*/i, "");
    cleaned = cleaned.replace(/^<thinking>[\s\S]*?<\/thinking>\s*/i, "");
    cleaned = cleaned.replace(/^<analysis>[\s\S]*?<\/analysis>\s*/i, "");
    cleaned = cleaned.replace(/<\/?(?:response|think|thinking|analysis)>/gi, "");
    const escapedCharacterName = this.characterName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    cleaned = cleaned.replace(new RegExp(`^\\s*${escapedCharacterName}\\s*:\\s*`, "i"), "");
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
