import type { CharacterRecord, ChatMessageRecord, GroupRecord } from "../types";

export type RoleplayLanguage = "es" | "en" | "pt" | "fr";

const LANGUAGE_WORDS: Record<RoleplayLanguage, ReadonlySet<string>> = {
  es: new Set(["que", "qué", "como", "cómo", "donde", "dónde", "cuando", "cuándo", "quien", "quién", "porque", "pero", "para", "con", "sin", "una", "uno", "los", "las", "del", "esta", "está", "eres", "soy", "quiero", "puedes", "tienes", "hola", "gracias"]),
  en: new Set(["the", "that", "what", "where", "when", "who", "why", "because", "but", "with", "without", "this", "are", "you", "your", "want", "can", "have", "hello", "thanks"]),
  pt: new Set(["que", "como", "onde", "quando", "quem", "porque", "mas", "para", "com", "sem", "uma", "você", "voce", "quero", "pode", "tem", "olá", "ola", "obrigado"]),
  fr: new Set(["que", "quoi", "comment", "où", "ou", "quand", "qui", "pourquoi", "mais", "pour", "avec", "sans", "une", "vous", "veux", "peux", "bonjour", "merci"]),
};

function supported(value?: string | null): RoleplayLanguage | undefined {
  const code = value?.trim().toLowerCase().split(/[-_]/)[0];
  return code === "es" || code === "en" || code === "pt" || code === "fr" ? code : undefined;
}

function scores(value: string): Record<RoleplayLanguage, number> {
  const result: Record<RoleplayLanguage, number> = { es: 0, en: 0, pt: 0, fr: 0 };
  const normalized = value.toLocaleLowerCase();
  const words = normalized.match(/[\p{L}]+/gu) ?? [];
  for (const language of Object.keys(LANGUAGE_WORDS) as RoleplayLanguage[]) {
    for (const word of words) if (LANGUAGE_WORDS[language].has(word)) result[language] += 1;
  }
  if (/[¿¡ñ]/iu.test(value)) result.es += 4;
  if (/[ãõ]/iu.test(value)) result.pt += 4;
  if (/[çœ]/iu.test(value)) result.fr += 3;
  if (/\b(?:i'm|i've|don't|can't|won't|you're|it's)\b/iu.test(value)) result.en += 3;
  return result;
}

function strongestLanguage(value: string): { language?: RoleplayLanguage; confidence: number } {
  const ranked = (Object.entries(scores(value)) as Array<[RoleplayLanguage, number]>).sort((a, b) => b[1] - a[1]);
  const [first, second] = ranked;
  if (!first || first[1] < 2 || first[1] === second?.[1]) return { confidence: 0 };
  return { language: first[0], confidence: first[1] - (second?.[1] ?? 0) };
}

export interface RoleplayLanguageInput {
  configured?: string | null;
  messages?: ChatMessageRecord[];
  currentText?: string | null;
  character?: CharacterRecord;
  group?: GroupRecord;
}

/**
 * Chooses the language for one generation. The latest explicit user language
 * wins; the configured UI language is the stable fallback, and the card is
 * consulted only when neither provides a useful signal.
 */
export function inferRoleplayLanguage(input: RoleplayLanguageInput): RoleplayLanguage {
  const recentUserText = [
    ...(input.messages ?? []).filter((message) => message.role === "user").slice(-3).map((message) => message.content),
    input.currentText ?? "",
  ].filter(Boolean).join("\n");
  const recent = strongestLanguage(recentUserText);
  if (recent.language && recent.confidence >= 1) return recent.language;

  const configured = supported(input.configured);
  if (configured) return configured;

  const cardText = [input.character?.name, input.character?.description, input.character?.personality, input.character?.scenario, input.character?.greeting, input.group?.name, input.group?.description].filter(Boolean).join("\n");
  return strongestLanguage(cardText).language ?? "es";
}

export function roleplayLanguageName(language: RoleplayLanguage): string {
  return language === "en" ? "English" : language === "pt" ? "Portuguese" : language === "fr" ? "French" : "Spanish";
}

