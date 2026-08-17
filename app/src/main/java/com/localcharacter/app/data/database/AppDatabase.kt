package com.localcharacter.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CharacterEntity::class,
        CharacterSourceEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        ConversationSummaryEntity::class,
        CharacterRelationshipEntity::class,
        PendingEventEntity::class,
        LoreEntryEntity::class,
        ModelEntity::class,
        UserPersonaEntity::class,
        VoiceRepositoryEntity::class,
        VoiceEntity::class,
        CharacterPreferencesEntity::class,
        AiUsageEntity::class,
        GroupConversationEntity::class,
        GroupParticipantEntity::class,
        GroupMessageEntity::class,
        GroupMemoryEntity::class,
        GroupContextEntity::class,
        GroupParticipantContextEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun characterSourceDao(): CharacterSourceDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun conversationSummaryDao(): ConversationSummaryDao
    abstract fun characterRelationshipDao(): CharacterRelationshipDao
    abstract fun pendingEventDao(): PendingEventDao
    abstract fun loreDao(): LoreDao
    abstract fun modelDao(): ModelDao
    abstract fun userPersonaDao(): UserPersonaDao
    abstract fun voiceRepositoryDao(): VoiceRepositoryDao
    abstract fun voiceDao(): VoiceDao
    abstract fun characterPreferencesDao(): CharacterPreferencesDao
    abstract fun aiUsageDao(): AiUsageDao
    abstract fun groupConversationDao(): GroupConversationDao
    abstract fun groupParticipantDao(): GroupParticipantDao
    abstract fun groupMessageDao(): GroupMessageDao
    abstract fun groupMemoryDao(): GroupMemoryDao
    abstract fun groupContextDao(): GroupContextDao
    abstract fun groupParticipantContextDao(): GroupParticipantContextDao
}
