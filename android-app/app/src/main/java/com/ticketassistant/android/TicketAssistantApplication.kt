package com.ticketassistant.android

import android.app.Application
import androidx.room.Room
import com.ticketassistant.android.data.local.TicketDatabase
import com.ticketassistant.android.data.repository.RoomTicketTaskRepository
import com.ticketassistant.android.domain.TicketTaskRepository
import com.ticketassistant.android.platform.api.PlatformAdapterRegistry
import com.ticketassistant.android.platform.damai.DamaiAndroidAdapter

class TicketAssistantApplication : Application() {
    lateinit var taskRepository: TicketTaskRepository
        private set
    lateinit var platformAdapterRegistry: PlatformAdapterRegistry
        private set

    override fun onCreate() {
        super.onCreate()

        val database = Room.databaseBuilder(
            applicationContext,
            TicketDatabase::class.java,
            TicketDatabase.NAME,
        ).build()

        taskRepository = RoomTicketTaskRepository(database)
        platformAdapterRegistry = PlatformAdapterRegistry(
            adapters = listOf(DamaiAndroidAdapter()),
        )
    }
}
