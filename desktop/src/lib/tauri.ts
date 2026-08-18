import { convertFileSrc, invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import { open } from "@tauri-apps/plugin-dialog";
import type { CharacterRecord, ChatMessageRecord, ConversationRecord, ConversationSummaryRecord, EngineLog, EngineStatus, ExploreFilterCatalog, GenerationStats, GroupRecord, HardwareSnapshot, ModelRecord, ProviderRecord, RemoteCharacterRecord, RepositoryProbe, RepositorySourceRecord, RepositorySyncResult, SuspiciousMessageRecord, VoiceModelRecord, VoiceRepositoryRecord, VoiceRepositorySyncResult } from "../types";

export const isTauriRuntime = () => Boolean((window as Window & { __TAURI_INTERNALS__?: unknown }).__TAURI_INTERNALS__);

export async function getHardwareSnapshot(): Promise<HardwareSnapshot> {
  return invoke<HardwareSnapshot>("get_hardware_snapshot");
}

export async function listModels(): Promise<ModelRecord[]> {
  return invoke<ModelRecord[]>("list_models");
}

export async function chooseGguf(): Promise<string | null> {
  const selected = await open({
    multiple: false,
    directory: false,
    filters: [{ name: "Modelos GGUF", extensions: ["gguf"] }],
  });
  return typeof selected === "string" ? selected : null;
}

export async function chooseCharacterImage(): Promise<string | null> {
  const selected = await open({
    multiple: false,
    directory: false,
    filters: [{ name: "Imágenes", extensions: ["png", "jpg", "jpeg", "webp", "gif"] }],
  });
  return typeof selected === "string" ? selected : null;
}

export async function chooseCharacterCard(): Promise<string | null> {
  const selected = await open({
    multiple: false,
    directory: false,
    filters: [{ name: "Character Card", extensions: ["json", "png"] }],
  });
  return typeof selected === "string" ? selected : null;
}

export function localFileUrl(path?: string): string | undefined {
  if (!path) return undefined;
  return isTauriRuntime() ? convertFileSrc(path) : path;
}

export async function readAvatarData(path: string): Promise<string> {
  return invoke<string>("read_avatar_data", { path });
}

export async function addModel(path: string): Promise<ModelRecord> {
  return invoke<ModelRecord>("add_model", { path });
}

export async function loadModel(id: string, gpuLayers?: number): Promise<EngineStatus> {
  return invoke<EngineStatus>("load_model", { id, gpuLayers });
}

export async function unloadModel(): Promise<void> {
  await invoke("unload_model");
}

export async function getEngineStatus(): Promise<EngineStatus> {
  return invoke<EngineStatus>("get_engine_status");
}

export async function listenToRuntimeState(onStatus: (status: EngineStatus) => void): Promise<UnlistenFn> {
  return listen("llm://runtime-state", (event) => onStatus(event.payload as EngineStatus));
}

export async function removeModel(id: string): Promise<void> {
  await invoke("remove_model", { id });
}

export async function listCharacters(): Promise<CharacterRecord[]> {
  return invoke<CharacterRecord[]>("list_characters");
}

export async function saveCharacter(character: CharacterRecord): Promise<CharacterRecord> {
  return invoke<CharacterRecord>("save_character", { character });
}

export async function deleteCharacter(id: string): Promise<void> {
  await invoke("delete_character", { id });
}

export async function importCharacterCard(path: string): Promise<CharacterRecord> {
  return invoke<CharacterRecord>("import_character_card", { path });
}

export async function importCharacterRepository(path: string): Promise<CharacterRecord[]> {
  return invoke<CharacterRecord[]>("import_character_repository", { path });
}

export async function importCharacterRepositoryUrl(url: string): Promise<CharacterRecord[]> {
  return invoke<CharacterRecord[]>("import_character_repository_url", { url });
}

export async function probeCharacterRepositoryUrl(url: string): Promise<RepositoryProbe> {
  return invoke<RepositoryProbe>("probe_character_repository_url", { url });
}

export async function listCharacterRepositories(): Promise<RepositorySourceRecord[]> {
  return invoke<RepositorySourceRecord[]>("list_character_repositories");
}

export async function listRemoteCharacters(): Promise<RemoteCharacterRecord[]> {
  return invoke<RemoteCharacterRecord[]>("list_remote_characters");
}

export async function listVoiceRepositories(): Promise<VoiceRepositoryRecord[]> {
  return invoke<VoiceRepositoryRecord[]>("list_voice_repositories");
}

export async function listVoiceModels(): Promise<VoiceModelRecord[]> {
  return invoke<VoiceModelRecord[]>("list_voice_models");
}

export async function syncVoiceRepository(url: string, name?: string): Promise<VoiceRepositorySyncResult> {
  return invoke<VoiceRepositorySyncResult>("sync_voice_repository", { url, name });
}

export async function deleteVoiceRepository(id: string): Promise<void> {
  await invoke("delete_voice_repository", { id });
}

export async function getExploreFilterCatalog(): Promise<ExploreFilterCatalog> {
  return invoke<ExploreFilterCatalog>("get_explore_filter_catalog");
}

export async function syncCharacterRepository(input: { url: string; sourceId?: string; name?: string; query?: string; language?: string; tags?: string[]; safeOnly?: boolean }): Promise<RepositorySyncResult> {
  return invoke<RepositorySyncResult>("sync_character_repository", input);
}

export async function deleteCharacterRepository(id: string): Promise<void> {
  return invoke("delete_character_repository", { id });
}

export async function setCharacterRepositoryEnabled(id: string, enabled: boolean): Promise<void> {
  return invoke("set_character_repository_enabled", { id, enabled });
}

export async function installRemoteCharacter(providerId: string, remoteId: string): Promise<CharacterRecord> {
  return invoke<CharacterRecord>("install_remote_character", { providerId, remoteId });
}

export async function listGroups(): Promise<GroupRecord[]> { return invoke<GroupRecord[]>("list_groups"); }
export async function saveGroup(group: GroupRecord): Promise<GroupRecord> { return invoke<GroupRecord>("save_group", { group }); }
export async function deleteGroup(id: string): Promise<void> { await invoke("delete_group", { id }); }
export async function listProviders(): Promise<ProviderRecord[]> { return invoke<ProviderRecord[]>("list_providers"); }
export async function saveProvider(provider: ProviderRecord): Promise<ProviderRecord> { return invoke<ProviderRecord>("save_provider", { provider }); }
export async function deleteProvider(id: string): Promise<void> { await invoke("delete_provider", { id }); }
export async function discoverProviderModels(provider: ProviderRecord): Promise<string[]> { return invoke<string[]>("discover_provider_models", { provider }); }

export async function stopGeneration(): Promise<void> {
  await invoke("stop_generation");
}

export async function getEngineLogs(): Promise<EngineLog[]> {
  return invoke<EngineLog[]>("get_engine_logs");
}

export async function clearEngineLogs(): Promise<void> {
  await invoke("clear_engine_logs");
}

export async function listConversations(): Promise<ConversationRecord[]> {
  return invoke<ConversationRecord[]>("list_conversations");
}

export async function saveConversation(conversation: ConversationRecord): Promise<ConversationRecord> {
  return invoke<ConversationRecord>("save_conversation", { conversation });
}

export async function deleteConversation(id: string): Promise<void> {
  await invoke("delete_conversation", { id });
}

export async function listMessages(conversationId: string): Promise<ChatMessageRecord[]> {
  return invoke<ChatMessageRecord[]>("list_messages", { conversationId });
}

export async function getConversationSummary(conversationId: string): Promise<ConversationSummaryRecord | null> {
  return invoke<ConversationSummaryRecord | null>("get_conversation_summary", { conversationId });
}

export async function saveConversationSummary(summary: ConversationSummaryRecord): Promise<ConversationSummaryRecord> {
  return invoke<ConversationSummaryRecord>("save_conversation_summary", { summary });
}

export async function saveMessage(message: ChatMessageRecord): Promise<ChatMessageRecord> {
  return invoke<ChatMessageRecord>("save_message", { message });
}

export async function deleteMessage(id: string): Promise<void> {
  await invoke("delete_message", { id });
}

export async function findSuspiciousMessages(): Promise<SuspiciousMessageRecord[]> {
  return invoke<SuspiciousMessageRecord[]>("find_suspicious_messages");
}

export async function deleteSuspiciousMessages(ids: string[]): Promise<number> {
  return invoke<number>("delete_suspicious_messages", { ids });
}

export async function branchFromMessage(conversationId: string, messageId: string): Promise<ConversationRecord> {
  return invoke<ConversationRecord>("branch_from_message", { conversationId, messageId });
}

export async function rewindToMessage(conversationId: string, messageId: string): Promise<void> {
  await invoke("rewind_to_message", { conversationId, messageId });
}

export async function sendChatMessage(request: {
  prompt: string;
  messages?: Array<{ role: "system" | "user" | "assistant"; content: string }>;
  characterName?: string;
  userName?: string;
  generationId?: string;
  conversationId?: string;
  messageId?: string;
  maxOutput?: number;
  context?: number;
  gpuLayers?: number;
}): Promise<string> {
  return invoke<string>("send_chat_message", { request });
}

export async function runBenchmark(modelId: string, context: number, gpuLayers: number): Promise<GenerationStats> {
  return invoke<GenerationStats>("run_benchmark", { modelId, context, gpuLayers });
}

export async function listenToGeneration(
  onDelta: (event: { generationId: string; text: string }) => void,
  onComplete: (event: { generationId: string; conversationId?: string; messageId?: string; finishReason?: string; stats: unknown }) => void,
  onError: (event: { generationId?: string; conversationId?: string; messageId?: string; error?: string } | unknown) => void,
  onCancelled?: (event: { generationId: string; conversationId?: string; messageId?: string; finishReason?: string }) => void,
): Promise<UnlistenFn[]> {
  return Promise.all([
    listen("llm://delta", (event) => onDelta(event.payload as { generationId: string; text: string })),
    listen("llm://complete", (event) => onComplete(event.payload as { generationId: string; conversationId?: string; messageId?: string; finishReason?: string; stats: unknown })),
    listen("llm://error", (event) => onError(event.payload as { generationId?: string; conversationId?: string; messageId?: string; error?: string })),
    listen("llm://cancelled", (event) => onCancelled?.(event.payload as { generationId: string; conversationId?: string; messageId?: string; finishReason?: string })),
  ]);
}
