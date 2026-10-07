# NetPilot

In-app HTTP inspector for Android: see every OkHttp request your app makes, including Retrofit and Ktor's OkHttp engine. Later stages add the ability to control that traffic.

> Status: **stage 3**: capture, inspector UI, mock rules, edit & retry, cURL and HAR export.
> An MCP server for AI tools and breakpoints are planned next.

## Setup

[![](https://jitpack.io/v/Javohir14022000/NetPilot.svg)](https://jitpack.io/#Javohir14022000/NetPilot)

Requirements: minSdk 24, compileSdk 34+, OkHttp 4.12+.

Add JitPack in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content { includeGroup("com.github.Javohir14022000.NetPilot") }
        }
    }
}
```

Then add the real library to debug builds and the empty no-op to release builds:

```kotlin
dependencies {
    debugImplementation("com.github.Javohir14022000.NetPilot:netpilot:0.3.0")
    releaseImplementation("com.github.Javohir14022000.NetPilot:netpilot-no-op:0.3.0")
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

## Retry, edit & retry

The **⋮** menu on any request offers two ways to send it again:

- **Retry** re-sends the request exactly as captured.
- **Edit & retry** lets you change the method, URL, headers and body before sending.

The new request shows up in the list like any other. Some details:

- Redacted headers such as `Authorization` are stored on disk as `██`. Their real values are
  kept **in memory only**, so a retry in the same app session still authenticates. After a
  restart the editor asks you to type them in.
- Retries go through NetPilot's own OkHttp client. Your app's other interceptors, such as
  token refresh or certificate pinning, are not applied. Mock rules still apply.

## Export

- **Copy as cURL** / **Share as cURL**: redacted headers stay redacted.
- **Share as text**: a readable dump of the request and response.
- **Share as HAR** (one request) or **Export as HAR** from the list's **⋮** menu (latest 100):
  HAR 1.2 files open in Chrome DevTools, Charles and Proxyman.

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
./gradlew publishToMavenLocal   # dev.mobile:netpilot:<version> for local testing in other apps
```

Releases: bump `netpilot.version` in `gradle.properties` and create a GitHub release whose
tag is that version (for example `0.3.0`). JitPack builds the tag on first request.

## License

```
Copyright 2026 Javohir Rahmatullayev

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
