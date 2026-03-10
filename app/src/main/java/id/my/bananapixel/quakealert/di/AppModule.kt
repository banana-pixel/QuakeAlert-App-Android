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
import id.my.bananapixel.quakealert.ui.SensorsViewModel
import id.my.bananapixel.quakealert.ui.SettingsViewModel
import id.my.bananapixel.quakealert.ui.WarningViewModel
import id.my.bananapixel.quakealert.msg.ApiService
import id.my.bananapixel.quakealert.msg.NotificationDispatcher
import id.my.bananapixel.quakealert.msg.NotificationService
import id.my.bananapixel.quakealert.msg.Poller
import id.my.bananapixel.quakealert.util.QuakeNotificationProcessor
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import id.my.bananapixel.quakealert.BuildConfig
import id.my.bananapixel.quakealert.util.HttpUtil
import id.my.bananapixel.quakealert.api.QuakeAlertApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Data layer module: Database, SharedPreferences, and Repository implementations.
 */
val dataModule = module {
    single {
        androidContext().getSharedPreferences(Repository.SHARED_PREFS_ID, Context.MODE_PRIVATE)
    }
    single {
        Database.build(androidContext())
    }
    single {
        Repository(get(), get())
    }
    
    // Quake repository
    single<QuakeRepository> {
        QuakeRepositoryImpl(get<Database>().quakeHistoryDao(), get())
    }
    
    // Chat repository
    single<ChatRepository> {
        ChatRepositoryImpl(get<Database>().chatMessageDao())
    }
    
    // Networking
    single<OkHttpClient> {
        val baseClient = HttpUtil.defaultClientSync()
        baseClient.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("User-Agent", HttpUtil.USER_AGENT)
                        .build()
                )
            }
            .build()
    }

    single<QuakeAlertApi> {
        val baseUrl = BuildConfig.APP_BASE_URL.trimEnd('/') + "/"
        val contentType = "application/json".toMediaType()
        val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
        
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(get())
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(QuakeAlertApi::class.java)
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
    viewModel { QuakeViewModel(androidContext(), get<QuakeRepository>(), get<Database>(), get()) }
    viewModel { SensorsViewModel(get()) }
    viewModel { WarningViewModel(get()) }
    viewModel { SettingsViewModel(get()) }
}

/**
 * Service/Background layer module: Services, Workers, and Notification dispatchers.
 */
val serviceModule = module {
    single { ApiService(androidContext()) }
    single { NotificationDispatcher(androidContext(), get()) }
    single { Poller(get(), get()) }
    single { NotificationService(androidContext()) }
    single { QuakeNotificationProcessor(androidContext()) }
}

/**
 * Combined app module for Koin initialization.
 */
val appModule = listOf(dataModule, domainModule, uiModule, serviceModule)
