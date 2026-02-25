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
import id.my.bananapixel.quakealert.ui.DetailViewModel
import id.my.bananapixel.quakealert.ui.QuakeViewModel
import id.my.bananapixel.quakealert.ui.QuakeHistoryViewModel
import id.my.bananapixel.quakealert.ui.ChatViewModel
import id.my.bananapixel.quakealert.ui.SubscriptionsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Data layer module: Database, SharedPreferences, and Repository implementations.
 */
val dataModule = module {
    single {
        androidContext().getSharedPreferences(Repository.SHARED_PREFS_ID, Context.MODE_PRIVATE)
    }
    single {
        val db = Database.build(androidContext())
        Database.hiltInstance = db
        db
    }
    single {
        val repo = Repository(get(), get())
        Repository.hiltInstance = repo
        repo
    }
    
    // Quake repository
    single<QuakeRepository> {
        QuakeRepositoryImpl(get<Database>().quakeHistoryDao())
    }
    
    // Chat repository
    single<ChatRepository> {
        ChatRepositoryImpl(get<Database>().chatMessageDao())
    }
}

/**
 * Domain layer module: UseCases with business logic.
 */
val domainModule = module {
    single {
        FetchQuakesUseCase(get<QuakeRepository>())
    }
    single {
        ClearQuakesUseCase(get<QuakeRepository>())
    }
    single {
        SaveChatMessagesUseCase(get<ChatRepository>())
    }
    single {
        PruneChatMessagesUseCase(get<ChatRepository>())
    }
}

/**
 * UI layer module: ViewModels with modern lambda syntax.
 */
val uiModule = module {
    viewModel { QuakeHistoryViewModel(get<QuakeRepository>()) }
    viewModel { ChatViewModel(get<ChatRepository>()) }
    viewModel { DetailViewModel(get<Repository>()) }
    viewModel { SubscriptionsViewModel(get<Repository>()) }
    viewModel { QuakeViewModel(androidContext(), get<QuakeRepository>(), get<Database>()) }
}

/**
 * Combined app module for Koin initialization.
 */
val appModule = listOf(dataModule, domainModule, uiModule)
