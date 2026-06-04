package com.example.di

import android.content.Context
import com.example.core.db.AppDatabase
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.prefs.SecurePrefs
import com.example.core.gemini.GeminiClient
import com.example.core.auth.AuthManager
import com.google.firebase.vertexai.FirebaseVertexAI
import com.google.firebase.vertexai.GenerativeModel
import com.example.data.repository.ContactRepositoryImpl
import com.example.data.repository.EventRepositoryImpl
import com.example.data.repository.MessageRepositoryImpl
import com.example.data.repository.StyleProfileRepositoryImpl
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.repository.MemoryNoteRepository
import com.example.domain.repository.GiftHistoryRepository
import com.example.data.repository.MemoryNoteRepositoryImpl
import com.example.data.repository.GiftHistoryRepositoryImpl
import com.example.core.db.dao.MemoryNoteDao
import com.example.core.db.dao.GiftHistoryDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModuleBinds {
    @Binds
    @Singleton
    abstract fun bindContactRepository(impl: ContactRepositoryImpl): ContactRepository

    @Binds
    @Singleton
    abstract fun bindEventRepository(impl: EventRepositoryImpl): EventRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    @Binds
    @Singleton
    abstract fun bindStyleProfileRepository(impl: StyleProfileRepositoryImpl): StyleProfileRepository

    @Binds
    @Singleton
    abstract fun bindMemoryNoteRepository(impl: MemoryNoteRepositoryImpl): MemoryNoteRepository

    @Binds
    @Singleton
    abstract fun bindGiftHistoryRepository(impl: GiftHistoryRepositoryImpl): GiftHistoryRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideContactDao(database: AppDatabase): ContactDao = database.contactDao()

    @Provides
    fun provideEventDao(database: AppDatabase): EventDao = database.eventDao()

    @Provides
    fun providePendingMessageDao(database: AppDatabase): PendingMessageDao = database.pendingMessageDao()

    @Provides
    fun provideSentMessageDao(database: AppDatabase): com.example.core.db.dao.SentMessageDao = database.sentMessageDao()

    @Provides
    fun provideStyleProfileDao(database: AppDatabase): com.example.core.db.dao.StyleProfileDao = database.styleProfileDao()

    @Provides
    fun provideMemoryNoteDao(database: AppDatabase): MemoryNoteDao = database.memoryNoteDao()

    @Provides
    fun provideGiftHistoryDao(database: AppDatabase): GiftHistoryDao = database.giftHistoryDao()

    @Provides
    @Singleton
    fun provideSecurePrefs(@ApplicationContext context: Context): SecurePrefs {
        return SecurePrefs(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthManager(): AuthManager {
        return AuthManager()
    }

    @Provides
    @Singleton
    fun provideGenerativeModel(): GenerativeModel {
        return com.google.firebase.vertexai.FirebaseVertexAI.getInstance(
            com.google.firebase.FirebaseApp.getInstance(),
            "us-central1"
        ).generativeModel("gemini-1.5-flash")
    }

    @Provides
    @Singleton
    fun provideGeminiClient(model: GenerativeModel, securePrefs: SecurePrefs): GeminiClient {
        return GeminiClient(model, securePrefs)
    }
}
