package id.my.bananapixel.quakealert.di

import android.content.Context
import id.my.bananapixel.quakealert.db.Database
import id.my.bananapixel.quakealert.db.Repository
import id.my.bananapixel.quakealert.db.QuakeRepository
import id.my.bananapixel.quakealert.db.QuakeRepositoryImpl
import id.my.bananapixel.quakealert.db.ChatRepository
import id.my.bananapixel.quakealert.db.ChatRepositoryImpl
import id.my.bananapixel.quakealert.domain.FetchQuakesUseCase
import id.my.bananapixel.quakealert.domain.ClearQuakesUseCase
import id.my.bananapixel.quakealert.domain.SaveChatMessagesUseCase
import id.my.bananapixel.quakealert.domain.PruneChatMessagesUseCase
import io.mockk.mockk
import org.koin.dsl.module

/**
 * Test module for Koin DI setup in unit tests.
 * Provides mock instances of dependencies for testing UseCases and ViewModels in isolation.
 */
val testDataModule = module {
    single { mockk<Repository>() }
    single { mockk<Database>() }
    single<QuakeRepository> { mockk<QuakeRepositoryImpl>() }
    single<ChatRepository> { mockk<ChatRepositoryImpl>() }
}

/**
 * Test domain module for UseCase testing.
 * Injects mock repositories into UseCases.
 */
val testDomainModule = module {
    single { FetchQuakesUseCase(get<QuakeRepository>()) }
    single { ClearQuakesUseCase(get<QuakeRepository>()) }
    single { SaveChatMessagesUseCase(get<ChatRepository>()) }
    single { PruneChatMessagesUseCase(get<ChatRepository>()) }
}

/**
 * Combined test module for unit testing.
 * Use this in your test setup: `startKoin { modules(*testAppModule.toTypedArray()) }`
 */
val testAppModule = listOf(testDataModule, testDomainModule)
