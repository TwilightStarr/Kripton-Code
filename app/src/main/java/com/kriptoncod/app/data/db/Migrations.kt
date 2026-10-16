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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
