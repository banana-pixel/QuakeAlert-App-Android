# Contributing to QuakeAlert Android App

Thank you for your interest in contributing to QuakeAlert! This document provides guidelines for contributing to the project.

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Pull Request Process](#pull-request-process)
- [Reporting Bugs](#reporting-bugs)
- [Suggesting Enhancements](#suggesting-enhancements)

## Code of Conduct

We expect all contributors to be respectful and constructive. Please:
- Be welcoming and inclusive
- Respect differing viewpoints
- Accept constructive criticism gracefully
- Focus on what's best for the community
- Show empathy towards other community members

## Getting Started

1. **Fork the repository** to your own GitHub account
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR-USERNAME/QuakeAlert-App-Android.git
   cd QuakeAlert-App-Android
   ```
3. **Add upstream remote:**
   ```bash
   git remote add upstream https://github.com/banana-pixel/QuakeAlert-App-Android.git
   ```
4. **Create a branch** for your changes:
   ```bash
   git checkout -b feature/your-feature-name
   ```

## Development Setup

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK (API 26-36)
- Git

### Building the Project

```bash
# Sync Gradle dependencies
./gradlew build

# Build debug APK
./gradlew assembleFdroidDebug

# Run tests
./gradlew test

# Run app on connected device
./gradlew installFdroidDebug
```

## How to Contribute

### Types of Contributions

We welcome:
- 🐛 **Bug fixes**
- ✨ **New features**
- 📝 **Documentation improvements**
- 🌍 **Translations**
- 🧪 **Tests**
- ♻️ **Code refactoring**
- 🎨 **UI/UX improvements**

### Before You Start

1. **Check existing issues** - Someone might already be working on it
2. **Open an issue first** for large changes - Discuss the approach before implementing
3. **Keep changes focused** - One feature/fix per pull request

## Coding Standards

### Kotlin Style Guide

Follow the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html):

```kotlin
// ✅ Good
class QuakeRepository(
    private val quakeDao: QuakeHistoryDao
) {
    suspend fun fetchQuakes(): Result<Unit> {
        // Implementation
    }
}

// ❌ Bad
class QuakeRepository(private val quakeDao: QuakeHistoryDao) {
    suspend fun fetchQuakes(): Result<Unit> 
    {
        // Implementation
    }
}
```

### Naming Conventions

- **Classes:** PascalCase (`QuakeHistoryViewModel`)
- **Functions:** camelCase (`fetchQuakes()`)
- **Variables:** camelCase (`quakeData`)
- **Constants:** UPPER_SNAKE_CASE (`MAX_RETRY_COUNT`)
- **Private fields:** prefix with underscore (`_quakeLoadState`)

### Architecture Patterns

This app follows **Clean Architecture**:

```
UI Layer (Fragment/Activity/ViewModel)
    ↓
Domain Layer (UseCases)
    ↓
Data Layer (Repository/DAO)
```

**Guidelines:**
- UI layer should NOT directly access DAOs or API clients
- Business logic belongs in UseCases
- ViewModels should be thin, delegating to UseCases
- Repositories should return Flow or suspend functions
- Use sealed classes for state management

### Documentation

Add KDoc comments for:
- Public classes, functions, and properties
- Complex algorithms
- Non-obvious code

```kotlin
/**
 * Fetches earthquake reports from the backend API and updates local database.
 * 
 * @param context Android context required for API calls
 * @return [AppResult]<Unit> indicating success or failure
 * @throws NetworkError if connection fails
 */
suspend fun fetchQuakes(context: Context): AppResult<Unit>
```

### Kotlin Best Practices

1. **Prefer `val` over `var`**
   ```kotlin
   val quakeData = fetchQuakes() // Immutable
   ```

2. **Use data classes for models**
   ```kotlin
   data class QuakeData(val id: String, val magnitude: Double)
   ```

3. **Leverage scope functions**
   ```kotlin
   quake?.let { displayQuake(it) }
   ```

4. **Use coroutines for async operations**
   ```kotlin
   viewModelScope.launch {
       val result = fetchQuakes()
   }
   ```

5. **Prefer Flow over LiveData**
   ```kotlin
   val quakes: Flow<List<QuakeData>> = repository.quakes
   ```

## Testing Guidelines

### Test Coverage Requirements

All new code should include tests:
- **ViewModels:** 80%+ coverage
- **UseCases:** 80%+ coverage
- **Utils:** 90%+ coverage
- **Repository:** 70%+ coverage

### Writing Tests

**Unit Test Template:**
```kotlin
class QuakeUseCaseTest {
    
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
    }
}
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests QuakeHistoryViewModelTest

# Run with coverage
./gradlew testFdroidDebugUnitTestCoverage
```

## Pull Request Process

### Before Submitting

1. **Update your branch** with latest upstream changes:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run tests** and ensure they pass:
   ```bash
   ./gradlew test
   ```

3. **Build the app** to verify compilation:
   ```bash
   ./gradlew assembleFdroidDebug
   ```

4. **Lint check** (optional but recommended):
   ```bash
   ./gradlew lint
   ```

### Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: Add real-time earthquake notifications
fix: Correct timezone display in history
docs: Update README with setup instructions
test: Add tests for QuakeHistoryViewModel
refactor: Extract validation logic to separate util
```

**Format:**
```
<type>: <description>

[optional body]

[optional footer]
```

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

### Creating a Pull Request

1. Push your branch to your fork:
   ```bash
   git push origin feature/your-feature-name
   ```

2. Go to GitHub and create a Pull Request

3. Fill out the PR template:
   - **Description:** What does this PR do?
   - **Related Issue:** Link to issue (e.g., "Fixes #123")
   - **Testing:** How was this tested?
   - **Screenshots:** If UI changes, include before/after screenshots

4. **Be responsive** to review comments

### PR Review Criteria

Your PR will be reviewed for:
- ✅ Code quality and style
- ✅ Test coverage
- ✅ Documentation
- ✅ Performance impact
- ✅ Backward compatibility
- ✅ Security concerns

## Reporting Bugs

### Before Reporting

- Search existing issues to avoid duplicates
- Test with the latest version
- Gather relevant information

### Bug Report Template

```markdown
**Description:**
Brief description of the bug

**To Reproduce:**
1. Go to '...'
2. Click on '...'
3. Scroll down to '...'
4. See error

**Expected Behavior:**
What you expected to happen

**Screenshots:**
If applicable

**Environment:**
- Device: [e.g., Pixel 6]
- Android Version: [e.g., Android 13]
- App Version: [e.g., 0.4]
- Build Flavor: [fdroid/play]

**Additional Context:**
Any other relevant information
```

## Suggesting Enhancements

### Feature Request Template

```markdown
**Is your feature request related to a problem?**
Clear description of the problem

**Describe the solution you'd like**
Clear description of what you want to happen

**Describe alternatives you've considered**
Alternative solutions or features

**Additional context**
Mockups, examples, or references
```

## Translation Contributions

We welcome translations to new languages!

1. Copy `app/src/main/res/values/strings.xml`
2. Create `values-XX/strings.xml` (XX = language code)
3. Translate all strings
4. Test the app in your language
5. Submit a PR

**Language Codes:** en (English), id (Indonesian), es (Spanish), fr (French), etc.

## Questions?

- **GitHub Discussions:** For general questions
- **GitHub Issues:** For bugs and feature requests
- **Code Review:** Tag maintainers in your PR

## Recognition

Contributors will be acknowledged in:
- README.md acknowledgments section
- Release notes
- GitHub contributor graph

Thank you for contributing to QuakeAlert! 🙏

---

**Project Maintainers:** @banana-pixel

**License:** Apache 2.0
