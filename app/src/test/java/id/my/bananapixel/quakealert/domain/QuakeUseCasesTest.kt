package id.my.bananapixel.quakealert.domain

import android.content.Context
import id.my.bananapixel.quakealert.db.QuakeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [FetchQuakesUseCase] and [ClearQuakesUseCase].
 *
 * Tests are grouped by use case and cover:
 * - [FetchQuakesUseCase]: success path, network error, parse error, unknown error
 * - [ClearQuakesUseCase]: delegation to repository, invocation count, idempotency
 *
 * Mock strategy: [QuakeRepository] is mocked. Each test controls exactly what
 * the repository returns so tests are hermetic and fast.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuakeUseCasesTest {

    private lateinit var quakeRepository: QuakeRepository
    private lateinit var context: Context

    private lateinit var fetchQuakesUseCase: FetchQuakesUseCase
    private lateinit var clearQuakesUseCase: ClearQuakesUseCase

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        quakeRepository = mockk()
        context = mockk()

        // Default: quakes property returns an empty Flow (needed by interface contract)
        every { quakeRepository.quakes } returns flowOf(emptyList())

        fetchQuakesUseCase = FetchQuakesUseCase(quakeRepository)
        clearQuakesUseCase = ClearQuakesUseCase(quakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ===========================================================================
    // FetchQuakesUseCase
    // ===========================================================================

    @Test
    fun `FetchQuakesUseCase — success returns Result_success`() = runTest {
        // Given: repository returns success
        coEvery { quakeRepository.fetchQuakes(context) } returns Result.success(Unit)

        // When: invoking the use case
        val result = fetchQuakesUseCase(context)

        // Then: result is successful
        assertTrue(result.isSuccess)
        assertEquals(Unit, result.getOrNull())
    }

    @Test
    fun `FetchQuakesUseCase — success delegates to repository exactly once`() = runTest {
        // Given: repository returns success
        coEvery { quakeRepository.fetchQuakes(context) } returns Result.success(Unit)

        // When
        fetchQuakesUseCase(context)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: repository was called exactly once
        coVerify(exactly = 1) { quakeRepository.fetchQuakes(context) }
    }

    @Test
    fun `FetchQuakesUseCase — NetworkError propagates as Result_failure`() = runTest {
        // Given: repository signals a network error
        val networkError = AppError.NetworkError("Connection timed out")
        coEvery { quakeRepository.fetchQuakes(context) } returns Result.failure(networkError)

        // When
        val result = fetchQuakesUseCase(context)

        // Then: result is a failure wrapping the network error
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is AppError.NetworkError)
        assertEquals("Connection timed out", (error as AppError.NetworkError).errorMessage)
    }

    @Test
    fun `FetchQuakesUseCase — ParseError propagates as Result_failure`() = runTest {
        // Given: repository signals a parse/serialization error
        val parseError = AppError.ParseError("Unexpected JSON structure")
        coEvery { quakeRepository.fetchQuakes(context) } returns Result.failure(parseError)

        // When
        val result = fetchQuakesUseCase(context)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is AppError.ParseError)
        assertEquals("Unexpected JSON structure", (error as AppError.ParseError).errorMessage)
    }

    @Test
    fun `FetchQuakesUseCase — UnknownError propagates as Result_failure`() = runTest {
        // Given: repository signals an unknown error
        val unknownError = AppError.UnknownError("Something went wrong")
        coEvery { quakeRepository.fetchQuakes(context) } returns Result.failure(unknownError)

        // When
        val result = fetchQuakesUseCase(context)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is AppError.UnknownError)
    }

    @Test
    fun `FetchQuakesUseCase — failure wraps repository error not null`() = runTest {
        // Given: repository returns a failure (any AppError)
        coEvery { quakeRepository.fetchQuakes(context) } returns
            Result.failure(AppError.ApiError(503, "Service Unavailable"))

        // When
        val result = fetchQuakesUseCase(context)

        // Then: exceptionOrNull is non-null and is an AppError
        assertFalse(result.isSuccess)
        assertNotNull(result.exceptionOrNull())
        assertTrue(result.exceptionOrNull() is AppError)
    }

    @Test
    fun `FetchQuakesUseCase — can be called multiple times independently`() = runTest {
        // Given: success on first call, failure on second
        coEvery { quakeRepository.fetchQuakes(context) } returnsMany listOf(
            Result.success(Unit),
            Result.failure(AppError.NetworkError("Retry failed"))
        )

        // When: calling twice
        val first = fetchQuakesUseCase(context)
        val second = fetchQuakesUseCase(context)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(first.isSuccess)
        assertTrue(second.isFailure)
        coVerify(exactly = 2) { quakeRepository.fetchQuakes(context) }
    }

    // ===========================================================================
    // ClearQuakesUseCase
    // ===========================================================================

    @Test
    fun `ClearQuakesUseCase — delegates to repository clearQuakes`() = runTest {
        // Given: repository clear is stubbed
        coEvery { quakeRepository.clearQuakes() } returns Unit

        // When
        clearQuakesUseCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: repository received the call
        coVerify(exactly = 1) { quakeRepository.clearQuakes() }
    }

    @Test
    fun `ClearQuakesUseCase — completes without exception on success`() = runTest {
        // Given
        coEvery { quakeRepository.clearQuakes() } returns Unit

        // When / Then: no exception is thrown
        clearQuakesUseCase()
        testDispatcher.scheduler.advanceUntilIdle()
        // Reaching here means it completed normally
    }

    @Test
    fun `ClearQuakesUseCase — can be called multiple times`() = runTest {
        // Given
        coEvery { quakeRepository.clearQuakes() } returns Unit

        // When: calling 3 times
        clearQuakesUseCase()
        clearQuakesUseCase()
        clearQuakesUseCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: repository was called 3 times (idempotent from the use-case perspective)
        coVerify(exactly = 3) { quakeRepository.clearQuakes() }
    }

    @Test
    fun `ClearQuakesUseCase — FetchQuakesUseCase are independent — separate repository calls`() = runTest {
        // Given: both are stubbed
        coEvery { quakeRepository.fetchQuakes(context) } returns Result.success(Unit)
        coEvery { quakeRepository.clearQuakes() } returns Unit

        // When: using both in the same test
        val fetchResult = fetchQuakesUseCase(context)
        clearQuakesUseCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: each use case only called its own repository method
        assertTrue(fetchResult.isSuccess)
        coVerify(exactly = 1) { quakeRepository.fetchQuakes(context) }
        coVerify(exactly = 1) { quakeRepository.clearQuakes() }
    }
}
