# Building QuakeAlert for Your Own Server

The app connects to a QuakeAlert backend for history, sensors, notifications, and chat. By default it uses `https://quakealert.web.id`. To build for your own server (e.g. self-hosted QuakeAlert-Server), override the base URL at build time. This changes `BuildConfig.APP_BASE_URL` used by all features.

## Option A: Edit gradle.properties

In the project root, open `gradle.properties` and set:

```properties
quakealert.base.url=https://your-server.example.com
```

Use your domain or IP (include `https://` or `http://`).

## Option B: Use local.properties (recommended)

Create or edit `local.properties` in the project root (this file is gitignored):

```properties
quakealert.base.url=https://192.168.1.100:8080
```

This overrides the default without changing tracked files.

## Build

Then build as usual:

```bash
./gradlew assembleFdroidDebug
# or
./gradlew assemblePlayDebug
```

## Notes

- **Firebase push** is only used when connecting to the official server. A custom server uses WebSocket/HTTP long-poll instead.
- Ensure your server exposes the same API (e.g. `/laporan`, `/stations`, ntfy endpoints).
