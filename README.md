> **Retired (October 2026).** KITT Launcher is now built from the same code as KITT Launcher.2, in
> [tdater/KITT-Launcher.2](https://github.com/tdater/KITT-Launcher.2) (product flavor `kitt`, same app ID
> `com.aiconsole.knightriderlauncher`). Installed copies keep updating from KITT Config → Updates.
> This repo stays at v1.7 for reference; make changes there.

# KITT Launcher

A Knight Rider (KITT) themed Android home-screen launcher for aftermarket car head units, built
for a 12.1" portrait Tesla-style unit and adapting to landscape screens. It includes:

- an ELM327 OBD-II car scanner (gauges, fault codes, readiness, custom PIDs, data logging);
- KITT, a voice and text AI co-pilot (Gemini or Perplexity) that sees live vehicle data;
- map search with spoken turn-by-turn directions (OpenStreetMap, no account);
- a tabbed browser for streaming, AI chat sites and browser games;
- the always-on red scanner light and ten themes.

See [FEATURES.md](FEATURES.md) for everything it does.

## Building

Native Kotlin and Jetpack Compose, single `:app` module. Android 7.0+ (minSdk 24), target SDK 37.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"   # Android Studio's bundled JDK
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

- AI keys are entered in the app (KITT Config) and stored encrypted on the device. Optionally put
  `GEMINI_API_KEY=...` in `local.properties` as a development fallback; that file is never committed.
- Signed release builds come from the GitHub workflow when the `KITT_KEYSTORE_BASE64`,
  `KITT_KEYSTORE_PASSWORD`, `KITT_KEY_ALIAS` and `KITT_KEY_PASSWORD` repository secrets are set.
  Pushing a `v*` tag publishes the signed APK as a release for the in-app updater.

## Licence

Copyright © 2026 travisdater. All rights reserved. This is a personal project, provided as-is
with no support.
