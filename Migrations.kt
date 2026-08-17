package com.localcharacter.app.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserves every v1 memory while expanding it into the intelligent-memory schema. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `memories_new` (
                `id` TEXT NOT NULL,
                `characterId` TEXT NOT NULL,
                `conversationId` TEXT,
                `userPersonaId` TEXT,
                `type` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `normalizedContent` TEXT NOT NULL,
                `importance` REAL NOT NULL,
                `confidence` REAL NOT NULL,
                `origin` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `lastAccessedAt` INTEGER NOT NULL,
                `accessCount` INTEGER NOT NULL,
                `sourceMessageId` TEXT,
                `eventAt` INTEGER,
                `expiresAt` INTEGER,
                `isPinned` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                `supersededById` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL(
            """INSERT INTO `memories_new` (
                `id`, `characterId`, `conversationId`, `userPersonaId`, `type`, `content`,
                `normalizedContent`, `importance`, `confidence`, `origin`, `createdAt`,
                `updatedAt`, `lastAccessedAt`, `accessCount`, `sourceMessageId`, `eventAt`,
                `expiresAt`, `isPinned`, `isActive`, `supersededById`
            ) SELECT
                `id`, `characterId`, `conversationId`, NULL, 'FACT', `content`, LOWER(TRIM(`content`)),
                CASE WHEN `importance` <= 1 THEN 0.5 ELSE MIN(1.0, CAST(`importance` AS REAL) / 3.0) END,
                0.8, 'USER_STATED_FACT', `createdAt`, `createdAt`, `lastAccessedAt`, 0,
                NULL, NULL, NULL, 0, 1, NULL
            FROM `memories`""".trimIndent(),
        )
        db.execSQL("DROP TABLE `memories`")
        db.execSQL("ALTER TABLE `memories_new` RENAME TO `memories`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_conversationId` ON `memories` (`conversationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_characterId` ON `memories` (`characterId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_userPersonaId` ON `memories` (`userPersonaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_type` ON `memories` (`type`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_isActive` ON `memories` (`isActive`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_characterId_conversationId_userPersonaId_isActive` ON `memories` (`characterId`, `conversationId`, `userPersonaId`, `isActive`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `conversation_summaries` (
                `id` TEXT NOT NULL,
                `conversationId` TEXT NOT NULL,
                `summary` TEXT NOT NULL,
                `fromMessageId` TEXT NOT NULL,
                `toMessageId` TEXT NOT NULL,
                `fromCreatedAt` INTEGER NOT NULL,
                `toCreatedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_summaries_conversationId` ON `conversation_summaries` (`conversationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_summaries_conversationId_toCreatedAt` ON `conversation_summaries` (`conversationId`, `toCreatedAt`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `character_relationships` (
                `id` TEXT NOT NULL,
                `characterId` TEXT NOT NULL,
                `conversationId` TEXT,
                `userPersonaId` TEXT,
                `trust` REAL NOT NULL,
                `affection` REAL NOT NULL,
                `familiarity` REAL NOT NULL,
                `tension` REAL NOT NULL,
                `relationshipSummary` TEXT NOT NULL,
                `lastInteractionAt` INTEGER NOT NULL,
                `interactionCount` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_relationships_characterId` ON `character_relationships` (`characterId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_relationships_conversationId` ON `character_relationships` (`conversationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_relationships_userPersonaId` ON `character_relationships` (`userPersonaId`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `pending_events` (
                `id` TEXT NOT NULL,
                `characterId` TEXT NOT NULL,
                `conversationId` TEXT,
                `userPersonaId` TEXT,
                `description` TEXT NOT NULL,
                `eventAt` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `sourceMessageId` TEXT,
                `sourceMemoryId` TEXT,
                `followUpAskedAt` INTEGER,
                `cooldownUntil` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`sourceMemoryId`) REFERENCES `memories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_events_characterId` ON `pending_events` (`characterId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_events_conversationId` ON `pending_events` (`conversationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_events_userPersonaId` ON `pending_events` (`userPersonaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_events_eventAt` ON `pending_events` (`eventAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_events_status` ON `pending_events` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_events_sourceMemoryId` ON `pending_events` (`sourceMemoryId`)")
    }
}

/** Adds provider provenance without modifying existing local characters or conversations. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `character_sources` (
                `characterId` TEXT NOT NULL,
                `providerId` TEXT NOT NULL,
                `remoteId` TEXT NOT NULL,
                `sourceUrl` TEXT NOT NULL,
                `author` TEXT,
                `version` TEXT,
                `sourceUpdatedAt` INTEGER,
                `contentHash` TEXT NOT NULL,
                `avatarHash` TEXT,
                `downloadedAt` INTEGER NOT NULL,
                `originalCardPath` TEXT NOT NULL,
                `localAvatarPath` TEXT,
                `avatarState` TEXT NOT NULL,
                PRIMARY KEY(`characterId`),
                FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_sources_characterId` ON `character_sources` (`characterId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_character_sources_providerId_remoteId` ON `character_sources` (`providerId`, `remoteId`)")
    }
}

/** Preserves every model while enriching GGUF metadata and per-model template selection. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `models` ADD COLUMN `tensorCount` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `models` ADD COLUMN `parameterCount` INTEGER")
        db.execSQL("ALTER TABLE `models` ADD COLUMN `tokenizer` TEXT")
        db.execSQL("ALTER TABLE `models` ADD COLUMN `embeddedChatTemplate` TEXT")
        db.execSQL("ALTER TABLE `models` ADD COLUMN `chatTemplateMode` TEXT NOT NULL DEFAULT 'AUTO'")
        db.execSQL("ALTER TABLE `models` ADD COLUMN `customChatTemplate` TEXT")
    }
}

/** Adds local-only accounting without changing characters, conversations, messages or models. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `ai_usage` (
                `id` TEXT NOT NULL,
                `providerId` TEXT NOT NULL,
                `modelId` TEXT NOT NULL,
                `inputTokens` INTEGER,
                `outputTokens` INTEGER,
                `estimatedCostUsd` REAL,
                `timestamp` INTEGER NOT NULL,
                `conversationId` TEXT,
                `characterId` TEXT,
                `timeToFirstTokenMillis` INTEGER,
                `generationDurationMillis` INTEGER,
                PRIMARY KEY(`id`)
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_usage_providerId` ON `ai_usage` (`providerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_usage_modelId` ON `ai_usage` (`modelId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_usage_timestamp` ON `ai_usage` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_usage_conversationId` ON `ai_usage` (`conversationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_usage_characterId` ON `ai_usage` (`characterId`)")
    }
}

/** Adds personas, content preferences and offline voice metadata without touching existing rows. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `characters` ADD COLUMN `recommendedVoiceId` TEXT")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `user_personas_new` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `avatarUri` TEXT,
                `description` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `isDefault` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )""".trimIndent(),
        )
        db.execSQL(
            """INSERT INTO `user_personas_new` (`id`,`name`,`avatarUri`,`description`,`createdAt`,`updatedAt`,`isDefault`)
                SELECT `id`,`name`,`avatarUri`,`description`,
                    CAST(strftime('%s','now') AS INTEGER) * 1000,
                    CAST(strftime('%s','now') AS INTEGER) * 1000,
                    CASE WHEN `id` = (SELECT `id` FROM `user_personas` ORDER BY `name` LIMIT 1) THEN 1 ELSE 0 END
                FROM `user_personas`""".trimIndent(),
        )
        db.execSQL("DROP TABLE `user_personas`")
        db.execSQL("ALTER TABLE `user_personas_new` RENAME TO `user_personas`")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `voice_repositories` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `manifestUrl` TEXT NOT NULL,
                `enabled` INTEGER NOT NULL,
                `lastSyncAt` INTEGER,
                `lastSuccessfulSyncAt` INTEGER,
                `etag` TEXT,
                `schemaVersion` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )""".trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_voice_repositories_manifestUrl` ON `voice_repositories` (`manifestUrl`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `voices` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `engine` TEXT NOT NULL,
                `language` TEXT NOT NULL,
                `localModelPath` TEXT,
                `localConfigPath` TEXT,
                `filesJson` TEXT NOT NULL,
                `sampleUrl` TEXT,
                `repositoryId` TEXT,
                `remoteId` TEXT,
                `version` TEXT NOT NULL,
                `license` TEXT NOT NULL,
                `author` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `consentMetadata` TEXT,
                `contentHash` TEXT NOT NULL,
                `sizeBytes` INTEGER NOT NULL,
                `installedAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`repositoryId`) REFERENCES `voice_repositories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_voices_repositoryId` ON `voices` (`repositoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_voices_language` ON `voices` (`language`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_voices_engine` ON `voices` (`engine`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_voices_repositoryId_remoteId` ON `voices` (`repositoryId`, `remoteId`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `character_preferences` (
                `characterId` TEXT NOT NULL,
                `contentOverride` TEXT NOT NULL,
                `voiceId` TEXT,
                `autoplayOverride` TEXT NOT NULL,
                `speed` REAL NOT NULL,
                `pitch` REAL NOT NULL,
                `volume` REAL NOT NULL,
                `readMode` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`characterId`),
                FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`voiceId`) REFERENCES `voices`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_preferences_voiceId` ON `character_preferences` (`voiceId`)")
    }
}

/** Adds isolated group conversations. Existing direct chats and their messages remain untouched. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `group_conversations` (
                `id` TEXT NOT NULL, `name` TEXT NOT NULL, `avatarPath` TEXT,
                `userPersonaId` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                `lastMessageAt` INTEGER NOT NULL, `turnMode` TEXT NOT NULL DEFAULT 'SMART',
                `maxAutoResponses` INTEGER NOT NULL DEFAULT 1, `maxBotChain` INTEGER NOT NULL DEFAULT 2,
                `isPinned` INTEGER NOT NULL DEFAULT 0, `sharedMemoryEnabled` INTEGER NOT NULL DEFAULT 1,
                `forcedProviderId` TEXT, `forcedModelId` TEXT, PRIMARY KEY(`id`)
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_conversations_updatedAt` ON `group_conversations` (`updatedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_conversations_lastMessageAt` ON `group_conversations` (`lastMessageAt`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `group_participants` (
                `groupId` TEXT NOT NULL, `characterId` TEXT NOT NULL, `position` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL, `joinedAt` INTEGER NOT NULL, `lastSpokeAt` INTEGER,
                `messageCount` INTEGER NOT NULL, PRIMARY KEY(`groupId`, `characterId`),
                FOREIGN KEY(`groupId`) REFERENCES `group_conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_participants_groupId` ON `group_participants` (`groupId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_participants_characterId` ON `group_participants` (`characterId`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `group_messages` (
                `id` TEXT NOT NULL, `groupId` TEXT NOT NULL, `role` TEXT NOT NULL,
                `content` TEXT NOT NULL, `isComplete` INTEGER NOT NULL,
                `senderCharacterId` TEXT, `userPersonaId` TEXT, `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`groupId`) REFERENCES `group_conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`senderCharacterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`userPersonaId`) REFERENCES `user_personas`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_messages_groupId` ON `group_messages` (`groupId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_messages_groupId_createdAt` ON `group_messages` (`groupId`, `createdAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_messages_senderCharacterId` ON `group_messages` (`senderCharacterId`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `group_memories` (
                `id` TEXT NOT NULL, `groupId` TEXT NOT NULL, `type` TEXT NOT NULL,
                `content` TEXT NOT NULL, `importance` REAL NOT NULL, `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`id`),
                FOREIGN KEY(`groupId`) REFERENCES `group_conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_memories_groupId` ON `group_memories` (`groupId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_memories_isActive` ON `group_memories` (`isActive`)")
    }
}

/** Adds optional group world/context data without changing existing group messages or character cards. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `group_contexts` (
                `groupId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL,
                `scenario` TEXT NOT NULL, `userRole` TEXT NOT NULL, `worldRules` TEXT NOT NULL,
                `initialSituation` TEXT NOT NULL, `openingMessage` TEXT NOT NULL, `notes` TEXT NOT NULL,
                `lorePolicy` TEXT NOT NULL, `currentLocation` TEXT NOT NULL, `currentSituation` TEXT NOT NULL,
                `stateSummary` TEXT NOT NULL, `version` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`groupId`),
                FOREIGN KEY(`groupId`) REFERENCES `group_conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_contexts_groupId` ON `group_contexts` (`groupId`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `group_participant_contexts` (
                `groupId` TEXT NOT NULL, `characterId` TEXT NOT NULL, `role` TEXT NOT NULL,
                `scenarioOverride` TEXT NOT NULL, `relationshipToUser` TEXT NOT NULL,
                `relationshipToGroup` TEXT NOT NULL, `notes` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`groupId`, `characterId`),
                FOREIGN KEY(`groupId`) REFERENCES `group_conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_participant_contexts_groupId` ON `group_participant_contexts` (`groupId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_participant_contexts_characterId` ON `group_participant_contexts` (`characterId`)")
    }
}
