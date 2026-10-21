# DriveBoard

A Java/native Views Android Auto development app: arrange a clock, Malaysia JAKIM next prayer, and active now-playing card on your phone, then render that same `BoardView` into the car host's navigation surface. Package: `local.driveboard`. Android 12+ (API 31), compile/target API 36. This is a sideloaded dashboard prototype using the navigation category, not a production navigation app or a claim of Google Play eligibility.

## Build and install (Windows / JDK 17)

Versions: AGP **8.12.0**, Gradle wrapper **9.3.1** (matched to the read-only `winwin-store/android` reference), `androidx.car.app:app` **1.7.0**. Google Maven metadata was checked on 2026-10-21: 1.7.0 is the latest stable; 1.8.0 is still RC and 1.9.0 is alpha. JUnit 4.13.2 is test-only; the car library is the sole direct runtime dependency.

Create ignored `local.properties` with your SDK path, for example:

```properties
sdk.dir=C:/Users/Adam/AppData/Local/Android/Sdk
```

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -W -n local.driveboard/.MainActivity
```

If the environment disallows writes to the normal Gradle/Android user directories, use workspace-local homes before running Gradle (both ignored by Git):

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle-user-home"
$env:ANDROID_USER_HOME="$PWD\.android"
```

## Phone editor and Android Auto

Drag a card to move it. Drag its mint bottom-right handle to resize it. Positions and sizes snap to a 12 × 6 grid, clamp inside the board, and save immediately as JSON in SharedPreferences. Overlap is allowed. Reset restores the three starting cards. The preview uses the last visible car surface aspect ratio; before connecting it uses 1920 × 720.

Enter a JAKIM zone such as `WLY01`, then tap **Save zone**. Tap **Grant notification access** and enable **DriveBoard now playing** in Android settings to show playing MediaSession title/artist. The notification listener itself is a no-op; no notification content is collected. Access may be revoked in the same system settings.

To enable the sideloaded app in Android Auto: **Android Auto settings → tap Version 10 times → developer settings → Unknown sources**. Then connect to a compatible car host and choose DriveBoard. These settings must be changed by the user; the app does not change them.

The car service deliberately uses `ALLOW_ALL_HOSTS_VALIDATOR` for sideloaded development. Its `NavigationTemplate` has one Refresh action. It tracks visible/stable regions, clips all board drawing to the visible region, and measures/layouts the view at that region's dimensions. Surface rendering runs every second and on preference changes, and stops on lifecycle stop or surface destruction.

## Data behavior

- Clock uses device locale and the device's 12/24-hour preference.
- Prayer endpoint: `https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=today&zone=WLY01`. The verified response contains `prayerTime[0]`, `status: "OK!"`, `zone`, and `serverTime`, with `fajr`, `dhuhr`, `asr`, `maghrib`, `isha` as `HH:mm:ss`. These are displayed as Subuh, Zohor, Asar, Maghrib, Isyak. Times and daily rollover use Asia/Kuala_Lumpur. Fetches run off the UI thread with timeouts, cache successful daily JSON per zone, and refresh daily or on zone change. Failures preserve cached values or show `—`; failed requests retry at most once per minute. After Isyak, tomorrow's Subuh time/countdown is explicitly marked **estimated**, using today's Subuh until the next daily response is available at midnight. Stale offline data is marked cached and has no misleading live countdown.
- Media polls active sessions once per second, selecting the first playing session. Paused sessions show **Nothing playing**. Missing access shows **Grant access on phone**.
- `BoardView.draw()` is for native drawing; WebView/video will need VirtualDisplay + Presentation later (see the `ponytail:` comment).

## Verification and remaining checks

The plain JVM `BoardLayoutTest` covers snapping, move/resize clamping, and JSON round-trip without Android or `org.json` dependencies. `gradlew assembleDebug test` passes (debug and release unit-test variants).

Smoke check on 2026-10-21: installed onto the single connected Nothing A142 (Android 16), `adb install -r` returned `Success`, and `am start -W` returned `Status: ok` / cold launch in 813 ms. MainActivity remained resumed with a running app process; filtered AndroidRuntime and crash-buffer logs contained no DriveBoard crash. No other app was modified and Android Auto settings were left unchanged.

Environment notes: the first build was blocked by global Gradle/debug-keystore directory permissions; workspace-local user homes resolved it. Remaining nonfatal toolchain warnings concern Android analytics-directory access, SDK XML tooling version mismatch, and Gradle 10 deprecations. Windows curl's Schannel failed in this sandbox, so the live JAKIM JSON and Google Maven metadata were successfully verified with Node HTTPS instead.

Actual car display is **unverified**. Test the following on the car: app discovery, surface rendering, live phone edits, overlay/split-screen visible regions, disconnect/reconnect, and lifecycle stop/resume. On the phone, test drag/resize persistence, zone changes, cached/offline prayers, Malaysia midnight rollover, and notification access grant/revoke with real music playback. Android 12/12L runtime behavior is also unverified; the installation smoke check targets the connected Nothing A142 running Android 16.

Version sources: [Google Maven metadata](https://dl.google.com/dl/android/maven2/androidx/car/app/app/maven-metadata.xml), [Car App Library documentation](https://developer.android.com/training/cars/apps/library).
