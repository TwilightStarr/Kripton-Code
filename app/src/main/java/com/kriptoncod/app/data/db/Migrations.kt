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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
