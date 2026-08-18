import assert from "node:assert/strict";
import { buildContinuityContext } from "../src/chat/continuity.ts";

const message = (id: string, role: "user" | "assistant", content: string) => ({
  id,
  conversationId: "continuity-test",
  role,
  content,
  pinned: false,
  metadataJson: JSON.stringify({ source: role === "user" ? "user" : "character" }),
  createdAt: new Date(Date.now() + Number(id.slice(1)) * 1000).toISOString(),
});

const transcript = [
  message("m1", "user", "Perdí mis llaves.") ,
  message("m2", "assistant", "¿Revisaste tu mochila?"),
  message("m3", "user", "Sí, no están."),
  message("m4", "assistant", "Entonces busquemos en la mesa."),
  message("m5", "user", "Mi perro se llama Toby."),
  message("m6", "assistant", "Lo recordaré."),
  ...Array.from({ length: 34 }, (_, index) => message(`m${index + 7}`, index % 2 === 0 ? "user" : "assistant", `Seguimos hablando de la mochila y las llaves en la habitación, turno ${index + 7}.`)),
];

const result = buildContinuityContext({
  conversationId: "continuity-test",
  messages: transcript,
  currentMessage: message("m42", "user", "Ya miré ahí."),
  memories: ["Toby es el perro de Tadeo."],
  contextLimit: 2048,
  reserveOutput: 256,
});

assert.equal(result.context.recentMessages.at(-1)?.content, "Ya miré ahí.");
assert.ok(result.summaryToPersist, "Una conversación larga debe crear un resumen incremental");
assert.ok(result.context.relevantMemories.includes("Toby es el perro de Tadeo."));
assert.match(result.context.currentSituation, /Ya miré ahí/);

const continued = buildContinuityContext({
  conversationId: "continuity-test",
  messages: transcript,
  summary: result.summaryToPersist,
  contextLimit: 2048,
  reserveOutput: 256,
});
assert.ok(continued.context.recentMessages.length > 0);
assert.ok(!continued.context.recentMessages.some((item) => item.id === result.summaryToPersist?.summaryUntilMessageId), "El corte del resumen no debe repetirse en el historial reciente");
assert.notEqual(continued.context.recentMessages.at(-1)?.role, "user", "Continue no crea un mensaje de usuario artificial");
console.log(`continuity smoke ok: ${continued.context.recentMessageCount} recientes, ${continued.context.tokenEstimate} tokens estimados`);
