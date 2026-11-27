# hn2

A Hacker News reader for Android, built around a home-screen widget. It's a modern take on
[Materialistic](https://github.com/hidroh/materialistic), which stopped being maintained in 2019
(see [docs/research.md](docs/research.md)).

## What it does

- **Widget:** a resizable list of stories, either Top, Best, New, Ask, Show or Jobs. Each row shows
  the title and `points - comments`, and both turn orange when a story is hot. A refresh button and
  the last-updated time sit in the header. The widget refreshes every 30 minutes on its own.
  Long-press the widget to change its section or theme (dark, transparent or light).
- **Story screen:** opens on the article. The **ARTICLE** tab is a WebView that scrolls by itself,
  and the fullscreen button hides the header. The **N COMMENTS** tab shows threaded comments with
  colored depth bars. Tap a comment to collapse it. The arrow button jumps to the next top-level
  thread.
- **In-app list:** the launcher icon opens the same story lists with pull-to-refresh.
- **Links:** `news.ycombinator.com/item?id=…` links can open in the app.

It's read-only for now: no login, voting or replying yet.

## Stack

- Kotlin, with Jetpack Compose and Material 3 for the UI
- Jetpack Glance for the widget, with WorkManager handling refreshes
- OkHttp and kotlinx.serialization for networking
- Story lists come from the official [HN Firebase API](https://github.com/HackerNews/API).
- Comment trees come from [Algolia](https://hn.algolia.com/api) in a single request. The app then
  re-sorts the top-level comments into HN's ranked order.

## Build

You need JDK 17 and the Android SDK (platform 35).

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Set `sdk.dir` in `local.properties`, or set `ANDROID_HOME`.
