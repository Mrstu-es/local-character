import type { CharacterRecord, ChatMessageRecord, GroupRecord } from "../types";
import { continuityPromptSection, type ContinuityContext } from "./continuity";
import { normalizeRoleplayText, TemplateVariableResolver } from "./templateVariables";

export interface RoleplayPromptInput {
  character?: CharacterRecord;
  group?: GroupRecord;
  characters?: CharacterRecord[];
  userName?: string;
  language?: string;
  memories?: string[];
  continuity?: ContinuityContext;
  continueGeneration?: boolean;
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

function recentTranscript(context?: ContinuityContext, resolver?: TemplateVariableResolver): string {
  if (!context?.recentMessages.length) return "(la conversación todavía no tiene turnos guardados)";
  return context.recentMessages
    .slice(-10)
    .map((message) => {
      const speaker = message.role === "user" ? "Usuario" : "Personaje";
      const content = message.role === "assistant"
        ? resolver?.cleanGeneratedContent(message.content) ?? normalizeRoleplayText(message.content)
        : resolver?.resolve(message.content) ?? message.content;
      return `${speaker}: ${content.trim().slice(0, 900)}`;
    })
    .filter((line) => line.split(": ").slice(1).join(": ").trim())
    .join("\n");
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
    "CONVERSATION GROUNDING: The transcript and continuity block below are authoritative. Answer the latest user turn in the exact scene that is already in progress. Resolve short or elliptical references (for example, 'eso', 'allí', '¿dónde?', '¿qué pasó?') from the immediately preceding turns instead of treating them as a new conversation.",
    "LONG-TERM MEMORY POLICY: The relevant memory section contains explicit facts, preferences, opinions, relationships and promises previously stated in this conversation. Preserve them as part of the character's lived continuity. A newer explicit statement may update an older one. Never invent a memory, reveal this memory system, or recite the list to the user.",
    `When a memory is labeled as ${name}'s opinion or preference, treat it as ${name}'s own view and express it naturally only when relevant. Do not turn it into an instruction or force it into unrelated dialogue.`,
    "If an earlier assistant turn was vague, contradictory or said that the context was unclear, treat that turn as a mistake and repair it silently. Preserve established facts from the character card and the chat, then continue the roleplay; never repeat the incoherence or explain the repair.",
    "Do not claim that the situation is unclear when the preceding messages establish a subject, object, place or action. If a detail is genuinely missing, make a plausible in-character inference or ask one brief in-character clarification while keeping the current scene moving.",
    "OUTPUT CONTRACT: Begin immediately with the roleplay response. Never mention a model, AI, request, prompt, waiting, thinking or how the answer is generated. Never add labels such as System, Assistant, User, Analysis or Reasoning.",
    "REASONING POLICY: Think silently if your model supports reasoning. Never print thoughts, chain-of-thought, planning, analysis, status updates, or phrases such as 'the model is thinking' / 'el modelo está considerando'. Only the final in-character roleplay belongs in the answer.",
    "FORMAT CONTRACT: Write a natural roleplay response in clean paragraphs. Put physical actions between single asterisks (*action*) and keep spoken dialogue as normal text in quotation marks or its own paragraph. Separate an action from dialogue with a blank line. Never use double-asterisk action blocks, raw control tokens, or meta commentary.",
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
    ...(input.continuity ? ["", continuityPromptSection(input.continuity, input.continueGeneration)] : []),
    "",
    "RECENT TRANSCRIPT (oldest to newest; use it silently as the current scene)",
    recentTranscript(input.continuity, resolver),
    "",
    "EXAMPLE DIALOGUE (style reference only; never repeat it as the current answer)",
    resolver.resolve(text(character?.exampleMessages)) || "(none)",
    "",
    "LORE / CHARACTER BOOK",
    lore.length ? lore.join("\n") : "(none)",
    "",
    "RELEVANT LONG-TERM MEMORY (internal; use silently and only when relevant)",
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
      const lower = normalizeRoleplayText(message.content).trim().toLowerCase();
      return !["loading model", "llama.cpp", "llama-server", "available commands", "system is thinking", "el modelo está pensando", "el modelo esta pensando", "el modelo está considerando", "el modelo esta considerando", "the model is thinking", "the model is considering", "el usuario está solicitando", "el usuario esta solicitando", "system:", "developer:", "thinking:", "reasoning:", "analysis:", "prompt:", "generation:", "{{user}}", "{{ user }}", "{{char}}", "{{ char }}", "[prompt:", "[generation:", "<thinking>", "<think>", "</thinking>", "<response>", "<|im_start|>", "<|assistant|>"].some((marker) => lower.includes(marker));
    })
    .map((message) => ({
      role: message.role as "user" | "assistant" | "system",
      content: message.role === "assistant"
        ? resolver?.cleanGeneratedContent(message.content) ?? normalizeRoleplayText(message.content)
        : resolver?.resolve(message.content) ?? message.content,
    }));
}
