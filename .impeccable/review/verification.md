# DriveBoard verification and change record

2026-10-21. Git was used for read-only status/diff inspection; no commit, staging, checkout or other Git write operation was performed.

## Results

- Final `gradlew.bat test assembleDebug`: **BUILD SUCCESSFUL in 3s**, 57 tasks (12 executed, 45 up-to-date). Full output: `build-output.txt`.
- Six JUnit tests per variant: BoardLayoutTest (4), ToneTest (1), ZoneCatalogTest (1), zero failures in debug/release. They cover layout bounds/round trips/default arrangement/legacy migration, five-tone completeness/contrast, and the 60-zone catalogue including PHG07 and WLY01. Zone response parsing uses Android org.json and was verified against the real HTTPS payload rather than a JVM stub.
- `adb install -r`: **Success**. Launch: **Status: ok**, **COLD**, TotalTime **882 ms**, WaitTime **908 ms**. Output: `install-output.txt`.
- AndroidRuntime output for the final running process was empty (`runtime-output.txt`). An inset-controller startup crash found during early testing was fixed before these final captures.
- Final state confirmed: `font_scale` **1.0**, `uimode night` **no**. Only requested night/font system settings were changed. No other apps were operated. Phone app left in EN with Navy selected; the default layout was exercised using Reset layout.
- Node HTTPS returned 200 for the GPS lookup and 60-zone catalogue. Exact URL/response: `zone-endpoint.md`. No coordinate approximation fallback is used.
- Contrast cream/sage: Teal **8.78/5.34**, Emerald **8.14/4.95**, Navy **11.30/6.87**, Maroon **11.99/7.29**, Graphite **11.10/6.75**. No panel adjustment.
- Nonfatal toolchain notices: existing Gradle 10 deprecations, deprecated native Android APIs; earlier clean configuration also reports restricted analytics-directory access and SDK XML tool-version mismatch. They did not prevent final build/test/install.

## Visual review

All captures were opened and inspected. Astra 6 also reviewed the four required captures and returned a visual pass. No visible clipping or overlap. The preview date was shortened and its outer corners rounded during review; long titles ellipsize rather than shrink indefinitely.

| Capture | State |
| --- | --- |
| `phone-light.png` | Light, Teal, EN, normal font |
| `phone-dark.png` | Dark, Teal, EN, normal font |
| `phone-font130.png` | Light, Teal, EN, font_scale 1.3 |
| `phone-tone-navy.png` | Light, Navy, EN, normal font after tapping swatch |
| `phone-bm.png` | Additional BM check, Light, Navy, normal font |

Review images are direct adb captures, with no edits or injected/sample battery/music data. Additional UI hierarchy snapshots are diagnostic evidence only.

## Per-file changes

| File | Change |
| --- | --- |
| `app/src/main/java/local/driveboard/MainActivity.java` | Native single-card settings editor, edge-to-edge insets, light/night palettes, preview, named zone picker/location permission flow, battery status, tone swatches, language segments and reset/help. |
| `BoardView.java` (same Java directory) | Shared Masjid palette, bundled typography/tabular figures, proportional panels, prayer star/five-prayer row, battery missing/stale renderer, abbreviated dates and brass resize corners. |
| `BoardLayout.java` | Four-card default and battery codec; exact old defaults upgrade to the approved arrangement. Custom three-card layouts preserve their cards and place battery in free 3x3 space, using least overlap only if no region fits (overlap remains an editor feature). |
| `BoardScreen.java` | Whole-surface ground fill; visible/stable/full fallback; API-gated EV profile/energy listener, SUCCESS-only values, lifecycle cleanup and DriveBoardCar diagnostic logs; localized refresh action. |
| `WidgetData.java` | Localized media/prayer/battery snapshots, actual update timestamps, five daily prayer rows and hours/minutes countdown; removed next-day estimation. |
| `Tone.java` | Single table for five requested ground/panel presets. |
| `Fonts.java` | Cached bundled font family access at 400/600/800. |
| `AppLocale.java` | Preference-based EN/ms configuration context and locale. |
| `ZoneCatalog.java` | Offline 60-zone code/name catalogue generated from live API. |
| `ZoneLocator.java` | Coarse network location, bounded current-fix request, recent last-known fallback, HTTPS zone resolution and validation, inline failure callback, saved check time. |
| `app/src/main/AndroidManifest.xml` | Coarse location, CAR_FUEL and CAR_INFO declarations; localized application/listener labels. |
| `app/src/main/res/font/atkinson_next.ttf` | Upright variable font from google/fonts `ofl/atkinsonhyperlegiblenext`. |
| `app/src/main/res/font/atkinson_regular.xml`, `atkinson_semibold.xml`, `atkinson_extrabold.xml` | Explicit 400/600/800 weight/variation family resources. |
| `app/src/main/res/values/strings.xml`, `values-ms/strings.xml` | EN/BM phone UI strings and tone names. |
| `app/src/main/res/values/board_strings.xml`, `values-ms/board_strings.xml` | EN/BM board/data labels and states. |
| `app/src/main/res/values/data_strings.xml`, `values-ms/data_strings.xml` | EN/BM countdown, permission/location, car action and listener labels. |
| `app/src/test/java/local/driveboard/BoardLayoutTest.java` | Extended grid/default/migration/invalid-layout coverage. |
| `ToneTest.java` (same test directory) | Preset completeness and cream/sage contrast regression test. |
| `ZoneCatalogTest.java` (same test directory) | Live catalogue completeness/uniqueness and important names/codes. |
| `licenses/atkinsonhyperlegiblenext/OFL.txt` | Upstream OFL licence. |
| `tools/fetch-font.cjs`, `tools/font-and-contrast.cjs` | Reproducible font download and font family/contrast generation. |
| `README.md` | Features, build/install, exact verified zone API, battery caveat, data behavior, contrast and drive checks. |
| `DESIGN.md` | Design decisions, visual review verdict and raster provenance. |
| `.impeccable/review/*` | Device captures, contrast ratios, endpoint evidence/catalog generator and build/install/runtime logs. |

PRODUCT.md and the supplied surface/sketch were read as inputs; their direction contract was not copied into app source or resources.

## Still unverified

The actual Android Auto surface and e.MAS EnergyLevel sharing require a drive. Check API/profile/status logs, battery/range, visible/stable regions, split-screen overlays, live tone/language/layout, disconnect/reconnect and listener cleanup. Phone runtime GPS grant/deny/network failures, real music access grant/revoke, battery live/stale visual states, offline prayer cache/midnight and BM at 130% remain manual checks. No fictional car values were injected for screenshots. Android 12/12L runtime compatibility is not device-tested.
