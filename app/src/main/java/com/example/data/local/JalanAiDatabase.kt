package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [RoadInspectionEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoadInspectionConverters::class)
abstract class JalanAiDatabase : RoomDatabase() {

    abstract fun roadInspectionDao(): RoadInspectionDao

    companion object {
        @Volatile
        private var instance: JalanAiDatabase? = null

        fun getInstance(context: Context): JalanAiDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JalanAiDatabase::class.java,
                    "jalanai_field_db"
                ).build().also { instance = it }
            }
        }
    }
}
