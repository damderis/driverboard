# DriveBoard

Personal Android Auto board for a Proton e.MAS, with a native Java phone layout editor. Android 12+; package `local.driveboard`. This sideloaded navigation-surface prototype is not a production navigation app or a claim of Play eligibility.

## Features

- Masjid Stone & Teal phone and car surfaces, with five live colour tones, system light/dark phone styling, and bundled Atkinson Hyperlegible Next Regular (400), SemiBold (600), ExtraBold (800). Google Fonts supplies a variable TTF; XML font families pin those three weights. Licence: `licenses/atkinsonhyperlegiblenext/OFL.txt`.
- Clock/date, next JAKIM prayer and countdown, five daily prayers, active MediaSession title/artist, and car battery/range. The eight-point prayer star is drawn as two outlined squares. No sketch sample data is used.
- Native settings card: zone picker/location, notification access, battery status, tone swatches, EN/BM. Native controls use sp text, at least 48dp targets, scrolling and system-bar insets. The car-aspect preview uses the shared proportional Canvas renderer.
- Drag to move, bottom-right brass corner to resize on the 12 x 6 grid; preferences update the car live. Reset restores clock left, prayer upper right, music and battery below. Legacy layouts migrate to include battery.
- EN/BM through `createConfigurationContext`; prayer names stay Subuh, Zohor, Asar, Maghrib, Isyak. Dates use the selected locale. Numerical drawing uses `tnum`.

## Build and install

JDK 17, Gradle wrapper 9.3.1, AGP 8.12.0, Android SDK 36, AndroidX Car App 1.7.0. Set `sdk.dir` in ignored `local.properties`.

Always build with `ANDROID_USER_HOME` set to this workspace's `.android` directory so the project-local debug key matches the installed app's signature.

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle-user-home"
$env:ANDROID_USER_HOME="$PWD\.android"
.\gradlew.bat test assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n local.driveboard/.MainActivity
```

Enable Unknown sources in Android Auto developer settings yourself, then connect and select DriveBoard. The development service accepts installed hosts using `ALLOW_ALL_HOSTS_VALIDATOR`; restrict signatures before distributing it.

## Location and prayer data

Verified with Node HTTPS on 2026-10-21:

`GET https://api.waktusolat.app/zones/3.1390/101.6869` returned HTTP 200:

```json
{"zone":"WLY01","state":"KUL","district":"W.P. Kuala Lumpur"}
```

The app requests `/zones/{latitude}/{longitude}`, validates the returned zone against its bundled catalogue, saves code/name and refetches prayers. `GET https://api.waktusolat.app/zones` returned 60 entries with `jakimCode`, `negeri`, `daerah`; these provide offline zone names and the manual picker. Source: [API documentation](https://api.waktusolat.app/docs). Reproduction/evidence: `.impeccable/review/zone-endpoint.md` and `generate-zone-catalog.js`. No nearest-coordinate approximation is needed.

Coarse location permission is requested only after Use location. The app uses `LocationManager.getCurrentLocation` with the network provider, then a recent last-known fallback; no Play Services. With permission already granted, opening the app checks again after twelve hours. Errors stay inline and preserve the selected zone; tap the row to pick manually. Approximate location near a zone boundary should be checked against the manual picker.

Prayer source remains JAKIM: `https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=today&zone=WLY01`. Responses are validated and cached by zone/day, with Malaysia time for rollover. Network operations run off the drawing thread with timeouts. Old cache is dimmed and labelled with its actual update time. After Isyak, tomorrow's prayer remains unavailable until verified data arrives; today's Subuh is never presented as tomorrow's time.

## Car energy and surface behavior

On Car App API 3+, `CarHardwareManager` fetches `EnergyProfile` and subscribes to `EnergyLevel` for an EV. Only `CarValue.STATUS_SUCCESS` fields are saved as `battery_pct`, `range_m`, `battery_at`. The listener is removed when the screen stops. Battery/range support depends on the head unit sharing EnergyLevel; the Proton e.MAS is unverified until a drive. Missing data is a dash, the phone initially says Waiting, and data older than 30 minutes is dimmed with `updated HH:mm`. No phone battery or invented range is substituted.

Inspect the first drive with `adb logcat -s DriveBoardCar` for API level, profile and energy field statuses. Car hardware permission declarations do not guarantee host support.

Each frame fills the complete surface with the selected ground colour, then renders inside the visible region, falling back to stable area and full bounds. Preference changes and one-second ticks redraw; screen stop removes callbacks/listeners.

Notification access is optional. Grant opens Android notification-listener settings. The listener collects no notification content; active sessions supply playing metadata. Permission revocation and missing data remain explicit.

## Contrast

WCAG relative luminance, fixed cream `#F4EFE4` and sage `#A9C2BD` against each panel. All requested shades pass 4.5:1 without adjustment.

| Tone | Ground / panel | Cream | Sage |
| --- | --- | ---: | ---: |
| Teal | #0F3D3E / #16494A | 8.78 | 5.34 |
| Emerald | #0E3B2E / #15503F | 8.14 | 4.95 |
| Navy | #13233F / #1B3156 | 11.30 | 6.87 |
| Maroon | #3A1420 / #4D1D2C | 11.99 | 7.29 |
| Graphite | #24272B / #2F3337 | 11.10 | 6.75 |

`tools/font-and-contrast.cjs` reproduces these values; JUnit guards palette completeness and contrast. The phone uses amber `#8A6100` on limestone and a lighter amber on dark cards for Waiting.

## Verification

Build/test/install results and visual review are recorded in `.impeccable/review/verification.md`. The car itself requires a drive: app discovery, actual surface size and visible regions, live phone edits/tone/language, battery API statuses, disconnect/reconnect, stop/resume. Also test location grant/deny, zone changes, offline prayer cache and midnight rollover, media access grant/revoke and real music playback. Google Maps and a 3D car remain outside this change.
