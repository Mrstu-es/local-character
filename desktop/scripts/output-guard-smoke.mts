import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { collapseConsecutiveDuplicateAssistants, deduplicateRoleplayBlocks, mergeStreamText } from "../src/chat/outputGuard.ts";
import { inferRoleplayLanguage } from "../src/chat/language.ts";

let cumulative = "";
for (const chunk of ["*Tsuyu", "*Tsuyu te mira", "*Tsuyu te mira con calma.*", "*Tsuyu te mira con calma.*"]) {
  cumulative = mergeStreamText(cumulative, chunk);
}
assert.equal(cumulative, "*Tsuyu te mira con calma.*");

let incremental = "";
for (const chunk of ["*Tsuyu", " te mira", " con calma.*", " \"No, no lo dejé allí.\""]) {
  incremental = mergeStreamText(incremental, chunk);
}
assert.equal(incremental, "*Tsuyu te mira con calma.* \"No, no lo dejé allí.\"");

const paragraph = "*Tsuyu inclina la cabeza y observa la habitación con atención.*";
assert.equal(deduplicateRoleplayBlocks(`${paragraph}\n\n${paragraph}\n\n\"Buscaré en la lavandería.\"`), `${paragraph}\n\n\"Buscaré en la lavandería.\"`);

const collapsed = collapseConsecutiveDuplicateAssistants([
  { id: "a1", role: "assistant", content: paragraph, createdAt: "2026-08-22T10:00:00.000Z", metadataJson: JSON.stringify({ generated: true, senderCharacterId: "tsuyu" }) },
  { id: "a2", role: "assistant", content: paragraph, createdAt: "2026-08-22T10:00:01.000Z", metadataJson: JSON.stringify({ generated: true, senderCharacterId: "tsuyu" }) },
  { id: "u1", role: "user", content: "¿Y ahora?" },
  { id: "a3", role: "assistant", content: paragraph, createdAt: "2026-08-22T10:00:03.000Z", metadataJson: JSON.stringify({ generated: true, senderCharacterId: "tsuyu" }) },
]);
assert.deepEqual(collapsed.duplicateIds, ["a2"]);
assert.deepEqual(collapsed.messages.map((message) => message.id), ["a1", "u1", "a3"]);

const spanish = inferRoleplayLanguage({ configured: "en", currentText: "¿Dónde crees que dejaste el pijama?" });
assert.equal(spanish, "es");
const english = inferRoleplayLanguage({ configured: "es", currentText: "Where do you think you left your pajamas?" });
assert.equal(english, "en");

const promptSource = readFileSync(new URL("../src/chat/promptBuilder.ts", import.meta.url), "utf8");
const continuitySource = readFileSync(new URL("../src/chat/continuity.ts", import.meta.url), "utf8");
const chatSource = readFileSync(new URL("../src/chat/ChatPanelModern.tsx", import.meta.url), "utf8");
assert.match(promptSource, /toda la respuesta visible en español/iu);
assert.match(promptSource, /exactly one response/iu);
assert.match(promptSource, /Never print thoughts, chain-of-thought/iu);
assert.doesNotMatch(promptSource, /RECENT TRANSCRIPT/iu);
assert.doesNotMatch(continuitySource, /LATEST EXCHANGE/iu);
assert.doesNotMatch(continuitySource, /Current situation, derived only from the transcript/iu);
assert.match(chatSource, /const protocolMessages = \[\{ role: "system" as const, content: roleplaySystemPrompt \}, \.\.\.history\]/u);

console.log("output guard smoke: ok");
