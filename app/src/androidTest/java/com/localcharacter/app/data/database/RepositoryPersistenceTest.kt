package com.localcharacter.app.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.localcharacter.app.data.repository.CharacterRepository
import com.localcharacter.app.data.repository.ChatRepository
import com.localcharacter.app.domain.model.Character
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryPersistenceTest {
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After fun tearDown() = database.close()

    @Test fun conversationAndGreetingRoundTrip() = runBlocking {
        val characters = CharacterRepository(database.characterDao())
        val chats = ChatRepository(database.conversationDao(), database.messageDao())
        val character = Character("c", "Luna", firstMessage = "Hola")
        characters.save(character)
        val conversation = chats.create(character)
        assertEquals("Hola", chats.messages(conversation.id).first().single().content)
    }
}
