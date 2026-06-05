package com.example.di

import com.example.core.automation.notifications.NotificationServiceImpl
import com.example.core.automation.scheduler.SchedulerServiceImpl
import com.example.core.automation.sender.MessageDispatcherServiceImpl
import com.example.core.contacts.ContactSyncServiceImpl
import com.example.core.gemini.AiServiceImpl
import com.example.core.prefs.PreferencesRepositoryImpl
import com.example.domain.service.AiService
import com.example.domain.service.ContactSyncService
import com.example.domain.service.MessageDispatcherService
import com.example.domain.service.NotificationService
import com.example.domain.service.PreferencesRepository
import com.example.domain.service.SchedulerService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    @Singleton
    abstract fun bindAiService(impl: AiServiceImpl): AiService

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindContactSyncService(impl: ContactSyncServiceImpl): ContactSyncService

    @Binds
    @Singleton
    abstract fun bindMessageDispatcherService(impl: MessageDispatcherServiceImpl): MessageDispatcherService

    @Binds
    @Singleton
    abstract fun bindSchedulerService(impl: SchedulerServiceImpl): SchedulerService

    @Binds
    @Singleton
    abstract fun bindNotificationService(impl: NotificationServiceImpl): NotificationService
}
