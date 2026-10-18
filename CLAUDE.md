# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## How to work on this project

This is the owner's personal project, and they vibe code it. Deliver complete, working solutions with as little back-and-forth as possible.

- **Look before changing:** read the relevant code, `gradle/libs.versions.toml` and `AndroidManifest.xml` first. Don't assume the structure matches older notes, this file included; if they disagree, the code wins.
- **Decide routine things yourself.** Ask only when you're blocked, or before a risky or irreversible action.
- **Discussed means approved:** once the owner has discussed, brainstormed or agreed to a change (in this conversation or an earlier one), carry it all the way through without asking again: implement, commit, push, and once the checks pass, merge it into `main` and push. Don't stop to confirm each step. Still ask first before publishing a release (`v*` tag), before anything that wasn't discussed and is risky or irreversible, and before any vehicle write command (see "Vehicle safety").
- **Preserve what works:** don't regress existing features, and never discard, overwrite or commit the owner's unrelated uncommitted changes.
- **Edit sources, not generated output:** never hand-edit `app/build/`, `build/`, `.gradle/`, `.kotlin/` or KSP output. The project has no Capacitor or `node_modules`, so don't add web-wrapper layers.
- **Android versions:** gate permissions and APIs by SDK level (minSdk 24). Bluetooth is `BLUETOOTH`/`BLUETOOTH_ADMIN` below API 31 and `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN` from API 31. Location is needed for scans on older versions. `POST_NOTIFICATIONS` applies from API 33, and foreground-service types from API 34. Handle denied permissions, a disabled adapter and missing hardware with a visible error, not a crash.
- **OBD diagnosis:** name the failing layer and don't blur them together:
  1. Bluetooth Classic (SPP/RFCOMM), BLE (GATT), or the Wi-Fi transport;
  2. adapter connection (socket or GATT link up);
  3. ELM327 initialization (`AT` commands, `>` prompt, protocol detect);
  4. ECU response (`NO DATA`, `?`, CAN errors, headers);
  5. PID decoding (parser and formulas).

  The owner's adapters are a Veepeak OBDCheck BLE and an OBDAi Gen-1 (likely Classic). The OBDAi connects on the head unit in Torque Pro and Car Scanner Pro, so the head unit supports Classic SPP. If our Classic connect fails there, treat it as an app bug; don't blame the hardware.
- **Vehicle safety:** never clear DTCs, change ECU settings or send any vehicle write command (Mode 04, Mode 08, writes to Mode 2E/2F/31/3B, etc.) unless the owner explicitly asks for that operation. The existing user-initiated `ObdManager.clearCodes()` stays behind its explicit UI action. Never call it automatically, from the assistant, or during testing, and don't add new write paths unasked.
- **Verify before calling it done:** after implementing, run `testDebugUnitTest` (plus any new tests), `lintDebug` and `assembleDebug`, locally or on GitHub Actions (see "Build / test commands"). Fix failures and rerun until they pass. A successful build or passing tests is not proof of a live adapter, ECU or head-unit behavior, so never claim otherwise.
- **Commit without asking:** commit your own changes. Stage only the files you changed, never the owner's unrelated uncommitted changes. Pushing a `claude/` work branch so CI can build it is fine without asking; merge to `main` once checks pass if the change was discussed; create `v*` tags only when asked.
- **Final report:** end with:
  - what changed;
  - the actual verification results (commands run or the CI run URL, pass/fail);
  - the APK if one was built (local path, plus the CI run page in CI mode);
  - only the specific on-device test the owner needs to do.

## What this is

A Knight Rider (KITT) themed Android **home-screen launcher** for aftermarket car head units, primarily a 12.1" portrait (9:16) Tesla-style unit. It includes:
- an ELM327 OBD-II scanner (Car Scanner-style gauges, codes and custom PIDs);
- a voice/text AI copilot ("KITT") that sees live vehicle data;
- an always-on red scanner light.

Single `:app` module, Jetpack Compose, package `com.aiconsole.knightriderlauncher`.

## Build / test commands (Windows, PowerShell)

There are two build modes. Pick **local** if `C:\Program Files\Android\Android Studio\jbr` exists, otherwise **CI**. The owner's current laptop (4 GB RAM, Intel N150) has no Android Studio, JDK or SDK and can't run them well, so CI is the normal mode for now. Don't install the Android toolchain on it.

**CI mode (GitHub Actions):** commit on a `claude/<short-topic>` branch cut from `origin/main`, push it, and start the workflow by hand, since branch pushes don't trigger it:

```powershell
git push -u origin claude/my-change
gh workflow run android.yml --ref claude/my-change
gh run list --workflow android.yml --branch claude/my-change --limit 1   # get the run id
gh run watch <run-id> --exit-status                                       # several minutes; run in the background
gh run view <run-id> --log-failed                                         # on failure: test/lint/compile output
gh run download <run-id> -n app-debug -D "$env:USERPROFILE\Downloads\KITT-<run-id>"   # the debug APK
```

The owner installs the APK on the head unit, either from that download or from the run's page on GitHub (artifact `app-debug`, a zip). Instrumented tests can't run in CI. Without `gh` (e.g. a cloud session), push the branch and open a pull request instead; the workflow runs on pull requests. The `/android-finish` skill walks through the whole loop.

**Local mode:** Gradle needs a JDK; use Android Studio's bundled JBR:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat testDebugUnitTest --tests "com.aiconsole.knightriderlauncher.ObdProtocolTest"   # single class
.\gradlew.bat lintDebug    # text report: app/build/intermediates/lint_intermediate_text_report/
```

CI: `.github/workflows/android.yml` runs tests, lint and the debug build; with signing secrets (`KITT_KEYSTORE_BASE64` etc.) it also builds a signed release, published on `v*` tags for the in-app updater. CI sets `versionCode` from the run number.

Toolchain: AGP 9.x with **built-in Kotlin** (no `kotlin-android` plugin), Compose BOM, KSP 2.3.x (decoupled from Kotlin version) for Room, OkHttp 5. Versions live in `gradle/libs.versions.toml`. minSdk 24, target/compile 37.

Don't rewrite source files with PowerShell `Get-Content`/`Set-Content`. Windows PowerShell 5.1 reads them as ANSI and corrupts `°`, `·`, `…`. Use the editor tools.

## Architecture

**Service locator:** `KittApplication` holds lazy app-wide singletons:
- `settings`
- `tripDatabase`
- `tripRecorder`
- `gauges`
- `customPids`
- `obd`
- `kittVoice` (the single shared TTS engine)
- `alerts`
- `assistantRepository`
- `assistantInbox` (questions queued for KITT from other screens)

There's no DI framework. Composables that need them do `LocalContext.current.applicationContext as KittApplication`.

**UI shell** (`MainActivity.kt`):
- **`LauncherRoot`:** a permanent `KittScanner` strip (doubles as a shift light; 12–22 dp by screen height), then a `BoxWithConstraints` holding a `HorizontalPager` of `HomePage`s.
  - **Portrait (the owner's 12.1" unit):** page tabs and the stacked `ControlDock`.
  - **Landscape (`DeviceProfile.useRail`):** `NavRail` (KITT + page buttons) on the left, pages, and the one-row `DockBar` (in `ControlDock.kt`) below. Shared with KITT Launcher.2 (`../KITTLauncher2`, the 7"–10" landscape fork, package `com.aiconsole.kittlauncher2`); keep improvements in both.
  - **Screen size (`UiScale`):** `MainActivity` overrides `LocalDensity` by `DeviceProfile.scale`; Auto changes only landscape screens, portrait keeps its density.
  - **Lite mode (`LiteMode`, `KittApplication.lite`, `LocalLiteMode`):** Auto from `lowEndDevice` (≤ 3 GB RAM, ≤ 4 cores or the low-RAM flag; the owner's 8 GB / 8-core unit runs Full). Lite caps the scanner and overlay at 30 fps, freezes idle animations, drops the Matrix rain, keeps fewer WebViews and skips off-screen pre-raster. New animations must stand still when idle and respect `LocalLiteMode`.
  - **Drive:** tall screens (height ≥ 1.2× width) stack map 50% / grid 35%; wide screens put them side by side. The grid shows pinned favourites, else all non-hidden apps (`AppLists`).
  - **Gauges / Media / Apps:** `GaugeWallPane`, `MediaPane` (active `MediaSession` via `MediaBridge`, needs notification access), `AppDrawerPane` (search, A–Z rail, pin/hide).
  - **Browser (`browser/`, `ui/browser/BrowserView`):** one tabbed WebView browser behind the stream window and the AI and Games pages. Each `BrowserSession` has its own WebView profile (sandboxed sign-ins) where `MULTI_PROFILE` is supported.
    - Per-site options (`SiteSettings`): desktop/mobile, dark pages, ad blocking (`AdBlock`, never on YouTube), text size.
    - Pop-ups the driver triggers open as tabs. Non-web schemes are dropped so apps can't take over the screen.
    - Memory (`DeviceProfile.liveWebViews`, `WebMedia.enforceBudget`): pages pause (`WebView.onPause`) when hidden and silent, and while the launcher is behind another app; least recently used hidden tabs are discarded beyond the budget and reload from their URL when shown; `onTrimMemory` frees every hidden page. Visible and playing tabs are never discarded.
    - Hung or crashed pages (`RendererWatch`, `onRenderProcessGone`): WebView draws in lock-step with the page renderer, so a hung page (e.g. a long Claude chat on the AI page) used to freeze the launcher until Android fell back to the stock launcher. An unresponsive renderer is terminated from a background executor; every WebView on a dead renderer is destroyed and its tab shows a "page stopped" card (`BrowserTab.gone`, no automatic reload loop). Off-screen pre-raster is stream-only. Console errors are rate-limited (they arrive on the main thread).
    - `FreezeWatchdog` records the main-thread stack (plus the last page shown) into the crash files whenever the UI thread stalls 4 s+, so Report a problem shows the cause of a freeze.
    - Background sound (`WebMedia`): WebViews attach only on the settled pager page (`LocalWebViewHostActive`); a playing page leaving the screen is parked in a 1×1 container so it keeps playing. Pages report playback through the `KittMedia` JS bridge (`PageScripts.MEDIA_WATCH`); one page plays at a time, and it holds audio focus (`MediaFocus`: loss pauses, transient loss pauses until regained, ducking does nothing).
    - Userscripts (`stream/Userscripts`, `UserscriptStore`) inject at document start. `.user.js` links and downloads prompt to install.
    - `PageScripts` holds the toolbar's JS (close pop-ups, scroll, find install link).
    - Stream window: site picker built-ins are licensed services only (`StreamSites`; don't add piracy sites). Video plays while moving by the owner's choice; don't add a driving gate.
    - AI page (`AiSites`): the driver's own accounts; voice prompts typed into every open AI tab. Games page: controller input remapped to emulator keys (`Gamepad`); `GameSites` are legal sources only.
  - **Swipe:** web pages and the map keep drags that start inside them; drags from the 48 dp edge strips (`edgeSwipe`, `EdgeSwipe`) always change page. Home returns to Drive.
  - **Map directions (`nav/`):** `NavServices` (Photon search, OSRM routing with a fallback server, no key), `RouteRules` (pure: polyline, progress, wording, prompt timing; unit-tested), `RouteNavigator` (state, GPS follow, spoken prompts, reroute) as `KittApplication.navigator`; UI in `ui/panes/RoutePanels.kt`. Voice: "navigate to …" / "cancel the route".
  - **More:** `DrivingPanel` (driving layout while moving), `QuickSettingsPanel` (tap the status strip), `WeatherService` (Open-Meteo + spoken road warnings), messages read aloud via `NavNotificationListener` + `MessageRules`, `DriverProfiles` (personal vs shared keys in `ProfileRules`), `SelfUpdate` (GitHub releases, `PackageInstaller`).
  - **Extras:** `StatusStrip` (clock/heading/radios/OBD/volts), GPS speed fallback (`GpsTracker`), `Screensaver` when parked and idle, `WidgetsPane` (`AppWidgetHost` in `KittApplication`), `StartupRoutine` (on start or wake after 5+ min), `HardwareKeys` button mapping via `onKeyUp`, `LauncherBackup` (API keys excluded on purpose).
- **Overlays:** full-screen features are selected by the `LauncherOverlay` enum and drawn inside that box, below the scanner. The scanner is always visible. Each overlay swallows taps and registers its own `BackHandler`.
  - `Assistant`
  - `Diagnostics`
  - `Config`
  - `Trips`
- **Live dash state:** `rememberDashState` collects the OBD, gauge and alert flows into `DashState`.
- **No recreation on rotation:** the manifest `configChanges` means the activity isn't recreated, and some state relies on that.
- **Voice button:** `VOICE_COMMAND`/`ASSIST` intents (steering-wheel voice button) bump `voiceTrigger`, which opens the assistant with `autoListen`.
- **Services start from the activity:** foreground services must start while the activity is visible, so `MainActivity.onStart` starts the OBD service and the scanner overlay, and triggers the greeting.

**OBD (`obd/`)**, layered bottom-up:
- **Transports:** `ObdTransport` implementations (Classic SPP with an RFCOMM channel-1 fallback, BLE GATT with a notify/write characteristic search, Wi-Fi TCP) all push bytes into a `Channel`.
- **`Elm327`:** commands end at the `>` prompt. Use `exclusive { }` to group a header switch plus request so the poll loop and on-demand calls never interleave.
- **`ObdResponseParser`** (pure Kotlin): ISO-TP multi-frame, multi-PID splitting, DTC count byte on CAN only, VIN.
- **`ObdManager`:** session and reconnect loop.
  - **Polling:**
    - demand-driven: only PIDs registered via `watch(key, ids)` plus `ALWAYS_POLLED`;
    - tiered: `fast` PIDs every cycle;
    - CAN multi-PID batching (6 per request), falling back to single requests automatically;
    - ELM response-count suffix (`010C1`), disabled on `?`.
  - **Headers:** CAN polling targets the engine header `7E0`; code reads switch to broadcast `7DF`.
  - **Units and logging:** values are stored in **metric base units** and converted with `Quantity.toDisplay`. Readings feed `TripMetricRecorder` at 1 Hz.
- **`ObdConnectionService`:** just holds the foreground lifetime (type `connectedDevice`).
- **Demo mode (`DemoSession`, debug builds only):** `am start -n com.aiconsole.knightriderlauncher/.MainActivity --ez kitt.demo true` feeds simulated readings, a sample stored code and one past trip, labelled "DEMO (simulated)", for screenshots. Nothing is sent to an adapter; a real connect or disconnect ends it.
- **Diagnostics tools** (all read-only): `ReadinessDecoder`, `Mode06`, `ModuleScan`, `PerformanceTimer`, `BatteryHealth`, `VinDecoder` + NHTSA lookup, maintenance/fuel (`VehicleService`). The raw console goes through `ConsoleSafety`'s allow-list; keep it read-only. `ConnectionLog` tags events by `LinkLayer`. Saved adapters (`AdapterProfiles`) are tried in rotation.

**PIDs:**
- **`PidDefinition`** covers standard Mode 01, manufacturer (`ManufacturerProfiles`, detected from the VIN WMI prefix, longest match wins) and custom (`CustomPidStore`) PIDs.
- **Formulas:** Torque-style strings (`((A*256)+B)/4`, `signed()`) compiled and cached by `FormulaEvaluator`.
- **Community-sourced PIDs:** manufacturer PIDs are community-sourced and each carries a `note`. Don't add ones you can't source.

**Storage:**
- **Gauges and custom PIDs:** JSON in SharedPreferences (`GaugeStore`, `CustomPidStore`), not Room. Room only holds `TripMetricLog`.
- **Room migrations:** Room uses `exportSchema = false`, so any schema change needs a hand-written `Migration`.

**Assistant (`assistant/`):**
- **Routing:** `AssistantRepository` builds the vehicle context (`ObdManager.vehicleContext()` + 30-day Room baseline via `VehicleContextFormatter`). It routes to `GeminiAssistantClient` or `PerplexityAssistantClient` (Agent API `/v1/agent`; the old Sonar endpoint is retired).
- **Perplexity credit routing:** `PerplexityRouting.needsWeb` sends current-fact and diagnostic questions to the web-grounded preset; vehicle-data questions and small talk go to `perplexityLocalModel` with no tools, so they don't pay for a search. If that model is rejected (400/404), the client falls back to the preset. Each answer's engine label shows its `usage` cost.
- **Diagnostic questions:** `KittPersona.isDiagnostic` sends them to the diagnostics model.
- **Gemini client resilience:**
  - retries without `google_search` when a free-tier key refuses grounding;
  - swaps a retired model (404) for the newest same-tier model from `listModels` and persists it.
- **Errors:** clients return the sealed `AssistantResult` instead of throwing.
- **Persona:** `KittPersona` holds the personality plus non-negotiable accuracy and driving-safety rules. `PersonaStyle.Savage` is the default by the owner's explicit choice (Unfiltered turned up further: swearing in every sentence, sarcasm on every fact, a parting shot); `Unfiltered` and `Classic` remain. Check `persona.rude`, never `== Unfiltered`. Spoken lines go through `SavageLines.pick` (Savage appends a jab after the facts); `KittAlerts`, `LauncherCommandRunner` and `RouteRules` have the rude variants.
- **Keys:** API keys are entered in-app and encrypted with an Android Keystore AES-GCM key (`SecureStore`). `BuildConfig.GEMINI_API_KEY` from `local.properties` is only a dev fallback.
- **Voice commands:** `VoiceCommands` (pure parser) is tried before the AI; `LauncherCommandRunner` runs matches and sends home-screen changes over `LauncherBus`. `WakeWordListener` ("Hey KITT") restarts the recognizer in short sessions and pauses while an overlay or KITT's voice is active.
- **Problem reports:** `ProblemReport` installs a crash handler (files in `filesDir/crashes`) and writes a text report (crashes, connection log, settings without keys, own logcat) from KITT Config.
- **Subscriptions:** consumer subscriptions (Gemini/Claude/Perplexity Pro) can't be used via API; `AiAppHandoff` sends questions to those official apps instead.

**Riding along (`assistant/DriveAssistant`, all read-only):**
- `DrivingMonitor` (pure) turns OBD readings into `DrivingEvent`s (hard brake, cold revs, long idle, full throttle), rate-limited; `KittAlerts.drivingEvent` speaks them (setting `drivingCommentary`). Full throttle also plays `SoundFx.turboBoost` (synthesized, off by default).
- When the OBD link drops after 2+ minutes (ignition off): the GPS position becomes `ParkingSpot` (reverse-geocoded via Photon), and `ReportCards.grade` grades the drive from the trip log; both show on the Trips screen (parking spot as a QR code for the phone).
- `FuelRules` (low fuel once per drive) → alert with `AlertAction.NearestFuel` → `RouteNavigator.navigateToNearestFuel` (Photon `osm_tag=amenity:fuel`). `KittAlert.action` puts a button on the banner.
- `SpeedLimitService` asks Overpass for the road's `maxspeed` while the launcher shows and the vehicle moves (`SpeedLimits` parses it); HUD sign on the map, optional warning (`speedAlerts`, off by default).
- Voice: "find gas", "where did I park", "pursuit mode" (`VoiceCommands`).

**Extras:**
- `SelfCheckPanel` (top of KITT Config) checks every permission/setting with a fix button; `HomeScreen.isDefault` also raises an alert at start-up.
- `ReportShare`: problem report to the share sheet (FileProvider `${applicationId}.files`) or a QR code (ZXing) for a 10-minute, random-token, one-file HTTP server on the Wi-Fi/hotspot address.
- `PursuitScreen` overlay (`LauncherOverlay.Pursuit`); `SidePanelService` ("Maps + KITT") is a touchable overlay beside a full-screen nav app, since a home activity can't share split-screen.
- Night theme (`nightTheme`) follows `NightModeController`, which uses `SunTimes` (pure) at the GPS position when there's no light sensor.
- `PersonaStyle.Karr`: menacing, not profane; `SavageLines.pick` adds a KARR coda. In `KittAlerts`, use `bare()` for pieces of a line that gets its own jab.
- Browser: per-site `dataSaver` (no images, media waits for a tap); the stream window's mini player (`WebMedia.mini`, a corner FrameLayout in the activity) takes a playing video when its page leaves the screen. AI page "Big text" reader.
- `AutoBackup` writes the backup weekly to Downloads/KITT (MediaStore on 29+); backups restore into either edition. `ReleaseNotes` (levels shared by both editions) shows "What's new" once after an update.
- GM tire pressures: BCM header 241, `22 5005`–`5008`, sourced from OBDb (2022 Blazer).

**Film themes (`HudStyle`: Terminator, RoboCop, Matrix, Future = Back to the Future):**
- `FilmHud.kt`: status/intro/alert text (`FilmHudText`), the full-screen overlay (Terminator's scrolling machine code, RoboCop's visor sweep; still in lite mode) and `hudFrame` (brackets, ticks, BTTF rivets).
- `FilmExtras.kt`: `ThemeIntro` boot sequence when a film theme comes on (`filmIntros`), `TimeCircuitsStrip` (BTTF replaces the status-strip clock; `TimeCircuits` is pure), `FilmMapReticle`, `FilmAlertHeader`, `assistantName()`.
- `KittScanner` (flux pulses for BTTF), `VoiceModulator` (flux capacitor / oscilloscope), `KittPanel` titles (label tape for BTTF).
- `FilmCharacter` (`assistant/FilmCharacters.kt`): per-theme personality layer added to `KittPersona.systemInstruction`, Gemini voice + style, device-TTS pitch/rate, greeting; setting `filmCharacters`. Original characters only: never imitate a real actor's voice or recite film dialogue.

**Scanner (`ui/scanner/`):**
- **`ScannerEngine`:** pure-Kotlin light simulation (eased head plus exponential lamp decay) shared by the Compose `KittScanner` and the overlay's plain `View`.
- **`ScannerOverlayService`:** a non-touchable `TYPE_APPLICATION_OVERLAY` at alpha 0.8, the Android 12+ pass-through-touch limit. It hides while `LauncherVisibility.visible` is true.

## Conventions

- **Film HUD themes:** `UiTheme.hud` (`HudStyle`: Terminator, RoboCop, Matrix — the Matrix theme is stored as `UiTheme.Colorado`) also changes typography (`HudTypography`), shapes, the scanner (`KittScanner`: 20-segment bar or visor), panel headers/frames (`hudFrame`), the full-screen `FilmHudOverlay`, and HUD text (`FilmHudText`). New UI should go through `KittPanel`/palette tokens so these themes apply automatically.
- **KITT visual language:** new UI uses `KittPanel` framing, uppercase labels, palette constants (`ScannerRed`, `DashAmber`, `LedGreen`, `KittBlack`…) and Canvas-drawn indicators (`SegmentDisplay`, `LedBarGauge`, `VoiceModulator`). There is intentionally no material-icons dependency.
- **Shared controls:** reusable controls live in `ui/components/KittControls.kt` (`KittTextField`, `KittToggle`, `ChoiceRow`, `SectionHeader`).
- **Lazy lists:** wrap multiple composables inside a `LazyColumn` `item {}` in a `Column`, otherwise they overlap.
- **Animations:** read animated values inside draw lambdas, so frames only redraw instead of recomposing.
- **Threading:** network, DB, Keystore and IO work goes through `Dispatchers.IO` or Room suspend functions. Always rethrow `CancellationException` before a broad `catch`. `SpeechRecognizer` must be driven on the main thread.
- **Unit tests:** JVM tests can't use `org.json` or Android framework classes. Keep protocol and formatting logic in plain-Kotlin objects (`ObdResponseParser`, `FormulaEvaluator`, `DtcDecoder`, `VehicleContextFormatter`, `ScannerEngine`), which is what the tests cover.
