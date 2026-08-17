package com.localcharacter.app.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration4To5Test {
    private val dbName = "migration-4-5-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun preservesExistingRowsAndAddsAiUsageTable() {
        helper.createDatabase(dbName, 4).apply {
            execSQL("INSERT INTO characters (id,name,description,personality,scenario,firstMessage,exampleMessages,systemPrompt,creatorNotes,tags,alternateGreetings,avatarUri,source,sourceUrl,createdAt,updatedAt) VALUES ('char','Alya','','','','','','','','[]','[]',NULL,'local',NULL,1,1)")
            close()
        }

        helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5).use { database ->
            database.query("SELECT name FROM characters WHERE id = 'char'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Alya", cursor.getString(0))
            }
            database.execSQL("INSERT INTO ai_usage VALUES ('u','openrouter','model',10,5,0.001,123,'chat','char',100,500)")
            database.query("SELECT providerId, outputTokens FROM ai_usage WHERE id = 'u'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("openrouter", cursor.getString(0))
                assertEquals(5L, cursor.getLong(1))
            }
        }
    }
}
