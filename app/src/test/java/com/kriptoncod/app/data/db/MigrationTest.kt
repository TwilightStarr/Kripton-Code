package com.kriptoncod.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.kriptoncod.app.domain.AuthType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KriptonDatabase::class.java)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Zincir 1..VERSION arasında boşluksuz ve çakışmasız olmalı. */
    @Test fun migrationChainIsContiguous() {
        val sorted = Migrations.ALL.sortedBy { it.startVersion }
        var expected = 1
        sorted.forEach {
            assertEquals("Migration zincirinde boşluk var", expected, it.startVersion)
            assertEquals("Migration yalnız +1 adım olmalı", expected + 1, it.endVersion)
            expected = it.endVersion
        }
        assertEquals("Son migration güncel sürüme ulaşmalı", KriptonDatabase.VERSION, expected)
    }

    /** Dosya tabanlı DB kapatılıp yeniden açılınca (Migrations.ALL ile) veri korunur. */
    @Test fun dataSurvivesReopenWithMigrationsConfigured(): Unit = runBlocking {
        val name = "reopen-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, KriptonDatabase::class.java, name)
            .addMigrations(*Migrations.ALL).allowMainThreadQueries().build()

        val first = open()
        val id = first.aiAccountDao().insert(
            AiAccount(providerId = "p", displayName = "d", authType = AuthType.GOOGLE, modelId = "m", powerRank = 3),
        )
        first.close()

        val second = open()
        assertEquals("d", second.aiAccountDao().get(id)?.displayName)
        second.close()
        context.deleteDatabase(name)
    }

    /** Eski her sürümden güncele geçişi doğrular (şu an yalnız v1 olduğundan döngü boş). */
    @Test fun everyOldVersionMigratesToLatest() {
        for (from in 1 until KriptonDatabase.VERSION) {
            assumeSchema(from)
            val name = "mig-$from.db"
            helper.createDatabase(name, from).close()
            helper.runMigrationsAndValidate(name, KriptonDatabase.VERSION, true, *Migrations.ALL).close()
        }
    }

    /** Dışa aktarılan v1 şeması ile oluşturulan DB, Room'un beklediği şemayla birebir uyuşur. */
    @Test fun exportedSchemaV1MatchesEntities() {
        assumeSchema(1)
        helper.createDatabase("schema-v1.db", 1).close()
        if (KriptonDatabase.VERSION == 1) {
            helper.runMigrationsAndValidate("schema-v1.db", 1, true).close()
        }
    }

    private fun assumeSchema(version: Int) {
        val path = "${KriptonDatabase::class.java.canonicalName}/$version.json"
        val present = runCatching {
            InstrumentationRegistry.getInstrumentation().context.assets.open(path).close()
        }.isSuccess
        assumeTrue("Şema $path bulunamadı: ilk derlemeden sonra app/schemas commit edilmeli", present)
    }
}
