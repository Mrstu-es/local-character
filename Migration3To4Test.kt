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
class Migration3To4Test {
    private val dbName = "migration-3-4-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun preservesModelsAndAddsMetadataDefaults() {
        helper.createDatabase(dbName, 3).apply {
            execSQL("INSERT INTO models VALUES ('model','Qwen','content://model',123,'qwen3','Q4_K_M',32768,1,99)")
            close()
        }

        helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4).use { database ->
            database.query("SELECT displayName, isActive, tensorCount, chatTemplateMode FROM models WHERE id = 'model'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Qwen", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(0L, cursor.getLong(2))
                assertEquals("AUTO", cursor.getString(3))
            }
        }
    }
}
