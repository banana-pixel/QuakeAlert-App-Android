package io.heckel.ntfy.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.heckel.ntfy.R
import io.heckel.ntfy.db.*
import io.heckel.ntfy.firebase.FirebaseMessenger
import io.heckel.ntfy.up.Distributor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import kotlinx.coroutines.flow.Flow

class SubscriptionsViewModel(private val repository: Repository) : ViewModel() {

    // --- QUAKE LOGIC ---
    val quakes: Flow<List<QuakeData>> = repository.quakes

    fun refreshQuakes(context: Context) = viewModelScope.launch {
        repository.fetchQuakes(context)
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

class SubscriptionsViewModelFactory(private val repository: Repository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        with(modelClass){
            when {
                isAssignableFrom(SubscriptionsViewModel::class.java) -> SubscriptionsViewModel(repository) as T
                else -> throw IllegalArgumentException("Unknown viewModel class $modelClass")
            }
        }
}