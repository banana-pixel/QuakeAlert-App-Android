<p align="center">
  <img src="docs/icon.png" alt="QuakeAlert" width="128">
</p>

# QuakeAlert Android App

**Real-time earthquake detection and alert system for Android**

Android app for **QuakeAlert** — connecting to a network of ESP32-powered seismic sensors for early earthquake detection. Receive instant push notifications, monitor station health, view earthquake history, and communicate via in-app chat.

**Package:** `id.my.bananapixel.quakealert`  
**Minimum SDK:** Android 8.0 (API 26)  
**Target SDK:** Android 14 (API 36)

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](.)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.24-purple.svg)](https://kotlinlang.org)

## ✨ Features

### 🚨 Real-Time Alerts
- Instant earthquake notifications with intensity (MMI scale) and distance
- Full-screen warning UI with safety instructions
- Alert sound with insistent alarm mode
- 10-minute automatic reset

### 📊 Earthquake History
- Complete history of detected earthquakes
- Swipe-to-refresh with offline caching
- Detailed view with map location
- Share earthquake reports

### 🌐 Station Monitoring
- Real-time health status of seismic sensors
- Network latency tracking
- Online/offline station indicators
- Station map view

### 💬 In-App Chat
- Community chat with other users
- Message reactions and threading
- Real-time updates via Socket.IO
- Automatic message pruning

### 🎨 Modern Design
- Material Design 3 components
- Dark mode support
- Smooth animations
- Indonesian & English translations

### 🔧 Technical Highlights
- Built on Clean Architecture principles
- Offline-first with Room database
- Kotlin Coroutines + Flow for reactive programming
- Dependency Injection with Koin
- ProGuard/R8 optimized for production

## 🏗️ Architecture

This app follows **Clean Architecture** principles with clear separation of concerns:

```
┌─────────────────────────────────────────────┐
│              UI Layer (Presentation)        │
│  ┌─────────────┐  ┌──────────────────────┐  │
│  │  Fragment   │  │    ViewModel         │  │
│  │  Activity   │  │    (ViewBinding)     │  │
│  └─────────────┘  └──────────────────────┘  │
└────────────────┬────────────────────────────┘
                 │
        ┌────────▼─────────┐
        │  Domain Layer    │
        │                  │
        │  • UseCases      │
        │  • Domain Models │
        │  • AppError      │
        └────────┬─────────┘
                 │
    ┌────────────▼────────────┐
    │     Data Layer          │
    │                         │
    │  • Repository Interface │
    │  • Repository Impl      │
    │  • Room Database        │
    │  • API Client (OkHttp)  │
    │  • Socket.IO Client     │
    └─────────────────────────┘
```

### Key Components

**Data Layer:**
- `QuakeRepository` - Earthquake data operations
- `ChatRepository` - Chat message operations  
- `Database` - Room database with DAOs
- Offline-first: Local DB as Single Source of Truth

**Domain Layer:**
- `FetchQuakesUseCase` - Fetch earthquake reports
- `ClearQuakesUseCase` - Clear cached data
- `SaveChatMessagesUseCase` - Save chat messages
- `PruneChatMessagesUseCase` - Clean old messages
- `AppError` - Typed errors (NetworkError, ParseError, etc.)

**UI Layer:**
- `WarningFragment` - Full-screen earthquake warning
- `HistoryFragment` - Earthquake history list
- `SensorsFragment` - Station monitoring
- `ChatFragment` - Real-time chat
- ViewModels with StateFlow for reactive UI updates

**Dependency Injection:**
- Koin modules: `dataModule`, `domainModule`, `uiModule`
- Constructor injection for testability

## 🧪 Testing

### Running Tests

```bash
# Run all unit tests
./gradlew test

# Run tests for specific flavor
./gradlew testFdroidDebugUnitTest

# Run with coverage
./gradlew testFdroidDebugUnitTestCoverage
```

### Test Coverage

| Layer | Coverage | Status |
|-------|----------|--------|
| Utils (Validation, Geo, Alert) | 80%+ | ✅ Complete |
| Data Parsing | 75%+ | ✅ Complete |
| Domain/UseCases | 0% | 🔴 TODO |
| ViewModels | 0% | 🔴 TODO |
| Integration Tests | 0% | 🔴 TODO |

**Existing Tests:**
- `ValidationUtilTest` - Input validation
- `GeoUtilTest` - Coordinate calculations
- `AlertLogicTest` - Alert triggering logic
- `QuakeReportParserTest` - API response parsing

**Planned Tests:** (see `docs/TESTING.md` for templates)
- ViewModel tests with MockK
- Repository tests with Room in-memory DB
- Integration tests for end-to-end flows

## Build

### Requirements

- Android SDK (compileSdk 36, minSdk 26)
- JDK 17
- Gradle 8.2+

### Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/banana-pixel/QuakeAlert-App-Android.git
   cd QuakeAlert-App-Android
   ```

2. **Configure server URL** (optional):
   
   Create `local.properties` or edit `gradle.properties`:
   ```properties
   quakealert.base.url=https://your-server.com
   ```

3. **For Google Play flavor** (optional):
   
   Add `app/google-services.json` from Firebase Console

4. **Build the app:**
   ```bash
   ./gradlew assembleFdroidDebug
   ```

### Flavors

| Flavor   | Use case                          | Extra requirement                    |
|----------|-----------------------------------|--------------------------------------|
| **fdroid** | F-Droid or APK without Google     | None                                 |
| **play**   | Google Play (Firebase push)      | `google-services.json` in `app/`     |

### Commands

```bash
# F-Droid / no Firebase (recommended if you don't have google-services.json)
./gradlew assembleFdroidDebug
# or release
./gradlew assembleFdroidRelease

# Play (requires app/google-services.json)
./gradlew assemblePlayDebug
./gradlew assemblePlayRelease
```

### Unit tests

```bash
./gradlew testFdroidDebugUnitTest
```

See [docs/TEST_COVERAGE.md](docs/TEST_COVERAGE.md) for what’s covered and how to add tests.

## Backend

The app is designed to work with the QuakeAlert backend (e.g. `quakealert.bananapixel.my.id`). Base URL and endpoints are configured in the app.

### Building for your own server

Override `quakealert.base.url` in `gradle.properties` or `local.properties`, then build. See [docs/BUILDING.md](docs/BUILDING.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).

This project is a fork of [ntfy-android](https://github.com/binwiederhier/ntfy-android) by [Philipp C. Heckel](https://heckel.io). Thanks to the ntfy project and to these resources:

- [Tabler Icons](https://tabler.io/icons) (MIT) — icon set used in the app
- [RecyclerViewKotlin](https://github.com/android/views-widgets-samples/tree/main/RecyclerViewKotlin) (Apache 2.0)
- [Android Room with a View](https://github.com/googlecodelabs/android-room-with-a-view/tree/kotlin) (Apache 2.0)
- [Firebase Messaging Example](https://github.com/firebase/quickstart-android) (Apache 2.0)
- [Foreground service](https://robertohuertas.com/2019/06/29/android_foreground_services/)
- [github/gemoji](https://github.com/github/gemoji) (MIT) — emoji data
- [emoji-java](https://github.com/vdurmont/emoji-java) (MIT) — inlined for emoji.json
