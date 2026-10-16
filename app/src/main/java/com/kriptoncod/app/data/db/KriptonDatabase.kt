package com.kriptoncod.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AiAccount::class,
        PromptSet::class,
        PromptItem::class,
        Assignment::class,
        Job::class,
        RunRecord::class,
    ],
    version = KriptonDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KriptonDatabase : RoomDatabase() {
    abstract fun aiAccountDao(): AiAccountDao
    abstract fun promptSetDao(): PromptSetDao
    abstract fun promptItemDao(): PromptItemDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun runDao(): RunDao
    abstract fun jobDao(): JobDao

    companion object {
        const val VERSION = 3
        const val NAME = "kriptoncod.db"

        fun build(context: Context): KriptonDatabase =
            Room.databaseBuilder(context.applicationContext, KriptonDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .build()
    }
}
