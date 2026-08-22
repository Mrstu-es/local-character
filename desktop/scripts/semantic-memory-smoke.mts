import assert from "node:assert/strict";
import type { ChatMessageRecord } from "../src/types.ts";
import { extractSemanticMemories, mergeSemanticMemories, semanticMemoryPromptText } from "../src/chat/semanticMemory.ts";

const message = (id: string, role: "user" | "assistant", content: string): ChatMessageRecord => ({
  id,
  conversationId: "chat-1",
  role,
  content,
  pinned: false,
  metadataJson: JSON.stringify({ source: role === "user" ? "user" : "character" }),
  createdAt: `2026-08-22T10:00:0${id}.000Z`,
});

const extracted = extractSemanticMemories({
  characterId: "char-1",
  conversationId: "chat-1",
  characterName: "Tsuyu",
  userName: "Tadeo",
  at: "2026-08-22T12:00:00.000Z",
  messages: [
    message("1", "user", "Me llamo Tadeo. Me gusta el café."),
    message("2", "assistant", "*Tsuyu sonríe.* Me gusta el té verde. Creo que la honestidad es importante. Confío en ti."),
    message("3", "assistant", "El modelo está considerando la solicitud del usuario y su razonamiento."),
  ],
});

assert(extracted.some((item) => item.kind === "fact" && item.subject === "Tadeo"));
assert(extracted.some((item) => item.kind === "preference" && item.subject === "Tadeo"));
assert(extracted.some((item) => item.kind === "preference" && item.subject === "Tsuyu"));
assert(extracted.some((item) => item.kind === "opinion" && item.subject === "Tsuyu"));
assert(extracted.some((item) => item.kind === "relationship" && item.subject === "Tsuyu"));
assert(!extracted.some((item) => /modelo|razonamiento/iu.test(item.content)));

const changed = extractSemanticMemories({
  characterId: "char-1",
  conversationId: "chat-1",
  characterName: "Tsuyu",
  userName: "Tadeo",
  at: "2026-08-22T13:00:00.000Z",
  messages: [message("4", "assistant", "No me gusta el té verde.")],
});
const merged = mergeSemanticMemories(extracted, changed);
const tea = merged.filter((item) => item.kind === "preference" && item.subject === "Tsuyu" && /té verde/iu.test(item.content));
assert.equal(tea.length, 1);
assert.match(tea[0].content, /no me gusta/iu);
assert.match(semanticMemoryPromptText(tea[0]), /preferencia estable/iu);

const fromCurrentChat = { ...tea[0], id: "current", conversationId: "chat-2", content: "Prefiero el té verde sin azúcar.", updatedAt: "2026-08-22T14:00:00.000Z" };
const fromOlderChat = { ...tea[0], id: "older", conversationId: "chat-1", content: "No me gusta el té verde.", updatedAt: "2026-08-21T14:00:00.000Z" };
const crossConversation = mergeSemanticMemories([fromCurrentChat, fromOlderChat], []);
assert.equal(crossConversation.filter((item) => item.memoryKey === tea[0].memoryKey).length, 1);
assert.equal(crossConversation.find((item) => item.memoryKey === tea[0].memoryKey)?.conversationId, "chat-2");

console.log(`semantic memory smoke ok: ${merged.length} recuerdos conservadores`);
