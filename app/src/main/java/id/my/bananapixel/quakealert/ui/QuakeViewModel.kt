package id.my.bananapixel.quakealert.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import id.my.bananapixel.quakealert.db.Database
import id.my.bananapixel.quakealert.db.QuakeData
import id.my.bananapixel.quakealert.db.QuakeRepository
import id.my.bananapixel.quakealert.db.QuakeRemoteMediator
import kotlinx.coroutines.flow.Flow

/**
 * ViewModel for quake data with paging.
 * Note: Context is only used for RemoteMediator's HTTP layer (passed by factory).
 */
class QuakeViewModel(
    private val appContext: Context,
    private val quakeRepository: QuakeRepository,
    private val database: Database,
    private val api: id.my.bananapixel.quakealert.api.QuakeAlertApi
) : ViewModel() {
    
    @OptIn(ExperimentalPagingApi::class)
    val quakes: Flow<PagingData<QuakeData>> = Pager(
        config = PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
            initialLoadSize = 20
        ),
        remoteMediator = QuakeRemoteMediator(database, api),
        pagingSourceFactory = { database.quakeHistoryDao().getPaged() }
    ).flow.cachedIn(viewModelScope)
}

class QuakeViewModelFactory(
    private val context: Context,
    private val quakeRepository: QuakeRepository,
    private val database: Database,
    private val api: id.my.bananapixel.quakealert.api.QuakeAlertApi
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        QuakeViewModel(context.applicationContext, quakeRepository, database, api) as T
}
