# QuakeAlert Android App

Android app for **QuakeAlert** — earthquake alerts and station monitoring. It connects to the QuakeAlert backend to receive push notifications, view quake history, check station health, and use in-app chat.

**Package:** `id.my.bananapixel.quakealert`

## Features

- Earthquake alerts and warning screen with intensity and distance
- Quake history with swipe-to-refresh and error/retry
- Station/sensors list with health status and latency
- In-app chat
- Built on a fork of [ntfy-android](https://github.com/binwiederhier/ntfy-android); supports F-Droid (no Google Play Services) and Play (with Firebase) flavors

## Build

### Requirements

- Android SDK (compileSdk 36, minSdk 26)
- JDK 17

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

## License

Apache License 2.0. See [LICENSE](LICENSE).

This project is a fork of [ntfy-android](https://github.com/binwiederhier/ntfy-android) by [Philipp C. Heckel](https://heckel.io). Thanks to the ntfy project and to these resources:

- [RecyclerViewKotlin](https://github.com/android/views-widgets-samples/tree/main/RecyclerViewKotlin) (Apache 2.0)
- [Android Room with a View](https://github.com/googlecodelabs/android-room-with-a-view/tree/kotlin) (Apache 2.0)
- [Firebase Messaging Example](https://github.com/firebase/quickstart-android) (Apache 2.0)
- [Foreground service](https://robertohuertas.com/2019/06/29/android_foreground_services/)
- [github/gemoji](https://github.com/github/gemoji) (MIT) — emoji data
- [emoji-java](https://github.com/vdurmont/emoji-java) (MIT) — inlined for emoji.json
