package id.my.bananapixel.quakealert.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.my.bananapixel.quakealert.BuildConfig
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.db.*
import id.my.bananapixel.quakealert.firebase.FirebaseMessenger
import id.my.bananapixel.quakealert.up.Distributor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import androidx.core.net.toUri

/**
 * ViewModel for subscription management (ntfy part).
 * Focused single responsibility: manage subscriptions to notification topics.
 * 
 * NOTE: Quake and chat logic have been moved to dedicated ViewModels:
 * - QuakeHistoryViewModel (quake data)
 * - (ChatViewModel would go here if needed)
 */
class SubscriptionsViewModel(private val repository: Repository) : ViewModel() {

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

        val appBaseUrl = BuildConfig.APP_BASE_URL
        if (subscription.baseUrl == appBaseUrl) {
            val messenger = FirebaseMessenger()
            messenger.unsubscribe(subscription.topic)
        }
    }

    suspend fun get(baseUrl: String, topic: String): Subscription? {
        return repository.getSubscription(baseUrl, topic)
    }
}

/**
 * Factory for SubscriptionsViewModel (kept for backward compatibility).
 * New code should use Koin injection: by viewModel()
 */
class SubscriptionsViewModelFactory(private val repository: Repository) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        when {
            modelClass.isAssignableFrom(SubscriptionsViewModel::class.java) -> SubscriptionsViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown viewModel class $modelClass")
        }
}