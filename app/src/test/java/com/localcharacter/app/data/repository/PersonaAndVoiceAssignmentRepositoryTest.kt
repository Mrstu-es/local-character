package com.localcharacter.app.data.repository

import com.localcharacter.app.data.database.CharacterPreferencesDao
import com.localcharacter.app.data.database.CharacterPreferencesEntity
import com.localcharacter.app.data.database.UserPersonaDao
import com.localcharacter.app.data.database.UserPersonaEntity
import com.localcharacter.app.domain.model.CharacterPreferences
import com.localcharacter.app.domain.model.UserPersona
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaAndVoiceAssignmentRepositoryTest {
    @Test fun `user persona repository persists and changes default`() = runBlocking {
        val dao = FakePersonaDao()
        val repository = UserPersonaRepository(dao)
        val first = UserPersona("one", "Tadeo", isDefault = true)
        repository.save(first)
        repository.save(UserPersona("two", "Theo"))
        repository.setDefault("two")
        assertEquals("Theo", repository.default()?.name)
        assertEquals(2, repository.personas.first().size)
    }

    @Test fun `voice assignment is shared by id instead of duplicating voice`() = runBlocking {
        val dao = FakeCharacterPreferencesDao()
        val repository = CharacterPreferencesRepository(dao)
        repository.save(CharacterPreferences("alya", voiceId = "voice-es"))
        assertEquals("voice-es", repository.get("alya").voiceId)
        assertTrue(repository.get("frieren").voiceId == null)
    }

    private class FakePersonaDao : UserPersonaDao {
        private val rows = MutableStateFlow<List<UserPersonaEntity>>(emptyList())
        override fun observeAll(): Flow<List<UserPersonaEntity>> = rows
        override suspend fun get(id: String) = rows.value.firstOrNull { it.id == id }
        override suspend fun defaultPersona() = rows.value.firstOrNull { it.isDefault }
        override suspend fun count() = rows.value.size
        override suspend fun upsert(persona: UserPersonaEntity) {
            rows.value = rows.value.filterNot { it.id == persona.id } + persona
        }
        override suspend fun setDefault(id: String, updatedAt: Long) {
            rows.value = rows.value.map { it.copy(isDefault = it.id == id, updatedAt = if (it.id == id) updatedAt else it.updatedAt) }
        }
        override suspend fun delete(id: String) { rows.value = rows.value.filterNot { it.id == id } }
    }

    private class FakeCharacterPreferencesDao : CharacterPreferencesDao {
        private val rows = mutableMapOf<String, MutableStateFlow<CharacterPreferencesEntity?>>()
        override fun observe(characterId: String): Flow<CharacterPreferencesEntity?> =
            rows.getOrPut(characterId) { MutableStateFlow(null) }
        override suspend fun get(characterId: String) = rows[characterId]?.value
        override suspend fun upsert(preferences: CharacterPreferencesEntity) {
            rows.getOrPut(preferences.characterId) { MutableStateFlow(null) }.value = preferences
        }
    }
}
