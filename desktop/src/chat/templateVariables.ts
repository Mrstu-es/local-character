export interface TemplateVariableContext {
  userName?: string;
  characterName?: string;
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
    return cleaned.trim();
  }
}

export function conversationUserName(conversation?: { personaId?: string }, fallback?: string): string {
  const personaId = conversation?.personaId?.trim();
  const personaName = personaId
    ? localStorage.getItem(`local-character.desktop.persona.${personaId}.name`)?.trim()
    : "";
  return personaName || fallback?.trim() || localStorage.getItem("local-character.desktop.displayName")?.trim() || "Usuario";
}
