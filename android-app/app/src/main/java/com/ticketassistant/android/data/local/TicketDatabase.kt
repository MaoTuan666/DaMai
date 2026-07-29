package com.ticketassistant.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.Instant

@Database(
    entities = [
        TicketTaskEntity::class,
        RunLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(InstantConverters::class)
abstract class TicketDatabase : RoomDatabase() {
    abstract fun ticketTaskDao(): TicketTaskDao
    abstract fun runLogDao(): RunLogDao

    companion object {
        const val NAME = "ticket-assistant.db"
    }
}

class InstantConverters {
    @TypeConverter
    fun fromEpochMillis(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter
    fun toEpochMillis(value: Instant): Long = value.toEpochMilli()
}
