package id.my.bananapixel.quakealert.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.db.*
import id.my.bananapixel.quakealert.firebase.FirebaseMessenger
import id.my.bananapixel.quakealert.up.Distributor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import java.io.IOException

/** Load state for quake history refresh. */
sealed class QuakeLoadState {
    data object Idle : QuakeLoadState()
    data object Loading : QuakeLoadState()
    data object Success : QuakeLoadState()
    data class Error(val message: String) : QuakeLoadState()
}

class SubscriptionsViewModel(private val repository: Repository) : ViewModel() {

    // --- QUAKE LOGIC ---
    val quakes: Flow<List<QuakeData>> = repository.quakes

    private val _quakeLoadState = MutableStateFlow<QuakeLoadState>(QuakeLoadState.Idle)
    val quakeLoadState: StateFlow<QuakeLoadState> = _quakeLoadState.asStateFlow()

    fun refreshQuakes(context: Context) = viewModelScope.launch {
        _quakeLoadState.value = QuakeLoadState.Loading
        val result = repository.fetchQuakes(context)
        _quakeLoadState.value = if (result.isSuccess) {
            QuakeLoadState.Success
        } else {
            val e = result.exceptionOrNull()
            QuakeLoadState.Error(
                context.getString(
                    if (e is IOException) R.string.error_connection_message
                    else R.string.error_generic_message
                )
            )
        }
    }

    // --- CHAT LOGIC (The Missing Piece) ---
    // This resolves the 'Unresolved reference chatMessages' in ChatFragment
    val chatMessages: Flow<List<ChatMessage>> = repository.chatMessages

    fun saveChatMessages(messages: List<ChatMessage>) = viewModelScope.launch {
        repository.saveChatMessages(messages)
    }

    // --- SUBSCRIPTION LOGIC ---
    fun list(): LiveData<List<Subscription>> = repository.getSubscriptionsLiveData()

    fun listIdsWithInstantStatus(): LiveData<Set<Pair<Long, Boolean>>> =
        repository.getSubscriptionIdsWithInstantStatusLiveData()

    fun add(subscription: Subscription) = viewModelScope.launch(Dispatchers.IO) {
        repository.addSubscription(subscription)
    }

    fun remove(context: Context, subscriptionId: Long) = viewModelScope.launch(Dispatchers.IO) {
        val subscription = repository.getSubscription(subscriptionId) ?: return@launch
        if (subscription.upAppId != null && subscription.upConnectorToken != null) {
            val distributor = Distributor(context)
            distributor.sendUnregistered(subscription.upAppId, subscription.upConnectorToken)
        }
        repository.removeSubscription(subscription)

        if (subscription.icon != null) {
            val resolver = context.applicationContext.contentResolver
            try { resolver.delete(subscription.icon.toUri(), null, null) } catch (_: Exception) { }
        }

        val appBaseUrl = context.getString(R.string.app_base_url)
        if (subscription.baseUrl == appBaseUrl) {
            val messenger = FirebaseMessenger()
            messenger.unsubscribe(subscription.topic)
        }
    }

    suspend fun get(baseUrl: String, topic: String): Subscription? {
        return repository.getSubscription(baseUrl, topic)
    }
}

class SubscriptionsViewModelFactory(private val repository: Repository) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        when {
            modelClass.isAssignableFrom(SubscriptionsViewModel::class.java) -> SubscriptionsViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown viewModel class $modelClass")
        }
}