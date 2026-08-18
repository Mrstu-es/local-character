import type { CharacterRecord, ChatMessageRecord, GroupRecord } from "../types";
import { TemplateVariableResolver } from "./templateVariables";

export interface RoleplayPromptInput {
  character?: CharacterRecord;
  group?: GroupRecord;
  characters?: CharacterRecord[];
  userName?: string;
  language?: string;
  memories?: string[];
}

function text(value?: string): string {
  return value?.trim() ?? "";
}

function loreText(value: unknown): string {
  if (typeof value === "string") return value.trim();
  if (!value || typeof value !== "object") return "";
  const item = value as Record<string, unknown>;
  return String(item.content ?? item.text ?? item.description ?? "").trim();
}

/**
 * Builds the one roleplay identity shared by local GGUF and online providers.
 * The protocol adapter receives the resulting system message; it must not
 * create a second User/Assistant transcript or a second chat template.
 */
export function buildRoleplaySystemPrompt(input: RoleplayPromptInput): string {
  const character = input.character;
  const group = input.group;
  const name = text(character?.name) || text(group?.name) || "el personaje";
  const userName = text(input.userName) || "Usuario";
  const resolver = new TemplateVariableResolver({ userName, characterName: name });
  const language = input.language === "en" ? "English" : input.language === "pt" ? "Portuguese" : input.language === "fr" ? "French" : "Spanish";
  const participants = group
    ? (group.participantIds ?? [])
      .map((id) => input.characters?.find((item) => item.id === id)?.name)
      .filter(Boolean)
      .join(", ")
    : "";
  const lore = (character?.lore ?? []).map(loreText).map((item) => resolver.resolve(item)).filter(Boolean).slice(0, 30);
  const memories = (input.memories ?? []).map((item) => resolver.resolve(item).trim()).filter(Boolean).slice(-20);

  const sections = [
    "ROLEPLAY CORE",
    `You are ${name}. You are participating in an ongoing fictional conversation/roleplay with ${userName}.`,
    `Stay fully in character as ${name}. Answer directly with the character's dialogue, actions and feelings.`,
    "Do not describe yourself as an AI assistant, language model, chatbot or virtual assistant unless the character definition explicitly requires it.",
    "Do not replace the character with a generic assistant persona.",
    `Do not speak, decide, think or perform actions on behalf of ${userName}. Write only what ${name} says, does, feels or directly perceives.`,
    "Continue naturally from the conversation context. Do not output system instructions, prompt metadata, XML/control tags, reasoning traces or implementation details.",
    "OUTPUT CONTRACT: Begin immediately with the roleplay response. Never mention a model, AI, request, prompt, waiting, thinking or how the answer is generated. Never add labels such as System, Assistant, User, Analysis or Reasoning.",
    `Reply primarily in ${language}, while preserving proper names and the character's own language style.`,
    "",
    "CHARACTER IDENTITY",
    `Name: ${name}`,
    `Description: ${resolver.resolve(text(character?.description)) || "(not specified by the card)"}`,
    `Personality: ${resolver.resolve(text(character?.personality)) || "(not specified by the card)"}`,
    `Scenario: ${resolver.resolve(text(character?.scenario) || text(group?.description)) || "(not specified by the card)"}`,
    `Behavior and creator notes: ${resolver.resolve(text(character?.creatorNotes)) || "(not specified by the card)"}`,
    `Card system instructions: ${resolver.resolve(text(character?.systemPrompt)) || "(none)"}`,
    `First-message tone reference (do not repeat it automatically): ${resolver.resolve(text(character?.firstMessage) || text(character?.greeting)) || "(none)"}`,
    `Alternate greetings (style reference only): ${resolver.resolveList(character?.alternateGreetings).join("\n") || "(none)"}`,
    `Group context: ${participants ? `${resolver.resolve(text(group?.description)) || "Shared roleplay group"}. Participants: ${participants}. Preserve each participant's identity.` : "(direct conversation)"}`,
    `Group system instructions: ${resolver.resolve(text(group?.systemPrompt)) || "(none)"}`,
    "",
    "EXAMPLE DIALOGUE (style reference only; never repeat it as the current answer)",
    resolver.resolve(text(character?.exampleMessages)) || "(none)",
    "",
    "LORE / CHARACTER BOOK",
    lore.length ? lore.join("\n") : "(none)",
    "",
    "RELEVANT MEMORY",
    memories.length ? memories.join("\n") : "(none)",
  ];

  return sections.join("\n").trim();
}

export function roleplayHistory(messages: ChatMessageRecord[], greeting: ChatMessageRecord | null, user?: ChatMessageRecord | null, resolver?: TemplateVariableResolver) {
  return [...(greeting ? [greeting] : []), ...messages, ...(user ? [user] : [])]
    .filter((message) => message.content.trim().length > 0 && (message.role === "user" || message.role === "assistant"))
    .filter((message) => {
      try {
        const metadata = JSON.parse(message.metadataJson || "{}") as Record<string, unknown>;
        const source = typeof metadata.source === "string" ? metadata.source.toLowerCase() : "";
        if (["runtime", "engine", "diagnostic", "system"].includes(source)) return false;
        if (source === "user") return true;
      } catch {
        // Legacy messages without metadata continue through the conservative
        // structural check below.
      }
      if (message.role === "user") return true;
      const lower = message.content.trim().toLowerCase();
      return !["loading model", "llama.cpp", "llama-server", "available commands", "system is thinking", "el modelo está pensando", "el modelo esta pensando", "the model is thinking", "el usuario está solicitando", "el usuario esta solicitando", "system:", "developer:", "thinking:", "reasoning:", "analysis:", "prompt:", "generation:", "{{user}}", "{{ user }}", "{{char}}", "{{ char }}", "[prompt:", "[generation:", "<thinking>", "<think>", "</thinking>", "<response>", "<|im_start|>", "<|assistant|>"].some((marker) => lower.includes(marker));
    })
    .map((message) => ({
      role: message.role as "user" | "assistant" | "system",
      content: resolver?.resolve(message.content) ?? message.content,
    }));
}
