
package id.my.bananapixel.quakealert.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import id.my.bananapixel.quakealert.db.Database
import id.my.bananapixel.quakealert.db.QuakeData
import id.my.bananapixel.quakealert.db.QuakeRemoteMediator
import kotlinx.coroutines.flow.Flow

class QuakeViewModel(private val appContext: Context, private val database: Database) : ViewModel() {
    // Uses Application context to avoid memory leaks (Context must not be Activity)

    @OptIn(ExperimentalPagingApi::class)
    val quakes: Flow<PagingData<QuakeData>> = Pager(
        config = PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
            initialLoadSize = 20
        ),
        remoteMediator = QuakeRemoteMediator(appContext, database),
        pagingSourceFactory = { database.quakeHistoryDao().getPaged() } // Points to Room DAO
    ).flow.cachedIn(viewModelScope)
}

class QuakeViewModelFactory(private val context: Context, private val database: Database) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Use applicationContext to prevent memory leaks when ViewModel holds Context
        return QuakeViewModel(context.applicationContext, database) as T
    }
}
