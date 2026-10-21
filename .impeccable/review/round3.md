# DriveBoard round 3 verification

2026-10-21. PRODUCT.md and the specified Direction contract were read before implementation. Contract prose was not copied into code. Astra 6 monitored and reviewed source changes; Sol handled the complex renderer/editor work.

## Automated results

Built with ANDROID_USER_HOME set to this workspace's `.android` and GRADLE_USER_HOME to `.gradle-user-home`.

- `gradlew.bat test assembleDebug`: **BUILD SUCCESSFUL in 4s**; 57 tasks, 23 executed, 34 up-to-date.
- Debug and release each passed 10 JUnit tests: BoardLayoutTest 4, CarPaintTest 4, ToneTest 1, ZoneCatalogTest 1. Zero failures/errors.
- CarPaint tests cover 5x5 averaging, connected grey selection excluding separate grey/black regions, preserved shading/alpha, and transparent taps.
- ToneTest checks cream, sage and DIM against all five panels at 4.5:1. Emerald panel was slightly darkened to #154A3B to pass DIM contrast.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`: **Performing Streamed Install / Success**.
- Only nonfatal deprecated API/Gradle deprecation notices remain.
- Full logs: `round3-build-output.txt`, `round3-install-output.txt` beside this file.

Git was used only for status/diff inspection. No staging or commit. No phone taps, launch, settings/permissions/uimode/font-scale changes, screenshots, Chromium or Playwright. Earlier verification.md and screenshot verdicts apply to the preceding revision only.

## Source review

All board headings removed; larger content centered; sage handles; dark cream controls; separated selection rings; preview edge; zone Change action; confirmed reset. Car night mode follows host configuration and uses regular clock/85% cream. Prayer imminent uses actual Malaysia time within 900 seconds, a filled star and localized minutes. Saved car renders are cached by preference revision and fitted above the battery line; stale/partial data remains explicit.

My car uses a system picker with no storage permission, private PNG source capped at 1024 pixels, native editor, debounced worker recolouring and persisted normalized selection/settings/render. No car image is bundled. Choosing a replacement updates the editable source; Cancel leaves the previously saved board render unchanged. The colour mask selects only connected similar pixels, so adjacent similar body/background areas can require tolerance adjustment.

## Owner acceptance checks

1. Phone 16:9 preview: large readable clock/prayer/media/battery, no heading labels or excessive dead bands; missing battery dash and single short line. Check light/dark, all tones, EN/BM, and larger text.
2. Zone Change and Use location; reset Cancel preserves layout, Reset restores it; buttons have no shadows; dark preview edge and gapped tone ring remain visible.
3. Choose your e.MAS image: picker cancel, transparent PNG and large image import. Tap paint; vary tolerance/presets/hue/brightness; confirm disconnected background stays unchanged and shading survives. Save, reopen editor, Original, Cancel, replacement, and app restart.
4. Car parked: saved image aspect fit/centering, live phone edits, range-only/percent-only telemetry, stale Updated detail, no-data state. Change host day/night and confirm immediate redraw with regular/dimmer clock at night.
5. Prayer countdown with sage zone suffix; at 15 minutes the brass star fills and countdown reads minutes only. Confirm EN/BM and no animation.

No visual/hardware acceptance is claimed in this round.

## Changed files

- `app/src/main/java/local/driveboard/MainActivity.java`
- `app/src/main/java/local/driveboard/BoardView.java`
- `app/src/main/java/local/driveboard/BoardScreen.java`
- `app/src/main/java/local/driveboard/DriveBoardService.java`
- `app/src/main/java/local/driveboard/Tone.java`
- `app/src/main/java/local/driveboard/CarEditorActivity.java` (new)
- `app/src/main/java/local/driveboard/CarPaint.java` (new)
- `app/src/main/java/local/driveboard/CarPictureStore.java` (new)
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/board_strings.xml` and `values-ms/board_strings.xml`
- `app/src/main/res/values/car_strings.xml` and `values-ms/car_strings.xml` (new)
- `app/src/main/res/values/review_strings.xml` and `values-ms/review_strings.xml` (new)
- `app/src/test/java/local/driveboard/ToneTest.java`
- `app/src/test/java/local/driveboard/CarPaintTest.java` (new)
- `README.md`, `DESIGN.md`, and this round's review/log files.
