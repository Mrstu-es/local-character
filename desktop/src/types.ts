export type BackendId = "cpu" | "cuda" | "vulkan";

export interface BackendInfo {
  id: BackendId;
  name: string;
  available: boolean;
  detail: string;
}

export interface GpuInfo {
  name: string;
  vendor?: string;
  vramBytes?: number;
  driver?: string;
}

export interface HardwareSnapshot {
  os: string;
  hostname: string;
  cpuName: string;
  physicalCores: number;
  logicalThreads: number;
  ramTotalBytes: number;
  ramAvailableBytes: number;
  gpus: GpuInfo[];
  backends: BackendInfo[];
}

export interface ModelRecord {
  id: string;
  path: string;
  name: string;
  sizeBytes: number;
  exists: boolean;
  architecture?: string;
  quantization?: string;
  parameterCount?: number;
  contextLength?: number;
  chatTemplate?: string;
  backend?: string;
  createdAt: string;
  updatedAt: string;
}

export interface EngineStatus {
  loadedModelId?: string;
  loadedModelPath?: string;
  loadedModelName?: string;
  modelArchitecture?: string;
  chatTemplate?: string;
  contextLength?: number;
  executable?: string;
  backend?: string;
  running: boolean;
  runtimeState?: "STOPPED" | "STARTING" | "LOADING_MODEL" | "READY" | "GENERATING" | "STOPPING" | "ERROR";
  serverPort?: number;
  error?: string;
}

export interface EngineLog {
  timestamp: string;
  level: string;
  component: string;
  message: string;
}

export interface CharacterRecord {
  id: string;
  name: string;
  description: string;
  personality: string;
  greeting: string;
  scenario?: string;
  firstMessage?: string;
  exampleMessages?: string;
  systemPrompt?: string;
  creatorNotes?: string;
  tags?: string[];
  alternateGreetings?: string[];
  lore?: unknown[];
  avatarPath?: string;
  voiceId?: string;
  createdAt: string;
  updatedAt: string;
}

export interface GenerationStats {
  timeToFirstTokenMs?: number;
  promptTokensPerSecond?: number;
  generationTokensPerSecond?: number;
  generatedTokens: number;
}

export interface ChatMessage {
  id: string;
  role: "user" | "assistant" | "system";
  content: string;
  createdAt: number;
}

export interface ConversationRecord {
  id: string;
  characterId?: string;
  modelId?: string;
  personaId?: string;
  title: string;
  pinned: boolean;
  archived: boolean;
  kind: string;
  parentConversationId?: string;
  branchPointMessageId?: string;
  lastMessagePreview: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessageRecord {
  id: string;
  conversationId: string;
  role: "user" | "assistant" | "system";
  content: string;
  replyToId?: string;
  pinned: boolean;
  metadataJson: string;
  createdAt: string;
  editedAt?: string;
}

export interface SuspiciousMessageRecord extends ChatMessageRecord {
  reason: string;
}

export interface GroupRecord {
  id: string;
  name: string;
  description: string;
  avatarPath?: string;
  systemPrompt?: string;
  participantIds: string[];
  createdAt: string;
  updatedAt: string;
}

export interface ProviderRecord {
  id: string;
  kind: string;
  name: string;
  endpoint?: string;
  apiKey?: string;
  modelName?: string;
  availableModels?: string[];
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RepositorySourceRecord {
  id: string;
  providerId: string;
  name: string;
  url: string;
  enabled: boolean;
  status: string;
  statusMessage: string;
  lastSyncAt?: string;
  updatedAt: string;
}

export interface RemoteCharacterRecord {
  providerId: string;
  remoteId: string;
  sourceId: string;
  name: string;
  description: string;
  avatarUrl?: string;
  author?: string;
  tags: string[];
  categories: string[];
  language?: string;
  isNsfw: boolean;
  downloadCount?: number;
  updatedAt?: string;
  sourceUrl: string;
  cardUrl?: string;
  rawJson: string;
  installedCharacterId?: string;
  cachedAt: string;
}

export interface RepositoryProbe {
  url: string;
  normalizedUrl: string;
  providerId: string;
  providerName: string;
  status: string;
  message: string;
  supportsSearch: boolean;
  supportsInstall: boolean;
  isDirectCharacter: boolean;
}

export interface RepositorySyncResult {
  source: RepositorySourceRecord;
  probe: RepositoryProbe;
  items: RemoteCharacterRecord[];
}

export interface VoiceRepositoryRecord {
  id: string;
  name: string;
  endpoint: string;
  language?: string;
  enabled: boolean;
  updatedAt: string;
}

export interface VoiceModelRecord {
  id: string;
  repositoryId?: string;
  name: string;
  language?: string;
  path?: string;
  metadataJson: string;
}

export interface VoiceRepositorySyncResult {
  repository: VoiceRepositoryRecord;
  voices: VoiceModelRecord[];
}

export interface ExploreFilterOption {
  id: string;
  label: string;
  count: number;
}

export interface ExploreFilterCatalog {
  sources: ExploreFilterOption[];
  languages: ExploreFilterOption[];
  tags: ExploreFilterOption[];
  categories: ExploreFilterOption[];
}

export type View = "overview" | "chat" | "characters" | "models" | "benchmarks" | "settings" | "home" | "chats";
