
package io.heckel.ntfy.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import io.heckel.ntfy.db.Database
import io.heckel.ntfy.db.QuakeData
import io.heckel.ntfy.db.QuakeRemoteMediator
import kotlinx.coroutines.flow.Flow

class QuakeViewModel(private val context: Context, private val database: Database) : ViewModel() {
    
    @OptIn(ExperimentalPagingApi::class)
    val quakes: Flow<PagingData<QuakeData>> = Pager(
        config = PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
            initialLoadSize = 20
        ),
        remoteMediator = QuakeRemoteMediator(context, database),
        pagingSourceFactory = { database.quakeHistoryDao().getPaged() } // Points to Room DAO
    ).flow.cachedIn(viewModelScope)
}

class QuakeViewModelFactory(private val context: Context, private val database: Database) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return QuakeViewModel(context, database) as T
    }
}
