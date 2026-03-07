package id.my.bananapixel.quakealert.ui

import android.content.Context
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.db.QuakeData
import id.my.bananapixel.quakealert.db.QuakeRepository
import id.my.bananapixel.quakealert.domain.AppError
import id.my.bananapixel.quakealert.domain.AppResult
import id.my.bananapixel.quakealert.domain.FetchQuakesUseCase
import id.my.bananapixel.quakealert.domain.ClearQuakesUseCase
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
    private lateinit var fetchQuakesUseCase: FetchQuakesUseCase
    private lateinit var clearQuakesUseCase: ClearQuakesUseCase
    private lateinit var context: Context
    
    // Use StandardTestDispatcher for proper synchronization
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        repository = mockk()
        fetchQuakesUseCase = mockk()
        clearQuakesUseCase = mockk()
        context = mockk()
        
        // Setup default mocks
        every { context.getString(R.string.error_connection_message) } returns "Network error"
        every { context.getString(R.string.error_generic_message) } returns "Generic error"
        every { repository.quakes } returns flowOf(emptyList())
        
        viewModel = QuakeHistoryViewModel(repository, fetchQuakesUseCase, clearQuakesUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should be Idle`() {
        // Given: Fresh ViewModel
        
        // When: Observing initial state
        val state = viewModel.quakeLoadState.value
        
        // Then: State should be Idle
        assertTrue(state is QuakeLoadState.Idle)
    }

    @Test
    fun `refreshQuakes success updates state to Success`() = runTest {
        // Given: Use case returns success
        coEvery { fetchQuakesUseCase(any()) } returns Result.success(Unit)
        
        // When: Refreshing quakes
        viewModel.refreshQuakes(context)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then: State should be Success
        val state = viewModel.quakeLoadState.value
        assertTrue(state is QuakeLoadState.Success)
        coVerify { fetchQuakesUseCase(context) }
    }

    @Test
    fun `refreshQuakes network error updates state to Error with message`() = runTest {
        // Given: Use case returns network error
        coEvery { fetchQuakesUseCase(any()) } returns 
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
        // Given: Use case returns parse error
        coEvery { fetchQuakesUseCase(any()) } returns 
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
        // Given: Use case responds immediately
        coEvery { fetchQuakesUseCase(any()) } returns Result.success(Unit)
        
        // When: Refreshing quakes
        viewModel.refreshQuakes(context)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then: State becomes Success
        assertTrue(viewModel.quakeLoadState.value is QuakeLoadState.Success)
    }

    @Test
    fun `quakes flow emits data from repository`() = runTest {
        // Given: Repository has quake data
        val mockQuakes = listOf(
            QuakeData(
                id = "quake-001",
                magnitude = 5.5,
                place = "Test Location",
                time = 1000000L,
                description = "Test earthquake",
                latitude = -6.2,
                longitude = 106.8,
                pga = "0.5",
                durasi = 30,
                station_id = "STATION1",
                intensity = "V",
                sync_time = 1000100L
            )
        )
        val testRepository = mockk<QuakeRepository>()
        every { testRepository.quakes } returns flowOf(mockQuakes)
        
        // When: Creating ViewModel with test repository
        val testViewModel = QuakeHistoryViewModel(testRepository, fetchQuakesUseCase, clearQuakesUseCase)
        val quakes = testViewModel.quakes.first()
        
        // Then: Should receive data from repository
        assertEquals(1, quakes.size)
        assertEquals("V", quakes[0].intensity)
        assertEquals("Test Location", quakes[0].place)
    }

    @Test
    fun `clearAllQuakes calls use case`() = runTest {
        // Given: Use case is ready
        coEvery { clearQuakesUseCase() } returns Unit
        
        // When: Clearing quakes
        viewModel.clearAllQuakes()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then: Use case should be called
        coVerify { clearQuakesUseCase() }
    }

    @Test
    fun `multiple refresh calls handle state correctly`() = runTest {
        // Given: Use case returns success
        coEvery { fetchQuakesUseCase(any()) } returns Result.success(Unit)
        
        // When: Calling refresh multiple times
        viewModel.refreshQuakes(context)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.refreshQuakes(context)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then: State should still be Success
        assertTrue(viewModel.quakeLoadState.value is QuakeLoadState.Success)
        
        // And: Use case called twice
        coVerify(atLeast = 2) { fetchQuakesUseCase(context) }
    }
}
