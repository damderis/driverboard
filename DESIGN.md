# DriveBoard design record

## Round 7 ? Material 3 Expressive (2026-10-21)

This section supersedes the historical limestone/Atkinson direction below. The user-pinned contract is .impeccable/surfaces/n-java-local-driveboard-mainactivity-java-f08d8bf9.md. Existing references m3-board5.png and m3-lyrics4.png under .impeccable/review/car are not screenshots of round 7.

The board keeps distinct dark tonal containers, cookie artwork, the prayer burst and wavy progress. Song titles wrap at word boundaries into at most two lines, with ellipsis at the end; ordinary long words may reduce type by up to 18% before an oversized word must split. The clock uses the complete compact date and font metrics to centre its time/date block. Prayer names sit above times with a gap of 0.28 times the time size; the redundant small next-prayer dot is removed. Localised countdowns omit zero hours and round remaining minutes up. Battery text is centred inside a padded inscribed square when the container is circular, with a modest missing-value dash and secondary-size two-line status.

The phone uses Java MDC Views and a Material3Expressive dynamic-colour DayNight theme with bundled Google Sans Flex typography. Wallpaper remains the default source; album art and presets use the same seed from BoardColours as the car. The preview remains editable within a large rounded MaterialCardView. Connected toggle groups, colour chips, rounded settings, permission access, zone selection/location, background import/removal, language selection and confirmed reset preserve existing functions. Insets include system bars and cutouts; settings scroll and controls target at least 48dp. System Back retains platform/AppCompat behaviour.

MDC 1.14.0 offers Expressive themes, button groups/styles, lists, top app bars, shape motion and LoadingIndicator in Views. Sources: [official 1.14.0 release notes](https://github.com/material-components/material-components-android/releases/tag/1.14.0), linked official component documentation, and the locally resolved 1.14.0 AAR. No Kotlin or Compose was added.

Review verdict: Astra 6 source review finds the car geometry coherent at default full 1768?956/1020 and split 1144?956 proportions, including battery safe area and prayer separation. This is source review, not visual acceptance. BoardScreen is untouched: software lockCanvas(null), host surface ownership and the stale-destroy guard remain. Lyrics drawing is unchanged. Final verification: gradlew test assembleDebug passed with ANDROID_USER_HOME set to this workspace?s .android; all 14 tests passed in both debug and release variants. git diff --check passed. adb install -r was attempted and failed with ?no devices/emulators found?; the authorised single MainActivity launch and logcat crash check remain unperformed. No phone taps or settings changes were made, and Git was used read-only. Visual acceptance at the three car sizes, EN/BM, DayNight, large text and live battery states remains with the owner.

Raster provenance: no new shipping raster was generated. Existing Google Sans Flex and launcher assets are reused; the new colour dot is vector geometry. Album art and chosen backgrounds remain user/media-provided runtime content. No new device screenshots were captured.

## Historical records (before round 7)

Reviewed 2026-10-21 against the approved safer-masjid sketch and surface brief.

The phone is a quiet limestone editor: tone-coloured 26sp ExtraBold title, full-width rounded car preview, a short editing hint, and one settings card. Each settings row has at least 60dp height; buttons and swatches have at least 48dp touch area. The screen scrolls and respects system bars/cutouts. System night uses the selected tone's ground/panel with cream text.

The shared board places the clock in the left third, prayer across the upper right, and music/battery beneath. Rounded panels, restrained labels and scale establish hierarchy. Only the prayer has an outlined eight-point star. Brass also identifies the phone's selected swatch and editing corner, as explicitly requested. Missing battery and permission states replace the sketch's fictional samples.

Atkinson Hyperlegible Next is bundled as the upright Google Fonts variable TTF with 400/600/800 family resources. The font's slashed zero is intentional. Native phone text uses sp; board text scales from widget geometry so the preview matches the car rather than adopting a separate phone layout. Clock, prayer, countdown and battery drawing uses tabular numerals. Dates are abbreviated to keep the clock card readable. Long music titles ellipsize at a useful size.

All five requested panels pass cream/sage 4.5:1 contrast without adjustment; detailed ratios are in README and `.impeccable/review/contrast.json`. Dim text denotes historical/past values. Light Waiting uses #8A6100; dark Waiting uses a lighter amber for legibility.

Astra 6 reviewed implementation and all four required screenshots: visual pass, no visible clipping, overlap, inset problems or broken controls. BM was additionally inspected at normal text size. Remaining review requires an actual car: driving-distance type, host overlays/split screens and EV telemetry availability. Live/stale battery visual states and BM at 130% were not exercised on hardware.

## Raster provenance

No new shipping bitmap asset was created. Panels, star, swatches and handles are native geometry; the existing launcher icon is unchanged. Review PNGs are unedited adb screencaps of the connected Nothing A142 (1084 x 2412), 2026-10-21. They contain real clock/prayer data and truthful missing battery/notification access, not mock content. `before-phone.png` predates this change and was already present. Required final captures: phone-light.png, phone-dark.png, phone-font130.png, phone-tone-navy.png; extra language check: phone-bm.png.

## Round 3 (2026-10-21)

This revision removes board section headings, centres the larger glance content, reserves brass for the prayer marker/countdown, and uses sage edit handles. Dark phone controls use cream; tone selection has a separated ink/cream ring. The preview has a dark-mode hairline. Past prayers use regular weight; stale text uses contrast-checked #9DB3AF. Car night mode reduces cream brightness and uses a regular clock. Imminent prayer has a filled marker and minutes-only countdown without motion.

The parked-phone My car editor accepts only the owner's selected picture, stores a downscaled private PNG, and previews connected paint recolouring with preserved shading. No car photograph is bundled. The board uses only the saved render, cached by its preference revision. Source image provenance belongs to the user-selected file.

Round 3 review is source review by Astra 6 plus automated tests/build. No screenshots or phone interaction were performed; visual acceptance remains with the owner. Earlier screenshot verdicts above describe the previous revision only.
