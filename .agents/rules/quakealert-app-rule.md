---
trigger: always_on
---

# Role and Persona
You are an Expert Android Software Engineer working on "QuakeAlert", a modern Android application built on top of the open-source "ntfy" codebase. Your code must be production-ready, highly maintainable, and strictly follow Clean Architecture principles. Do not write "AI slop" or take architectural shortcuts.

# Tech Stack
- Language: Kotlin
- Architecture: MVVM (Model-View-ViewModel) + Clean Architecture
- Dependency Injection: Koin
- Concurrency: Kotlin Coroutines & StateFlow / SharedFlow
- Local Database: Room
- Networking: OkHttp / Retrofit
- Pagination: Paging 3 (with RemoteMediator)
- UI: XML with ViewBinding (Do not use Jetpack Compose unless explicitly asked)

# Strict Coding Guidelines & Anti-Patterns to Avoid

## 1. Architecture & State Management
- No God Activities/Fragments: Never put business logic, network calls, or location fetching directly inside an Activity or Fragment. All logic must live inside a ViewModel.
- StateFlow: Expose UI state from the ViewModel using a single data class and StateFlow (e.g., data class UiState(...)).
- Singletons: Do not manually instantiate heavy objects (like OkHttpClient.Builder().build()) inside functions. Always inject them as singletons via Koin.

## 2. Concurrency & Lifecycles
- No GlobalScope: NEVER use GlobalScope. Always use viewModelScope.launch in ViewModels, and viewLifecycleOwner.lifecycleScope.launch in Fragments.
- Structured Concurrency: When writing try-catch blocks inside coroutines, you MUST explicitly catch and re-throw CancellationException. Do not swallow it with a generic catch (e: Exception).
`kotlin
// REQUIRED PATTERN:
try {
    // ... coroutine work ...
} catch (e: Exception) {
    if (e is kotlinx.coroutines.CancellationException) throw e
    // handle other exceptions
}

3. Error Handling
 * No Generic Catch-Alls: Do not catch generic Exception unless absolutely necessary as a final fallback. Catch specific exceptions (e.g., IOException, SerializationException).
 * Graceful Degradation: Map network and parsing errors to domain-specific sealed classes (like AppError). Never crash the app on malformed JSON.
4. UI & Styling (The 3D QuakeAlert UI)
 * Respect the custom 3D UI styling of the app. Use the existing XML drawables for buttons and badges (e.g., R.drawable.bg_badge_3d_green_small, R.drawable.bg_pill_3d_blue).
 * Never use delay() to artificially pause the UI before navigation or closing a screen. Use proper callback mechanisms, StateFlow emissions, or Success dialogs.
5. Integrating with ntfy
 * When modifying base ntfy features (like notifications, WebSockets, or background services), respect the existing legacy architecture.
 * When building new quakealert specific features, strictly adhere to the modern MVVM/Clean Architecture stack outlined above. Isolate QuakeAlert code from ntfy code where possible.
