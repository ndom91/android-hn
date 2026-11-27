# Research: Materialistic (the original app)

## What it is

- **Materialistic – Hacker News**, by Ha Duy Trung (GitHub `hidroh`). Package ID `io.github.hidroh.materialistic`.
- A Material Design Hacker News reader for phones and tablets. Free, no ads, open source.
- Source: https://github.com/hidroh/materialistic, **Apache-2.0**. About 2.3k stars and 430+ open issues. Not archived, but dormant.
- **Status:** the last Play Store release was v3.3 (March 2019). Google unpublished the app from the Play Store in Feb 2022. F-Droid still ships the old build. The last real commits are from 2021, plus one "Spring cleaning" commit in April 2023 that bumped it to compileSdk/targetSdk 33 with minSdk 21.

## Features worth keeping

- **Story lists:** Top, New, Best, Show HN, Ask HN, Jobs, plus "Popular" (from Algolia, filtered by time range).
- **Item screen:** pages you swipe between, with a **Comments** tab and an **Article** tab. Single-pane on phones, list and detail side by side on tablets.
- **Comments:** threaded or single-page view, expanding and collapsing threads, "long comment" truncation, and navigating between top-level comments with the floating action button or the volume keys.
- **Article view:** an in-app WebView with an ad blocker, a "readability" mode, and PDF rendering through PDF.js.
- **Home-screen widget** (`appwidget/`): resizable, with settings for:
  - Section (top, best, new, and so on)
  - An optional search-query filter
  - Theme
  - Update frequency, with refresh handled by a JobService
- **Offline:** downloads comments, articles and readability text, either on Wi-Fi only or always, with a progress notification.
- **Favorites** (saved stories), searchable, with export.
- **Viewed tracking:** stories are marked as read as you scroll past them.
- **Search** through Algolia.
- **Account:** log in, vote, comment, reply and submit. The app does this by POSTing forms to news.ycombinator.com, because the official API is read-only.
- **Customization:** themes (light, dark, black, and a "sepia"-style theme), automatic day/night switching, font and text size, and which screen opens by default.

## Tech stack (and why it is breaking)

- Java with a little Kotlin. Dagger 2, RxJava 1/2, Retrofit and OkHttp, Room, and View-based XML layouts.
- **APIs:**
  - Official HN Firebase API (`hacker-news.firebaseio.com/v0`). It still works but makes you fetch one item at a time.
  - Algolia HN Search (`hn.algolia.com/api/v1`). It still works and can return a whole comment tree in a single request.
  - **Mercury Web Parser (Postlight)** for readability. This service **was shut down in 2019**, so readability mode is broken.
- **Article scroll bug (likely root cause):** `fragment_web.xml` puts a `WebView` with `layout_height="wrap_content"` inside a `NestedScrollView`. This stopped working well once Chromium-based WebViews arrived. The WebView then has to lay out the entire page at full height, very long pages get clipped or stop scrolling, and memory use spikes. Modern apps let the WebView scroll by itself, with the height set to `match_parent`, and coordinate the toolbar through nested scrolling instead.
- Old Rx, Dagger and support-library code and View-based theming explain the UI glitches users report, such as "elements randomly switching themes".

## Landscape

- **Harmonic** (SimonHalvdansson/Harmonic-HN): Java, Apache-2.0, actively maintained, and the most popular HN client today. It has no home-screen widget, which is Materialistic's signature feature.
- **ddxv/hackernews-app**: a small widget-focused app.
- So there is room for a **modern, widget-first client with Materialistic's UX**.

## Implications for our rewrite

- Write it from scratch rather than forking. The old codebase is the problem. We can still borrow ideas, and code if we want, under Apache-2.0 with attribution.
- Suggested stack:
  - Kotlin, Jetpack Compose with Material 3 (including dynamic color)
  - **Jetpack Glance** for the widget
  - WorkManager for widget refresh and offline downloads
  - Room, Ktor or Retrofit with kotlinx.serialization
  - Hilt or plain manual DI
- Data:
  - Use the Firebase API for the ranked story ID lists.
  - Use **Algolia `items/{id}`** to load the full comment tree in one request. This fixes the slow comment loading in the old app.
- Article view:
  - Open in Custom Tabs by default, or in an in-app WebView that scrolls on its own (not nested).
  - Optionally add an on-device readability parser, such as a port of Readability.js or a Kotlin library, instead of a hosted service.
- Login, voting and commenting: same approach as before (HN web forms plus the session cookie). This can wait until after the MVP.
