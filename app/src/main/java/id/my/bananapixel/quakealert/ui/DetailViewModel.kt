package id.my.bananapixel.quakealert.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import id.my.bananapixel.quakealert.db.Notification
import id.my.bananapixel.quakealert.db.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DetailViewModel(private val repository: Repository) : ViewModel() {
    private val searchQuery = MutableLiveData("")

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun hasSearchQuery(): Boolean = !searchQuery.value.isNullOrBlank()

    fun list(subscriptionId: Long): LiveData<List<Notification>> {
        return repository.getNotificationsLiveData(subscriptionId)
    }

    fun listFiltered(subscriptionId: Long): LiveData<List<Notification>> {
        return searchQuery.switchMap { query ->
            if (query.isNullOrBlank()) {
                repository.getNotificationsLiveData(subscriptionId)
            } else {
                repository.getNotificationsFilteredLiveData(subscriptionId, query)
            }
        }
    }

    fun markAsDeleted(notificationId: String) = viewModelScope.launch(Dispatchers.IO) {
        repository.markAsDeleted(notificationId)
    }
}

class DetailViewModelFactory(private val repository: Repository) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        when {
            modelClass.isAssignableFrom(DetailViewModel::class.java) -> DetailViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown viewModel class $modelClass")
        }
}
