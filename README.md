# NetPilot

In-app HTTP inspector for Android: see every OkHttp request your app makes, including Retrofit and Ktor's OkHttp engine. Later stages add the ability to control that traffic.

> Status: **stage 2**: capture, inspector UI and mock rules. Edit & retry, an MCP server
> for AI tools and breakpoints are planned next.

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

## Mock rules

Open NetPilot and tap the `{ }` icon. You can also open any captured request and choose
**Mock this response**, which prefills a rule from the real response.

A matching rule answers **without touching the network**. It can do one of three things:

- **Respond** with a custom status code, Content-Type, headers and body.
- **No internet**: throws `UnknownHostException`.
- **Timeout**: throws `SocketTimeoutException`.

Any rule can also add a delay first.

URL patterns:

| Pattern | Matches |
|---|---|
| `*/posts/*` | any URL with a `/posts/` segment |
| `/users/1` | URLs ending in `/users/1`; patterns without a scheme get an implicit leading `*` |
| `*/search?q=*` | the query is compared only when the pattern contains `?` |
| `posts/\d+` (regex on) | regex matched anywhere in the full URL |

Rules are checked by priority (highest first). A global switch turns all mocking off.
Mocked requests are tagged **MOCK** in the list, and the response carries an
`X-NetPilot-Mock: <rule name>` header.

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
