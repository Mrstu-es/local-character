package com.localcharacter.app.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration1To2Test {
    private val dbName = "migration-1-2-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun preservesExistingChatsAndMemories() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO characters VALUES ('char','Astra',NULL,'','','','','','','','[]','[]',1,1)")
            execSQL("INSERT INTO conversations VALUES ('chat','char','Chat',NULL,'',0,1,1)")
            execSQL("INSERT INTO messages VALUES ('msg','chat','USER','Vivo en Santa Cruz',1,2)")
            execSQL("INSERT INTO memories VALUES ('memory','chat','char','El usuario vive en Santa Cruz',2,2,2)")
            close()
        }

        helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2).use { database ->
            database.query("SELECT content, type, isActive, confidence FROM memories WHERE id = 'memory'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("El usuario vive en Santa Cruz", cursor.getString(0))
                assertEquals("FACT", cursor.getString(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals(0.8f, cursor.getFloat(3), 0.001f)
            }
            database.query("SELECT content FROM messages WHERE id = 'msg'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Vivo en Santa Cruz", cursor.getString(0))
            }
        }
    }
}
