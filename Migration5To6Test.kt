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
class Migration5To6Test {
    private val dbName = "migration-5-6-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun preservesPersonaAndAddsVoiceTables() {
        helper.createDatabase(dbName, 5).apply {
            execSQL("INSERT INTO user_personas (id,name,avatarUri,description) VALUES ('p','Tadeo',NULL,'Perfil')")
            close()
        }

        helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6).use { database ->
            database.query("SELECT name, description, isDefault, createdAt FROM user_personas WHERE id='p'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Tadeo", cursor.getString(0))
                assertEquals("Perfil", cursor.getString(1))
                assertEquals(1, cursor.getInt(2))
                assertTrue(cursor.getLong(3) > 0)
            }
            database.execSQL("INSERT INTO voice_repositories VALUES ('r','Repo','https://example.com/voice-repository.json',1,NULL,NULL,NULL,1)")
            database.execSQL("INSERT INTO voices VALUES ('v','Voz','PIPER','es','/voice/model.onnx',NULL,'[]',NULL,'r','remote','1','CC0','A','https://example.com',NULL,'hash',10,1,1)")
            database.query("SELECT name FROM voices WHERE id='v'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Voz", cursor.getString(0))
            }
        }
    }
}
