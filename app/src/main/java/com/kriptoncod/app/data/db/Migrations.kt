package com.kriptoncod.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Şema geçişleri. Şema değiştiğinde:
 *  1. KriptonDatabase.VERSION'u artır,
 *  2. Aşağıya Migration(n, n+1) ekle ve ALL'a koy,
 *  3. Derleme sonrası oluşan app/schemas/.../<n+1>.json dosyasını commit et,
 *  4. MigrationTest zinciri otomatik doğrular.
 *
 * Yıkıcı geri dönüş (fallbackToDestructiveMigration) KASITLI olarak kullanılmaz.
 */
object Migrations {
    /** 1 → 2: prompt_items.targetFiles (hedef dosya yolları, virgülle ayrılmış). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE prompt_items ADD COLUMN targetFiles TEXT")
        }
    }

    /** 2 → 3: düğüm grafiği alanları (kind, catalog, title, body). */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE jobs ADD COLUMN kind TEXT NOT NULL DEFAULT 'WORK'")
            db.execSQL("ALTER TABLE jobs ADD COLUMN catalog TEXT")
            db.execSQL("ALTER TABLE jobs ADD COLUMN title TEXT")
            db.execSQL("ALTER TABLE jobs ADD COLUMN body TEXT")
        }
    }

    /** 3 → 4: ai_accounts.baseUrl (proxy hesaplarının taban adresi). */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE ai_accounts ADD COLUMN baseUrl TEXT")
        }
    }

    /** 4 → 5: usage_events (hesap başına istek sonuçları; kullanım/kota panosu). */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `usage_events` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`accountId` INTEGER NOT NULL, " +
                    "`jobId` INTEGER, " +
                    "`at` INTEGER NOT NULL, " +
                    "`outcome` TEXT NOT NULL, " +
                    "`durationMs` INTEGER NOT NULL, " +
                    "`waitMs` INTEGER, " +
                    "FOREIGN KEY(`accountId`) REFERENCES `ai_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_usage_events_accountId` ON `usage_events` (`accountId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_usage_events_at` ON `usage_events` (`at`)")
        }
    }

    /** 5 → 6: ai_accounts.dailyLimit (günlük istek sınırı) ve isBackup (yedek hesap). */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE ai_accounts ADD COLUMN dailyLimit INTEGER")
            db.execSQL("ALTER TABLE ai_accounts ADD COLUMN isBackup INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** 6 → 7: usage_events token alanları; ai_accounts isteğe bağlı fiyat alanları (maliyet paneli). */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE usage_events ADD COLUMN promptTokens INTEGER")
            db.execSQL("ALTER TABLE usage_events ADD COLUMN completionTokens INTEGER")
            db.execSQL("ALTER TABLE usage_events ADD COLUMN tokensEstimated INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE ai_accounts ADD COLUMN priceInPerM REAL")
            db.execSQL("ALTER TABLE ai_accounts ADD COLUMN priceOutPerM REAL")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
}
