# Testing Guide

## Overview

This document provides guidelines for testing the QuakeAlert app, including how to write unit tests, integration tests, and UI tests.

## Test Structure

```
app/src/test/          # Unit tests (JVM, no Android framework)
app/src/androidTest/   # Integration & UI tests (Android framework)
```

## Dependencies

Already in `build.gradle`:
```gradle
testImplementation 'junit:junit:4.13.2'
testImplementation 'io.mockk:mockk:1.13.8'
testImplementation 'io.insert-koin:koin-test:3.5.6'
```

Add for coroutine testing:
```gradle
testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3'
```

## Unit Testing ViewModels

### Example: QuakeHistoryViewModelTest

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class QuakeHistoryViewModelTest {
    
    private lateinit var viewModel: QuakeHistoryViewModel
    private lateinit var repository: QuakeRepository
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        viewModel = QuakeHistoryViewModel(repository)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `refreshQuakes success updates state`() = runTest {
        // Given
        coEvery { repository.fetchQuakes(any()) } returns Result.success(Unit)
        
        // When
        viewModel.refreshQuakes(mockk())
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        assertTrue(viewModel.quakeLoadState.value is QuakeLoadState.Success)
    }
}
```

## Unit Testing UseCases

### Template:

```kotlin
class FetchQuakesUseCaseTest {
    
    private lateinit var useCase: FetchQuakesUseCase
    private lateinit var repository: QuakeRepository
    
    @Before
    fun setup() {
        repository = mockk()
        useCase = FetchQuakesUseCase(repository)
    }
    
    @Test
    fun `invoke returns success when repository succeeds`() = runTest {
        // Given
        coEvery { repository.fetchQuakes(any()) } returns Result.success(Unit)
        
        // When
        val result = useCase(mockk())
        
        // Then
        assertTrue(result.isSuccess)
        coVerify { repository.fetchQuakes(any()) }
    }
    
    @Test
    fun `invoke returns failure when repository fails`() = runTest {
        // Given
        val error = AppError.NetworkError("Connection failed")
        coEvery { repository.fetchQuakes(any()) } returns Result.failure(error)
        
        // When
        val result = useCase(mockk())
        
        // Then
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }
}
```

## Testing Repository with Room

### Use in-memory database:

```kotlin
class QuakeRepositoryImplTest {
    
    private lateinit var database: Database
    private lateinit var quakeDao: QuakeHistoryDao
    private lateinit var repository: QuakeRepositoryImpl
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, Database::class.java)
            .allowMainThreadQueries()
            .build()
        quakeDao = database.quakeHistoryDao()
        repository = QuakeRepositoryImpl(quakeDao)
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    @Test
    fun `quakes flow emits database updates`() = runTest {
        // Given: Insert quake into database
        val quake = QuakeData(/* ... */)
        quakeDao.insertOrReplace(quake)
        
        // When: Observe quakes flow
        val result = repository.quakes.first()
        
        // Then: Should emit inserted quake
        assertEquals(1, result.size)
        assertEquals(quake.id, result[0].id)
    }
}
```

## Integration Testing

### Testing full flow from API to UI:

```kotlin
@RunWith(AndroidJUnit4::class)
class QuakeIntegrationTest {
    
    @Test
    fun `when earthquake detected, saves to database and shows notification`() = runTest {
        // Given: Mock web server with earthquake data
        mockWebServer.enqueue(MockResponse().setBody(/* JSON */))
        
        // When: Fetch earthquakes
        val result = repository.fetchQuakes(context)
        
        // Then: Data saved and notification triggered
        assertTrue(result.isSuccess)
        val saved = repository.quakes.first()
        assertEquals(1, saved.size)
    }
}
```

## UI Testing with Espresso

Add dependencies:
```gradle
androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
androidTestImplementation 'androidx.test:runner:1.5.2'
androidTestImplementation 'androidx.test:rules:1.5.0'
```

### Example:

```kotlin
@RunWith(AndroidJUnit4::class)
class HistoryFragmentTest {
    
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)
    
    @Test
    fun testQuakeListDisplayed() {
        // Navigate to history
        onView(withId(R.id.navigation_history)).perform(click())
        
        // Verify RecyclerView displayed
        onView(withId(R.id.history_recycler_view))
            .check(matches(isDisplayed()))
    }
}
```

## Running Tests

```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests QuakeHistoryViewModelTest

# Run with coverage
./gradlew testFdroidDebugUnitTestCoverage

# View coverage report
open app/build/reports/coverage/test/fdroidDebug/index.html
```

## Best Practices

1. **Test Naming:** Use backticks for descriptive names:
   ```kotlin
   @Test
   fun `refreshQuakes success updates state to Success`()
   ```

2. **AAA Pattern:** Arrange, Act, Assert
   ```kotlin
   // Given: Setup test data
   // When: Execute action
   // Then: Verify outcome
   ```

3. **Mock External Dependencies:** Always mock IO operations, network calls, database
4. **Test One Thing:** Each test should verify one behavior
5. **Use Test Doubles:** Prefer mocks/stubs over real implementations
6. **Test Error Cases:** Don't just test happy paths

## Test Coverage Goals

| Layer | Target | Priority |
|-------|--------|----------|
| Utils | 90% | High |
| Domain/UseCases | 80% | High |
| Repository | 70% | High |
| ViewModel | 80% | High |
| UI/Integration | 50% | Medium |

## Common Pitfalls

1. **Not using test dispatcher:** Use `StandardTestDispatcher` for coroutines
2. **Forgetting to advance time:** Call `advanceUntilIdle()` after coroutine launches
3. **Not resetting Main dispatcher:** Always reset in `@After`
4. **Testing implementation:** Test behavior, not implementation details
5. **Flaky tests:** Avoid Thread.sleep, use test dispatchers

## Resources

- [Kotlin Coroutines Testing](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/)
- [MockK Documentation](https://mockk.io/)
- [Android Testing Guide](https://developer.android.com/training/testing)
