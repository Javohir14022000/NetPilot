# NetPilot

In-app HTTP inspector for Android: see every OkHttp request your app makes, including Retrofit and Ktor's OkHttp engine. Later stages add the ability to control that traffic.

> Status: **stage 1** — capture, storage and inspector UI. Mock rules, breakpoints,
> edit & retry and an MCP server for AI tools are planned next.

## Setup

```kotlin
dependencies {
    debugImplementation(project(":netpilot"))
    releaseImplementation(project(":netpilot-no-op"))
}
```

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(NetPilotInterceptor(context))
    .build()

// Ktor: reuse the same client
val ktor = HttpClient(OkHttp) { engine { preconfigured = client } }
```

Open the inspector from the notification, from the launcher shortcut (long-press the app icon),
or in code with `startActivity(NetPilot.getLaunchIntent(context))`.

## Configuration

```kotlin
NetPilotInterceptor(
    context,
    NetPilotConfig(
        maxContentLength = 250_000,          // bytes kept per body (max 500 KB)
        redactHeaders = setOf("Authorization", "X-Api-Key"),
        maxRecords = 500,
        retentionPeriodMillis = TimeUnit.DAYS.toMillis(7),
        isNotificationEnabled = true,
        isLauncherShortcutEnabled = true,
    ),
)
```

`Authorization`, `Proxy-Authorization`, `Cookie` and `Set-Cookie` are redacted **by default**,
before anything is written to disk.

## Modules

| Module | Purpose |
|---|---|
| `netpilot` | Interceptor, SQLite storage, Compose inspector UI |
| `netpilot-no-op` | Same public API that does nothing; for release builds |
| `app` | Sample app with Retrofit and Ktor demo calls |

## Development

```bash
./gradlew :netpilot:testDebugUnitTest
./gradlew :app:assembleDebug
```
