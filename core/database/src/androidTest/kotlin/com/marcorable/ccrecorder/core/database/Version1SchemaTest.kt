package com.marcorable.ccrecorder.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Version1SchemaTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CCRecorderDatabase::class.java,
    )

    @Test
    fun exportedVersion1SchemaCreatesExpectedTables() {
        helper.createDatabase(TEST_DATABASE, 1).use { database ->
            database.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                val names = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
                assertTrue("conversations" in names)
                assertTrue("pending_file_deletions" in names)
            }
        }
    }

    companion object {
        private const val TEST_DATABASE = "migration-test"
    }
}
