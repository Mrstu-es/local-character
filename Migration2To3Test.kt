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
class Migration2To3Test {
    private val dbName = "migration-2-3-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun preservesCharactersAndAddsUniqueSourceProvenance() {
        helper.createDatabase(dbName, 2).apply {
            execSQL("INSERT INTO characters VALUES ('char','Astra',NULL,'desc','','','','','','','[]','[]',1,1)")
            close()
        }

        helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3).use { database ->
            database.query("SELECT name, description FROM characters WHERE id = 'char'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Astra", cursor.getString(0))
                assertEquals("desc", cursor.getString(1))
            }
            database.execSQL(
                "INSERT INTO character_sources VALUES ('char','provider','remote','https://example.com/card',NULL,'1',NULL,'hash',NULL,2,'/card.png',NULL,'CARD_FALLBACK')",
            )
            database.query("SELECT providerId, remoteId FROM character_sources WHERE characterId = 'char'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("provider", cursor.getString(0))
                assertEquals("remote", cursor.getString(1))
            }
        }
    }
}
