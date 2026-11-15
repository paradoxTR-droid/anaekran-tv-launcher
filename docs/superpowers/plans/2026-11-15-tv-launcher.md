# Ana Ekran (TV launcher) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and install a zero-dependency, no-network Android TV home screen with a time-of-day intro animation and a 5-column app grid on the user's Google TV stick.

**Architecture:** One `HomeActivity` built from framework Views. Pure-Kotlin logic (greeting choice, app ordering, intro gating) lives in small files without Android imports, so plain JVM tests cover it. Android-facing code covers the package query, the banner loader, prefs, receivers and animation. It stays thin and is verified on the device over ADB.

**Tech Stack:** Kotlin (AGP 9.5.1 built-in Kotlin), Android framework only (no AndroidX), Gradle wrapper 9.8.2, JUnit 4 (test only).

**Spec:** `docs/superpowers/specs/2026-11-15-tv-launcher-design.md`

## Global Constraints

- Runtime dependencies: only `kotlin-stdlib`. No AndroidX, no third-party libraries. `testImplementation("junit:junit:4.13.2")` is allowed because it does not ship.
- Manifest declares **no** `<uses-permission>` at all. In particular, no `INTERNET`, `QUERY_ALL_PACKAGES` or `RECEIVE_BOOT_COMPLETED`. No services, no accessibility service.
- `minSdk 34`, `compileSdk 36`, `targetSdk 36`. Build-tools 36.0.0. JDK 17.
- Package / applicationId: `dev.anaekran`. App label: `Ana Ekran`.
- Device: ADB serial `<NEXT_STICK_IP>:5555`, 1920x1080, density 320. Run every adb command with `export ANDROID_SERIAL=<NEXT_STICK_IP>:5555`.
- Package visibility uses only `<queries><intent><action MAIN/><category LEANBACK_LAUNCHER/></intent></queries>`.
- No PackageManager or bitmap work on the main thread.
- All user-visible copy is Turkish, exactly as written in this plan.
- Settings intents:
  - Wi-Fi: `Settings.ACTION_WIFI_SETTINGS`.
  - Sound: `Settings.ACTION_SOUND_SETTINGS` with `setPackage("com.android.tv.settings")`. Without the package, the device shows a chooser between two handlers.
  - All settings: `Settings.ACTION_SETTINGS`.
  - Fall back to `ACTION_SETTINGS` on `ActivityNotFoundException`.
- Not a git repo yet: Task 1 runs `git init`. Commits are local only, never pushed.

## Review Focus

1. **A pinned or hidden app gets uninstalled.** Expect stale package names in prefs to be ignored, with no crash or ghost card. Test: `AppOrderTest.staleKeysIgnored` (Task 2).
2. **The user hides every app.** Expect an empty grid, the text "Tüm uygulamalar gizli. Ayarlar'a uzun basarak geri getirebilirsin.", and settings icons that stay focusable. Test: `AppOrderTest.allHiddenGivesEmpty` (Task 2) plus a device check (Task 5).
3. **An app is removed between list load and OK press.** Expect `ActivityNotFoundException` / `SecurityException` to be caught and the list to reload. Device check: `pm disable-user` an app while its card is on screen, then press OK (Task 3).
4. **`boot_count` is missing or unreadable (-1).** Expect the intro not to replay on every resume; only the screen-off flag drives it. Test: `IntroGateTest.unknownBootCountDoesNotLoop` (Task 6).
5. **Clock edge: hour 4 vs 5, and midnight.** Expect the slot to follow the spec table exactly. Test: `GreetingTest.slotBoundaries` (Task 1).

---

### Task 1: Project scaffold, greeting logic, installable empty home

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore` (`build/`, `.gradle/`, `local.properties`, `.superpowers/`), `local.properties` (`sdk.dir=~/Android/Sdk`)
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`
- Create: `app/src/main/java/dev/anaekran/Greeting.kt`, `app/src/main/java/dev/anaekran/HomeActivity.kt`
- Test: `app/src/test/java/dev/anaekran/GreetingTest.kt`

**Interfaces:**
- Produces: `enum class TimeSlot { SABAH, GUNDUZ, AKSAM, GECE }`; `fun slotFor(hour: Int): TimeSlot`; `fun pickGreeting(slot: TimeSlot, random: kotlin.random.Random): String`; `val GREETINGS: Map<TimeSlot, List<String>>`. `HomeActivity` is the launcher activity (`dev.anaekran/.HomeActivity`).

- [ ] **Step 1: Scaffold.** Run `git init`, then `gradle wrapper --gradle-version 9.8.2` in the repo root. Plugin: `com.android.application` version `9.5.1`, using built-in Kotlin with no separate kotlin plugin. If the build rejects built-in Kotlin, add `org.jetbrains.kotlin.android` at the version AGP's POM names (`2.2.10`).
  - Manifest: `HomeActivity` gets `exported=true`, `launchMode="singleTask"`, `screenOrientation="landscape"`, `stateNotNeeded="true"`. Its intent filter has `MAIN` with categories `HOME`, `DEFAULT` and `LEANBACK_LAUNCHER`.
  - Also in the manifest: `<uses-feature android:name="android.software.leanback" android:required="true"/>` and `android.hardware.touchscreen` with `required=false`.
  - Add the `<queries>` block from Global Constraints. No permissions.
  - Theme: `Theme.DeviceDefault.NoActionBar` with a black window background.

- [ ] **Step 2: Write failing test** `GreetingTest`:
```kotlin
@Test fun slotBoundaries() {
    assertEquals(TimeSlot.GECE, slotFor(4)); assertEquals(TimeSlot.SABAH, slotFor(5))
    assertEquals(TimeSlot.SABAH, slotFor(10)); assertEquals(TimeSlot.GUNDUZ, slotFor(11))
    assertEquals(TimeSlot.GUNDUZ, slotFor(16)); assertEquals(TimeSlot.AKSAM, slotFor(17))
    assertEquals(TimeSlot.AKSAM, slotFor(21)); assertEquals(TimeSlot.GECE, slotFor(22))
    assertEquals(TimeSlot.GECE, slotFor(0))
}
@Test fun everySlotHasThreeToFivePhrases() =
    TimeSlot.entries.forEach { assertTrue(GREETINGS.getValue(it).size in 3..5) }
@Test fun pickIsFromSlot() =
    repeat(50) { assertTrue(pickGreeting(TimeSlot.GECE, Random(it)) in GREETINGS.getValue(TimeSlot.GECE)) }
```

- [ ] **Step 3: Run** `./gradlew :app:testDebugUnitTest`. Expected: FAIL, because `slotFor` is unresolved.

- [ ] **Step 4: Implement `Greeting.kt`.** The first phrase of each slot is fixed by the spec; write the other 2–4 in the same warm, casual Turkish tone:
  - SABAH: "Günaydın, kahve hazır mı?"
  - GUNDUZ: "Öğleden sonra molası"
  - AKSAM: "Akşam keyfi"
  - GECE: "Gece sineması başlasın"

  `HomeActivity` for now only sets a black content view with a centered `TextView` showing `pickGreeting(slotFor(currentHour), Random.Default)`.

- [ ] **Step 5: Run tests.** Run `./gradlew :app:testDebugUnitTest`. Expected: PASS.

- [ ] **Step 6: Build and verify there are no permissions.** Run `./gradlew :app:assembleDebug && ~/Android/Sdk/build-tools/36.0.0/aapt dump permissions app/build/outputs/apk/debug/app-debug.apk`. Expected: only the `package: dev.anaekran` line, plus at most the auto-added `dev.anaekran.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No `uses-permission: android.permission.*` lines. Also check that the APK is under 1 MB.

- [ ] **Step 7: Install without making it home.** Run `adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n dev.anaekran/.HomeActivity`. Expected: a greeting appears on the TV (check with `adb exec-out screencap -p > s.png` in the scratchpad). Do **not** change the default home in this task.

- [ ] **Step 8: Commit.** Run `git add -A && git commit -m "feat: scaffold launcher with time-of-day greeting"`.

---

### Task 2: App ordering logic and prefs

**Files:**
- Create: `app/src/main/java/dev/anaekran/AppOrder.kt`, `app/src/main/java/dev/anaekran/Prefs.kt`
- Test: `app/src/test/java/dev/anaekran/AppOrderTest.kt`

**Interfaces:**
- Produces:
  - `data class AppEntry(val packageName: String, val activityName: String, val label: String)`
  - `fun arrange(apps: List<AppEntry>, pinned: List<String>, hidden: Set<String>, compare: Comparator<String>): List<AppEntry>`. Pinned apps come first in `pinned` order, then the rest sorted by `label` with `compare`. Hidden apps are removed. Unknown keys are ignored.
  - `fun pinFirst(pinned: List<String>, pkg: String): List<String>`. This puts `pkg` at index 0 and removes it from anywhere else.
  - `class Prefs(context: Context)` with `var pinned: List<String>`, `var hidden: Set<String>`, `var introPending: Boolean`, `var lastBootCount: Int`. Backed by `SharedPreferences("anaekran")`. `pinned` is stored as a single string joined with `\n`.

- [ ] **Step 1: Write failing tests** in `AppOrderTest`. Use `java.text.Collator.getInstance(Locale("tr"))` as the comparator.
  - `alphabeticalTurkish`: labels `["Zap","Çay","Cam","Ödev","Oyun"]` sort as `["Cam","Çay","Oyun","Ödev","Zap"]`.
  - `pinnedFirstInPinnedOrder`: `pinned=["b","a"]` gives b, a, then the rest alphabetically.
  - `hiddenRemoved`: an app in `hidden` does not appear in the result.
  - `staleKeysIgnored`: `pinned=["gone"]` and `hidden=setOf("gone2")` produce the same result as empty pins and hides.
  - `allHiddenGivesEmpty`: hiding every package returns `emptyList()`.
  - `pinFirstMovesExisting`: `pinFirst(listOf("a","b","c"),"c") == listOf("c","a","b")`.

- [ ] **Step 2: Run tests.** Run `./gradlew :app:testDebugUnitTest --tests '*AppOrderTest*'`. Expected: FAIL (unresolved).

- [ ] **Step 3: Implement** `AppOrder.kt` (pure, no Android imports) and `Prefs.kt`.

- [ ] **Step 4: Run tests.** Same command. Expected: PASS.

- [ ] **Step 5: Commit.** Message: `feat: app ordering with pin/hide prefs`.

---

### Task 3: App grid with banners, focus and launch

**Files:**
- Create: `app/src/main/java/dev/anaekran/AppRepository.kt`, `app/src/main/java/dev/anaekran/BannerLoader.kt`, `app/src/main/java/dev/anaekran/AppGridAdapter.kt`
- Create: `app/src/main/res/layout/activity_home.xml`, `app/src/main/res/layout/item_app.xml`, `app/src/main/res/drawable/bg_home.xml` (radial gradient `#1a2236` → `#07080c`), `app/src/main/res/drawable/card_focus.xml` (2dp white stroke, 6dp corners)
- Modify: `HomeActivity.kt`

**Interfaces:**
- Consumes: `AppEntry`, `arrange`, `Prefs` (Task 2).
- Produces:
  - `fun loadApps(pm: PackageManager, selfPackage: String): List<AppEntry>`. This queries `Intent(ACTION_MAIN).addCategory(CATEGORY_LEANBACK_LAUNCHER)` and excludes `selfPackage`. Call it off the main thread.
  - `class BannerLoader(pm: PackageManager)` with `fun load(entry: AppEntry, into: ImageView, fallback: (Drawable?) -> Unit)`. It uses a single-thread `Executor` and an `android.util.LruCache<String, Drawable>` with 40 entries. Order: activity banner, then app banner, then `null`. On `null` it calls `fallback(icon)`. It ignores results for views that were recycled to another entry (tag check).
  - In `HomeActivity`: `fun reloadApps()`, which loads on the background executor, calls `arrange`, then posts the result to the adapter.

- [ ] **Step 1: Layout.**
  - `activity_home.xml`: root `FrameLayout` with `bg_home`. Inside it:
    - A top bar with a header `TextView` (id `header`) on the left, plus clock and icons on the right (added in Task 4).
    - A `GridView` (id `grid`) with `numColumns=5`, 48dp side padding, 24dp spacing, `clipToPadding=false` and `clipChildren=false`.
    - An empty-state `TextView` (id `empty`, gone by default) with the text from Review Focus #2.
  - `item_app.xml`: a 16:9 card holding an `ImageView` (banner) and a fallback layer (icon plus label on a solid color).

- [ ] **Step 2: Implement** the repository, the loader and the adapter.
  - Fallback card color is a hash of `packageName` modulo a palette of 8 dark colors: `#b20710 #0b2a8c #5a189a #00796b #ff6a00 #1f6feb #2b2d42 #8d6e00`.
  - Focus: on focus gain, animate the card to `scaleX/Y 1.08`, elevation 12dp and the `card_focus` foreground over 150 ms. On focus loss, revert.
  - OK launches `Intent(MAIN).setClassName(pkg, activity).addFlags(FLAG_ACTIVITY_NEW_TASK)`. Catch `ActivityNotFoundException` and `SecurityException`, then call `reloadApps()`.
  - Call `reloadApps()` from `onResume`.

- [ ] **Step 3: Device check.** Install and start (`am start -n dev.anaekran/.HomeActivity`), then take a screenshot. Expected:
  - All TV apps (Netflix, YouTube, Disney+, HBO Max, Prime Video, Exxen, tabii, TOD, Tivibu, TV+, puhutv, MUBI, Kick, iQIYI, YouTube Music, …) appear as banners in 5 columns.
  - D-pad moves the focus and the focused card scales up.
  - OK on YouTube opens YouTube.

- [ ] **Step 4: Review Focus #3 check.** Focus the Kick card, run `adb shell pm disable-user --user 0 com.kick.mobile`, then press OK (`input keyevent KEYCODE_DPAD_CENTER`). Expected: no crash (`logcat -d | grep -c "FATAL EXCEPTION"` returns 0) and Kick disappears from the grid. Then run `pm enable com.kick.mobile`.

- [ ] **Step 5: Commit.** Message: `feat: app grid with banners, focus and launch`.

---

### Task 4: Top bar: clock, settings icons, live header

**Files:**
- Create: `app/src/main/res/drawable/ic_wifi.xml`, `ic_sound.xml`, `ic_settings.xml` (vector, white, 24dp, simple paths drawn by hand)
- Modify: `activity_home.xml`, `HomeActivity.kt`

**Interfaces:**
- Consumes: `slotFor`, `pickGreeting` (Task 1).
- Produces: `fun openSettings(action: String, pkg: String? = null)` in `HomeActivity`.

- [ ] **Step 1: Implement the top bar.**
  - Right side: clock `TextView` (`HH:mm`, 24h), then three focusable `ImageButton`s for Wi-Fi, Sound and Settings, using the Global Constraints intents.
  - Clock: register `ACTION_TIME_TICK` dynamically in `onStart` and unregister in `onStop`. On each tick, update the clock. If `slotFor(hour)` differs from the slot the current header was picked for, replace the header with a new `pickGreeting` (no animation).
  - Focus: pressing up from any first-row grid item moves focus to the Wi-Fi icon. Pressing down from the icons returns focus to grid position 0.

- [ ] **Step 2: Device check.**
  - Each icon opens its screen. Verify with `dumpsys activity activities | grep topResumedActivity`: `NetworkActivity`, `DisplaySoundActivity` (no chooser), `MainSettings`.
  - The clock shows the current time and updates within a minute. Slot-change logic is `slotFor`, already covered by the Task 1 tests.

- [ ] **Step 3: Commit.** Message: `feat: top bar with clock, settings shortcuts, live greeting`.

---

### Task 5: Long-press menu: pin, hide, restore hidden

**Files:**
- Modify: `HomeActivity.kt`, `AppGridAdapter.kt`, `strings.xml`

**Interfaces:**
- Consumes: `Prefs.pinned`, `Prefs.hidden`, `pinFirst`, `reloadApps()`.

- [ ] **Step 1: Implement the menus.**
  - Long press on a card opens a framework `AlertDialog` (theme `Theme.DeviceDefault.Dialog.Alert`) titled with the app label, with the items "Başa taşı" and "Gizle".
  - "Başa taşı" sets `prefs.pinned = pinFirst(prefs.pinned, pkg)`. "Gizle" sets `prefs.hidden = prefs.hidden + pkg`. Both then call `reloadApps()`.
  - Long press on the Settings icon opens a dialog titled "Gizli uygulamalar". It lists hidden labels (`pm.getApplicationLabel`, skipping packages that no longer exist). Selecting one removes it from `hidden` and reloads. With nothing hidden, show "Gizli uygulama yok".
  - When `arrange` returns empty, show the `empty` text and move focus to the Wi-Fi icon.

- [ ] **Step 2: Device check.**
  - Long-press OK (`input keyevent --longpress KEYCODE_DPAD_CENTER`) on MUBI, then pick "Başa taşı". Expected: MUBI becomes the first card.
  - Hide Kick. Expected: it is gone.
  - `am force-stop dev.anaekran` and restart. Expected: the order and the hide are kept.
  - Restore Kick from the Settings long-press menu.

- [ ] **Step 3: Commit.** Message: `feat: pin, hide and restore apps`.

---

### Task 6: Intro gating and intro animation

**Files:**
- Create: `app/src/main/java/dev/anaekran/IntroGate.kt`, `app/src/main/java/dev/anaekran/IntroAnimator.kt`
- Test: `app/src/test/java/dev/anaekran/IntroGateTest.kt`
- Modify: `HomeActivity.kt`, `Prefs.kt` (implement `IntroState`)

**Interfaces:**
- Consumes: `Prefs.introPending`, `Prefs.lastBootCount`.
- Produces:
  - `interface IntroState { var introPending: Boolean; var lastBootCount: Int }`. `Prefs` implements it.
  - `class IntroGate(state: IntroState)` with:
    - `fun onScreenOff()`, which sets `introPending = true`.
    - `fun shouldPlay(bootCount: Int): Boolean`. Returns true if `introPending`, or if `bootCount >= 0 && bootCount != lastBootCount`.
    - `fun markPlayed(bootCount: Int)`, which sets `introPending = false` and `lastBootCount = bootCount` when `bootCount >= 0`.
  - `class IntroAnimator(header: TextView, grid: GridView, overlayTitle: TextView)` with `fun play(text: String, onEnd: () -> Unit)`, `fun skip()` and `val isRunning: Boolean`.

- [ ] **Step 1: Write failing tests** in `IntroGateTest`, using a fake `IntroState` with `lastBootCount=-1` and `introPending=false` initially:
  - `firstEverRunPlays`: `shouldPlay(461)` is true.
  - `sameBootNoPending`: after `markPlayed(461)`, `shouldPlay(461)` is false.
  - `newBootPlays`: after `markPlayed(461)`, `shouldPlay(462)` is true.
  - `screenOffPlays`: after `markPlayed(461)` and `onScreenOff()`, `shouldPlay(461)` is true; after another `markPlayed(461)` it is false.
  - `unknownBootCountDoesNotLoop`: after `markPlayed(-1)`, `shouldPlay(-1)` is false; after `onScreenOff()` it is true.

- [ ] **Step 2: Run tests.** Run `./gradlew :app:testDebugUnitTest --tests '*IntroGateTest*'`. Expected: FAIL.

- [ ] **Step 3: Implement `IntroGate`**, then `IntroAnimator`. Timeline (total ≤ 2.0 s):
  - 0–350 ms: the overlay title, centered at 48sp, goes from alpha 0 and scale 1.25 to alpha 1 and scale 1.0.
  - 700–1100 ms: the title translates and scales into the `header` position, using deltas from `getLocationOnScreen`.
  - From 1100 ms: grid children rise and fade in (translationY 60dp to 0, alpha 0 to 1), each child delayed by `index * 80 ms`, capped at index 14, each 300 ms long with `DecelerateInterpolator`.
  - At the end, hide the overlay and make the header visible with the same text.

  `skip()` cancels all animators and snaps to the end state.

- [ ] **Step 4: Wire into `HomeActivity`.**
  - In `onResume`, read the boot count with `Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)`. If `gate.shouldPlay(bc)`, play the intro with a fresh greeting, then call `markPlayed(bc)`. Otherwise show the grid immediately and focus position 0.
  - Register `ACTION_SCREEN_OFF` dynamically in `onCreate` and unregister in `onDestroy`. Its handler calls `gate.onScreenOff()`.
  - In `dispatchKeyEvent`, any key while `isRunning` calls `skip()` and consumes the event.

- [ ] **Step 5: Run unit tests and check on the device.**
  - `./gradlew :app:testDebugUnitTest`: PASS.
  - Device: `am force-stop dev.anaekran; am start -n dev.anaekran/.HomeActivity`. Expected: no intro (same boot, no pending).
  - `input keyevent KEYCODE_SLEEP`, wait 3 s, `input keyevent KEYCODE_WAKEUP`, then `input keyevent KEYCODE_HOME`. Expected: the intro plays (`screenrecord --time-limit 5 /sdcard/i.mp4`, or 2 screenshots 0.5 s apart).
  - A key press during the intro snaps to the grid.

- [ ] **Step 6: Commit.** Message: `feat: intro animation on boot and wake`.

---

### Task 7: Live package updates, key handling, go live

**Files:**
- Modify: `HomeActivity.kt`
- Create: `README.md` (build, install, set home, and rollback commands copied from the spec's "Kurulum ve geçiş" section)

- [ ] **Step 1: Implement.**
  - Package updates: dynamic receiver for `ACTION_PACKAGE_ADDED`, `ACTION_PACKAGE_REMOVED` and `ACTION_PACKAGE_CHANGED`, with data scheme `package`, registered with `RECEIVER_NOT_EXPORTED`. If system broadcasts do not arrive, switch to `RECEIVER_EXPORTED` and document the reason in a one-line comment. Its handler calls `reloadApps()`.
  - Back key: no-op on the home screen.
  - Home key: `onNewIntent` with `ACTION_MAIN` + `CATEGORY_HOME` scrolls the grid to the top and focuses position 0, without the intro.

- [ ] **Step 2: Device check, apps.**
  - `pm disable-user --user 0 com.mubi` while the launcher is visible. Expected: MUBI disappears within 2 s.
  - `pm enable com.mubi`. Expected: it reappears.

- [ ] **Step 3: Make it home.** Run `adb shell cmd package set-home-activity dev.anaekran/.HomeActivity`, then press Home. Expected: `topResumedActivity` is `dev.anaekran/.HomeActivity`.

- [ ] **Step 4: Measure.**
  - Open YouTube, then `am kill dev.anaekran`, then `am start -W -n dev.anaekran/.HomeActivity`. Record `TotalTime`.
  - Repeat with `-n com.spocky.projengmenu/.ui.home.MainActivity`, killing it first with `am kill com.spocky.projengmenu`.
  - Run `dumpsys meminfo dev.anaekran | grep "TOTAL PSS"`.
  - Expected: our `TotalTime` is below Projectivy's, and PSS is below 90 MB. Report all three numbers.

- [ ] **Step 5: Re-verify security on the final APK.** Run `aapt dump permissions` (see Task 1, Step 6). Also run `aapt dump xmltree app-debug.apk AndroidManifest.xml | grep -iE "service|accessibility"`. Expected: empty.

- [ ] **Step 6: Commit.** Message: `feat: live package updates, home/back handling, README`.

- [ ] **Step 7: Hand back to the user.** Uninstalling Projectivy is **not** part of this plan. Ask the user first, since it is hard to reverse (it would need a Play Store reinstall).
