# AGENTS.md

Notes for working on hn2, for people and coding agents alike. What the app does is in
[README.md](README.md); background research on Materialistic is in
[docs/research.md](docs/research.md).

## Stack

- Kotlin 2.1, AGP 8.7, Gradle 8.11 (wrapper), JDK 17
- `compileSdk`/`targetSdk` 35, `minSdk` 26
- Jetpack Compose + Material 3 for the app UI, with dynamic color on Android 12+
- Jetpack Glance for the widget, WorkManager for its refreshes
- OkHttp + kotlinx.serialization for networking, coroutines throughout
- JUnit 4 for unit tests

Versions live in [gradle/libs.versions.toml](gradle/libs.versions.toml).

## Build and test

You need JDK 17 and the Android SDK with platform 35. Point Gradle at the SDK with `sdk.dir` in
`local.properties` (gitignored) or `ANDROID_HOME`.

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Project layout

All code is in the single `:app` module, under `app/src/main/java/app/hn2/`:

| Package | What's there |
|---|---|
| `Hn2App.kt` | Application class. Builds the shared OkHttp client and `HnRepository`; `Context.repository` gets it. |
| `data/` | `HnApi` (Firebase + Algolia), `HnRepository` (fetching, in-memory story cache, comment tree), models and `Section`. |
| `ui/list/` | `MainActivity` and the story list screen with section chips. |
| `ui/item/` | `ItemActivity`: the story screen with its collapsing header, the Comments/Article pager, the article WebView and the comments list. |
| `ui/common/`, `ui/theme/` | Shared composables (`Pill`, `ErrorMessage`, `groupedShape`) and the Material You theme. |
| `widget/` | The Glance widget, its stored state, refresh worker, refresh action and configuration activity. |
| `util/` | Pure helpers: formatting, HN HTML parsing, `runSuspendCatching`. |

Unit tests mirror this layout under `app/src/test/`. Keep logic that can be tested (tree building,
row flattening, HTML parsing, formatting) out of composables so it stays testable on the JVM.

## Data sources

- Story ID lists and items come from the [HN Firebase API](https://github.com/HackerNews/API). A
  list loads the first 30 items in parallel and drops dead and deleted ones.
- Comment trees come from Algolia's `items/{id}` in one request. Algolia returns comments
  oldest-first, so `buildCommentTree` re-sorts the top level by the story's `kids`, which are in
  HN's ranked order. Nested replies stay chronological. Deleted comments with no replies are pruned.

## Conventions and gotchas

- **The widget's look is final.** Don't change its layout, colors or text sizes without being asked.
  Its colors live in `widget/WidgetState.kt`, separate from the app theme.
- **Widget taps and `ItemActivity` share an extra name.** The widget passes the story ID as an
  action parameter whose key is `ItemActivity.EXTRA_ITEM_ID`. Change both together.
- **Never put the article WebView inside another scroll container.** It is `match_parent` and
  scrolls itself. Materialistic wrapped a `wrap_content` WebView in a `NestedScrollView`, which
  cut off long pages; that is the bug this app exists to fix.
- **Swiping off the article is custom.** A WebView eats every touch, so the pager has user scrolling
  turned off on the article page, and `Modifier.swipeFromArticle` (`ui/item/ArticleSwipe.kt`)
  catches mostly-horizontal drags first and drives the pager. Pages that can scroll sideways
  themselves keep the gesture.
- **Use `runSuspendCatching`, not `runCatching`, in coroutines.** Plain `runCatching` reports a
  cancelled job as an error.
- **`ItemActivity` handles its own configuration changes** so rotating doesn't reload the article.
- **Comment rows are flat.** `flattenComments` turns the tree into `CommentRow`s with depth, collapse
  state, which thread lines continue (`linesContinue`) and whether the row closes its thread card.
  The comments page only draws rows.
- Write plain, explicit Kotlin. Prefer small focused files and comments that explain why, not what.

## CI and releases

[.github/workflows/android.yml](.github/workflows/android.yml) runs on every push to `main`, on pull
requests and by hand. It builds the debug APK, runs the unit tests and uploads the APK as a workflow
artifact.

Every push to `main` that changes more than docs (`*.md`, `docs/`) is also a release. CI takes the newest `vX.Y.Z` tag, bumps the patch number
(0.1.0, 0.1.1, ...), writes that into `versionName` in `app/build.gradle.kts`, builds, and publishes
a GitHub release `vX.Y.Z` titled "hn2 X.Y.Z" with the APK as `hn2-debug.apk`, marked as the repo's
latest release. It then commits the new `versionName` back to `main` as "Release X.Y.Z [skip ci]",
so pull before pushing. The newest build is always at:

https://github.com/ndom91/android-hn/releases/latest/download/hn2-debug.apk

The README links there, so keep the asset name. Releases must be full releases, not pre-releases:
GitHub's `/releases/latest` skips pre-releases. To start a new minor or major version, set
`versionName` to it (e.g. `0.2.0`) and push; CI releases a `versionName` above the newest tag as is.

- `versionCode` is the workflow run number (`VERSION_CODE`), so every build installs as an update.
  Local builds use 1.
- **Every build must be signed with the same key**, or Android refuses to install it over an
  existing copy. CI restores a fixed debug keystore from the `DEBUG_KEYSTORE_BASE64` secret and
  passes it to Gradle through `HN2_DEBUG_KEYSTORE`. A CI step fails the run if the APK's signing
  certificate doesn't match the expected SHA-256.

## Workflow

- Commit straight to `main`; there's no PR process for this repo.
- Run `./gradlew assembleDebug testDebugUnitTest` before pushing.
- There's no instrumented or screenshot test suite yet. UI changes need checking on a device.
