package id.my.bananapixel.quakealert.ui

import android.content.Context
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.db.QuakeData
import id.my.bananapixel.quakealert.db.QuakeRepository
import id.my.bananapixel.quakealert.domain.AppError
import id.my.bananapixel.quakealert.domain.AppResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for QuakeHistoryViewModel.
 * 
 * Tests the business logic of earthquake history management including:
 * - Fetching quakes from repository
 * - Handling loading states
 * - Error handling and user-facing messages
 * - Data flow from repository to UI
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuakeHistoryViewModelTest {

    private lateinit var viewModel: QuakeHistoryViewModel
    private lateinit var repository: QuakeRepository
    private lateinit var context: Context
    
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        repository = mockk(relaxed = true)
        context = mockk(relaxed = true)
        
        // Setup default mocks
        every { context.getString(R.string.error_connection_message) } returns "Network error"
        every { context.getString(R.string.error_generic_message) } returns "Generic error"
        
        viewModel = QuakeHistoryViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should be Idle`() = runTest {
        // Given: Fresh ViewModel
        
        // When: Observing initial state
        val state = viewModel.quakeLoadState.value
        
        // Then: State should be Idle
        assertTrue(state is QuakeLoadState.Idle)
    }

    @Test
    fun `refreshQuakes success updates state to Success`() = runTest {
        // Given: Repository returns success
        coEvery { repository.fetchQuakes(any()) } returns Result.success(Unit)
        
        // When: Refreshing quakes
        viewModel.refreshQuakes(context)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then: State should be Success
        val state = viewModel.quakeLoadState.value
        assertTrue(state is QuakeLoadState.Success)
        coVerify { repository.fetchQuakes(context) }
    }

    @Test
    fun `refreshQuakes network error updates state to Error with message`() = runTest {
        // Given: Repository returns network error
        coEvery { repository.fetchQuakes(any()) } returns 
            Result.failure(AppError.NetworkError("Connection failed"))
        
        // When: Refreshing quakes
        viewModel.refreshQuakes(context)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then: State should be Error with network message
        val state = viewModel.quakeLoadState.value
        assertTrue(state is QuakeLoadState.Error)
        assertEquals("Network error", (state as QuakeLoadState.Error).message)
    }

    @Test
    fun `refreshQuakes parse error updates state to Error with generic message`() = runTest {
        // Given: Repository returns parse error
        coEvery { repository.fetchQuakes(any()) } returns 
            Result.failure(AppError.ParseError("Invalid JSON"))
        
        // When: Refreshing quakes
        viewModel.refreshQuakes(context)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then: State should be Error with generic message
        val state = viewModel.quakeLoadState.value
        assertTrue(state is QuakeLoadState.Error)
        assertEquals("Generic error", (state as QuakeLoadState.Error).message)
    }

    @Test
    fun `refreshQuakes sets Loading state during execution`() = runTest {
        // Given: Repository takes time to respond
        val testScope = this
        coEvery { repository.fetchQuakes(any()) } coAnswers {
            testScope.testScheduler.advanceTimeBy(100)
            Result.success(Unit)
        }
        
        // When: Refreshing quakes
        viewModel.refreshQuakes(context)
        
        // Then: State should be Loading initially
        assertEquals(QuakeLoadState.Loading, viewModel.quakeLoadState.value)
        
        // And: Eventually becomes Success
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.quakeLoadState.value is QuakeLoadState.Success)
    }

    @Test
    fun `quakes flow emits data from repository`() = runTest {
        // Given: Repository has quake data
        val mockQuakes = listOf(
            QuakeData(
                id = 1,
                intensitas_maks = "V",
                lokasi = "Test Location",
                latitude = -6.2,
                longitude = 106.8,
                waktu_kejadian = 1000000L,
                durasi = 30,
                sync_time = 1000100L
            )
        )
        every { repository.quakes } returns flowOf(mockQuakes)
        
        // When: Observing quakes
        val quakes = viewModel.quakes.first()
        
        // Then: Should receive data from repository
        assertEquals(1, quakes.size)
        assertEquals("V", quakes[0].intensitas_maks)
        assertEquals("Test Location", quakes[0].lokasi)
    }

    @Test
    fun `clearQuakes calls repository clearQuakes`() = runTest {
        // Given: ViewModel with repository
        coEvery { repository.clearQuakes() } returns Unit
        
        // When: Clearing quakes
        viewModel.clearQuakes()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then: Repository should be called
        coVerify { repository.clearQuakes() }
    }

    @Test
    fun `multiple refresh calls handle state correctly`() = runTest {
        // Given: Repository returns success
        coEvery { repository.fetchQuakes(any()) } returns Result.success(Unit)
        
        // When: Calling refresh multiple times
        viewModel.refreshQuakes(context)
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.refreshQuakes(context)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then: State should still be Success
        assertTrue(viewModel.quakeLoadState.value is QuakeLoadState.Success)
        
        // And: Repository called twice
        coVerify(exactly = 2) { repository.fetchQuakes(context) }
    }
}
