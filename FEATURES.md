# KITT Launcher: Features and Options

A Knight Rider–themed home screen for Android car head units. It replaces the unit's stock home screen with a KITT-style dashboard. It adds an OBD-II car scanner and an AI co-pilot named KITT that can see your vehicle's live data, plus a browser for video, music, AI chat and games. It is built for a 12.1" portrait (9:16) Tesla-style head unit, and it also adapts to landscape screens.

**Latest additions:**
- **Landscape layout:** on a wide screen, KITT and the page buttons move to a side rail and the dock becomes one slim row, so the pages get nearly the full height.
- **Screen size** (KITT Config → Display): Auto, Compact, Normal or Large.
- **Lite mode** (KITT Config → Display → Performance): Auto, Lite or Full, for slower head units.
- **Browser that can't take the launcher down:** a page that hangs (long Claude chats were freezing the whole launcher) is shut down and shows a "page stopped" card with Reload; hidden silent pages pause; least recently used hidden tabs are freed and reload when opened; every hidden page is freed when Android runs low on memory.
- **Freeze reports:** if the launcher ever stalls for 4 seconds, Report a problem now includes where it was stuck.
- **Savage KITT** personality, the new default (see Personality).

*Technical summary:* a native Android app in Kotlin and Jetpack Compose (single module), running on Android 7.0+ (minSdk 24, target SDK 37). It uses Room for trip history, OkHttp for networking, osmdroid for the map, and AndroidX WebKit for the browser. There are no Google Play Services or analytics dependencies. Logic such as the OBD parsing, formulas, trip maths and voice-command parsing is plain Kotlin and covered by about 156 JVM unit tests.

---

## The look

- **Always-on scanner light.** The red KITT scanner sweeps across the top of the screen at all times.
  - It can also show over other apps as a see-through strip that ignores touches (a `TYPE_APPLICATION_OVERLAY` at 80% opacity, the most Android 12+ allows while still letting taps through).
  - **Shift light option:** the scanner flashes as the engine approaches an RPM you choose (5,000 by default).
  - *Under the hood:* a shared simulation engine (eased head movement with exponentially fading lamps) drives both the in-app scanner and the overlay.
- **10 themes:**
  - **Colour themes:** KITT, KARR, Night Vision, Super Pursuit, Retro VFD, Outrun and Stealth.
  - **Film HUD themes:** these change much more than colours, including the fonts, panel shapes, the scanner, the panel frames, a full-screen scanline effect and themed status text.
    - **Terminator:** a red-tinted vision overlay, a 20-segment scanner, and T-800 readouts such as "THREAT ASSESSMENT: NONE".
    - **RoboCop:** a cold blue visor glow, targeting-bracket panel corners, a visor-style scanner and the Prime Directives.
    - **The Matrix:** green phosphor terminal styling with optional falling "digital rain" behind see-through panels.
- **Custom accent colour:** any theme can be given your own accent colour.
- **Night mode:** Off, Auto or Always.
- **Units:** imperial or metric.
- **Status strip** along the top shows the clock, compass heading, Wi-Fi/Bluetooth, OBD link and battery voltage. Tap it to open Quick Settings.
- **Optional extras:**
  - **Button sounds.**
  - **Themed app icons:** tinted to match the theme.
  - **Third-party icon packs** in the ADW/Nova format.
- **Screensaver:** after a set number of idle minutes while parked.

## Home screen pages

Swipe left and right between pages or tap the page buttons. A swipe that starts at the left or right edge of the screen always changes page, even over a web page or the map. The Home button always returns you to Drive.

- **Drive:** a live map (OpenStreetMap, centred on you) with a grid of your apps or vehicle status underneath. On wide screens they sit side by side.
  - **Search and directions:** tap **Search** on the map, type an address or place (results nearest first, plus your recent destinations), and the route is drawn on the map with its distance, time and arrival time. Tap **Start** for turn-by-turn guidance: the next turn shows at the top of the map with an arrow and distance, KITT speaks each turn twice (early, then at the turn; earlier on faster roads), recalculates if you leave the route, and announces arrival. **Other app** hands the destination to Google Maps, Waze or any navigation app instead. Uses free OpenStreetMap services (Photon search, OSRM routing), no account; needs a data connection and location. Guidance runs while the launcher is on screen. Spoken directions can be turned off in KITT Config.
  - The map area can switch to the **Stream window** (see Browser).
  - Swiping between pages is paused while you're dragging the map or a video.
- **Gauges:** a full wall of your OBD gauges.
- **Media:** shows whatever is playing in any app (title, artist, artwork, play/pause/skip). It needs notification access (it reads the active Android `MediaSession`).
- **AI:** your Gemini, Claude and Perplexity websites in browser tabs, signed in with your own accounts.
- **Games:** browser games from your bookmarked sites (Internet Arcade, MS-DOS Games, Poki, CrazyGames and others), playable with a game controller.
- **Widgets:** add any regular Android home-screen widget.
- **Apps:** the full app drawer.
  - Search and an A–Z jump bar.
  - Sorting by most used.
  - Pin apps to the home grid, or hide them.
  - Apps are grouped automatically (Navigation, Music, Video, Car, Social, Tools).
- **Dock:** fixed at the bottom for quick access.
- **Driving layout (optional, off by default):** switches to bigger, simpler controls while the vehicle is moving. It waits about 15 seconds of driving and 60 seconds stopped before switching, so it doesn't flicker in traffic.
- **Quick Settings:** brightness, sound, Wi-Fi, Bluetooth and night mode, plus a shortcut to Android Settings.

## KITT, the AI co-pilot

- **Talk or type.** Use the mic button, your steering-wheel voice button, or say **"Hey KITT"** if you turn that on.
- **KITT knows your vehicle.** Each question includes the live readings, fault codes, the vehicle identity and a 30-day summary of your driving history, so you can ask "how's my coolant?" or "how was that drive?"
- **Two AI services** (you supply your own API key; keys are encrypted on the device with the Android Keystore, AES-GCM):
  - **Google Gemini:**
    - a fast model for conversation and a smarter model for diagnostic questions, each selectable;
    - optional Google Search grounding, skipped automatically on free-tier keys;
    - if Google retires a model, KITT switches to the newest equivalent on its own.
  - **Perplexity Agent API:**
    - presets from **fast** to **high**, chosen separately for conversation and diagnostics;
    - every answer shows its cost;
    - **credit saving:** questions about your vehicle or plain chit-chat skip the paid web search and go to a single model; current-events questions (weather, prices, recalls, scores) and diagnostics search the web.
- **Sources:** web-grounded answers list their sources.
- **Subscriptions:** if you pay for Gemini, Claude or Perplexity Pro, KITT can hand your question to that official app, since consumer subscriptions can't be used through an API.
- **Personality:**
  - **Savage KITT** (the default): Unfiltered with the dial past maximum, swearing in every sentence, sarcasm on every fact and a parting shot on every spoken line, with the same accuracy and safety rules;
  - **Unfiltered KITT**: savagely sarcastic and foul-mouthed, swearing in every reply and alert and roasting your driving and your vehicle, while every fact stays accurate, including turn-by-turn directions (each turn is said plainly first, then the insult);
  - **Classic KITT:** the polite TV version.
  - Both follow strict rules: don't make things up, and don't distract the driver.
- **Voice:**
  - **Gemini neural voice:** pick from Gemini's voices;
  - **Device text-to-speech:** this is also the offline fallback.
  - Music automatically lowers ("ducks") while KITT speaks.
- **Speech recognition:**
  - **Auto:** uses KITT's own Gemini transcription when a key is set, otherwise a safe device recognizer;
  - **Gemini** only;
  - **a device recognizer** of your choice.
  - An endpoint detector decides when you've finished talking.
- **Microphone takeover guard:** some head units hijack the mic and jump into their built-in voice/radio app. KITT detects this, won't trigger the stock recognizer, and the Microphone fix section gives copy-paste ADB commands to disable the hijacker.
- **Instant voice commands** run immediately without the AI:
  - "open Spotify", "show the gauges page";
  - "stream YouTube" / "close the stream";
  - "navigate to the nearest Walmart", "take me to 1600 Main Street" / "cancel the route";
  - "play", "pause", "next track", "previous track";
  - "read my codes", "open diagnostics";
  - "start logging" / "stop logging".
- **KITT speaks up on its own:**
  - a greeting when you start up, and when the vehicle connects;
  - **alerts** for overheating, charging problems, new fault codes and any gauge limit you set (a gauge must stay past its limit for 20 seconds), each spoken at most twice per drive and then shown silently;
  - **road weather warnings** (freezing rain and black ice, heavy snow or rain, fog, storms, strong wind) from Open-Meteo, each distinct warning at most every 3 hours;
  - **maintenance reminders**, at most twice a day;
  - **incoming messages** read aloud: off, who it's from, or the whole message (SMS and common chat apps).

## Car scanner (OBD-II)

Plug in an ELM327-compatible OBD-II adapter (a Bluetooth or Wi-Fi diagnostic dongle) to get Car Scanner–style diagnostics.

- **Adapters supported:**
  - **Bluetooth Classic** (SPP/RFCOMM, with a channel-1 fallback for picky adapters);
  - **Bluetooth Low Energy** (finds the adapter's read/write channels automatically);
  - **Wi-Fi** (TCP).
  - Saved adapters are tried in turn, and it auto-reconnects.
- **Connection settings:** the protocol can be auto-detected or picked manually, and fast polling can be turned on or off.
- **Seven diagnostics tabs:**
  - **Gauges:**
    - build your own dashboard in four styles (Dial, LED bar, Digital, Graph);
    - set high and low warning limits on any gauge.
  - **Data:**
    - live graphs of any sensor;
    - **data logging** to CSV while you keep using the launcher, which you can export anywhere.
  - **Codes:**
    - stored, pending and permanent fault codes;
    - freeze-frame data;
    - emissions readiness (whether you'd pass an inspection);
    - "Ask KITT" about any code.
    - Clearing codes is a manual button with a warning; KITT and the app never do it on their own.
  - **Vehicle:**
    - **VIN decoding** (built-in, with check-digit validation, plus an NHTSA online lookup);
    - **odometer tracking;**
    - **battery & charging health** (resting voltage, voltage while cranking, voltage while running);
    - **maintenance schedule** (oil, tyres and others, with due-soon and overdue status);
    - **fuel log** (full-to-full measured economy and cost per mile);
    - **fuel range and cost.**
  - **Tools:**
    - **performance timer:** 0–60 mph, 1/4 mile and 60–0 braking, with results that are indicative only, since OBD speed has limited precision;
    - **Mode 06 on-board test results;**
    - **module scan;**
    - **adapter identification;**
    - **a raw command console** that can only send read-only commands;
    - **a connection log** that names which layer failed (Bluetooth, adapter, ELM init, ECU, or decoding).
  - **PIDs:**
    - every standard live sensor;
    - **manufacturer profiles** (GM, Ford, Toyota, Stellantis, Honda, Nissan, Hyundai/Kia, VW group, BMW, Mercedes, Subaru, Mazda), auto-detected from your VIN;
    - **custom PIDs** with Torque-style formulas such as `((A*256)+B)/4`.
  - **Link:** find, pair and connect adapters, and choose the manufacturer profile and protocol.
- **Trips:**
  - every drive is logged once per second;
  - the Trips screen shows your last drive and the rest of the week: distance, time, speed and fuel used/economy where the vehicle reports it.
- **Vehicle safety by design:** the app never clears codes, changes ECU settings or sends write commands unless you press the button for that specific action.
- *Under the hood:*
  - **ELM327 driver:** commands are serialised, so background polling and on-demand requests never collide.
  - **ISO-TP:** multi-frame responses are reassembled.
  - **Polling:** only sensors currently on screen are polled, with the fast ones every cycle. On CAN vehicles it requests up to 6 PIDs per request and falls back to single PIDs automatically. It also uses ELM response-count suffixes and engine-specific CAN headers.
  - **Units:** all values are stored in metric internally and converted for display.

## Browser (Stream window, AI page, Games page)

One shared, tabbed browser powers three places: the **Stream window** on the Drive page, the **AI** page and the **Games** page.

- **Stream window:**
  - a video/music window over the map area, which plays while driving by design;
  - **built-in licensed services:**
    - **video:** YouTube, Tubi, Pluto TV, Plex, Roku Channel, Xumo Play, Crackle, Kanopy, Hoopla, PBS, Twitch and more;
    - **music:** YT Music, Spotify, SoundCloud, Pandora, iHeartRadio, TuneIn, Amazon Music, Deezer, Bandcamp, SomaFM, Radio Garden and more.
- **Save any site:** add it from the address bar with ★ Save, or import and export your bookmarks under "My sites".
- **Per-site options:** desktop or mobile layout, dark pages, ad blocking (never applied to YouTube) and text size. Google sign-in pages always open in the mobile layout, because Google refuses sign-in from the desktop one inside an app.
- **Same controls everywhere:** the Stream window, AI page and Games page share one toolbar; ▲ ▼ also scroll chat sites that scroll an inner panel, and the Back button steps back through pages (leaving full screen first) on all three.
- **Sound keeps playing:** a page playing music or video keeps playing when you swipe to another home page (its tab shows ♪). Only one thing plays at a time: starting sound on another page, or pressing play in another app such as YouTube Music, pauses it. KITT's voice just lowers it.
- **Tabs and pop-ups:** multiple tabs, and pop-ups you trigger open as new tabs. Links that try to launch other apps are blocked, so nothing hijacks the screen.
- **Separate sign-ins:** each page keeps its own logins (separate WebView profiles where the system WebView supports it).
- **Userscripts:**
  - install Tampermonkey-style `.user.js` scripts from Greasy Fork, from a link, or by pasting the code;
  - they run when the page starts loading (document-start).
- **Toolbar helpers:** close on-page pop-ups, scroll buttons, full screen, reload, and find-the-install-link.
- **Game controllers:** buttons are mapped to the keyboard keys web emulators expect, and the analog stick works as a D-pad.
- **Voice to the AI page:** a question you speak can be typed into every open AI tab.

## Launcher settings

- **Steering-wheel buttons:** "learn" any button the head unit sends and assign it an action:
  - talk to KITT;
  - next or previous page;
  - Drive page;
  - stream window;
  - play/pause;
  - diagnostics.
- **Start-up routine:** on start, or on wake after 5+ minutes of sleep, open a chosen app and/or resume your audio.
- **Driver profiles:** save and switch personal settings per driver, while vehicle data (adapters, odometer, service, fuel) stays shared.
- **Backup and restore:** all settings in one file. API keys are deliberately left out.
- **Updates:** the app can check a GitHub repository for new releases and install them.
- **Report a problem:** writes one text report with recent crashes, the OBD connection log, your settings (keys removed) and the app's own log.
- **Default assistant:** set KITT as Android's assistant so the voice button opens it.

## Privacy and safety at a glance

- **API keys** are encrypted on the device and never included in backups or problem reports.
- **No analytics or tracking.** Network calls go only to the AI service you chose, Open-Meteo weather, NHTSA VIN lookup, map tiles, OpenStreetMap place search and routing (only when you search or navigate), and the sites you open.
- **The car is read-only** unless you explicitly press a button that does something else (clearing codes).
