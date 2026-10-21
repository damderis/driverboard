# DriveBoard design record

Reviewed 2026-10-21 against the approved safer-masjid sketch and surface brief.

The phone is a quiet limestone editor: tone-coloured 26sp ExtraBold title, full-width rounded car preview, a short editing hint, and one settings card. Each settings row has at least 60dp height; buttons and swatches have at least 48dp touch area. The screen scrolls and respects system bars/cutouts. System night uses the selected tone's ground/panel with cream text.

The shared board places the clock in the left third, prayer across the upper right, and music/battery beneath. Rounded panels, restrained labels and scale establish hierarchy. Only the prayer has an outlined eight-point star. Brass also identifies the phone's selected swatch and editing corner, as explicitly requested. Missing battery and permission states replace the sketch's fictional samples.

Atkinson Hyperlegible Next is bundled as the upright Google Fonts variable TTF with 400/600/800 family resources. The font's slashed zero is intentional. Native phone text uses sp; board text scales from widget geometry so the preview matches the car rather than adopting a separate phone layout. Clock, prayer, countdown and battery drawing uses tabular numerals. Dates are abbreviated to keep the clock card readable. Long music titles ellipsize at a useful size.

All five requested panels pass cream/sage 4.5:1 contrast without adjustment; detailed ratios are in README and `.impeccable/review/contrast.json`. Dim text denotes historical/past values. Light Waiting uses #8A6100; dark Waiting uses a lighter amber for legibility.

Astra 6 reviewed implementation and all four required screenshots: visual pass, no visible clipping, overlap, inset problems or broken controls. BM was additionally inspected at normal text size. Remaining review requires an actual car: driving-distance type, host overlays/split screens and EV telemetry availability. Live/stale battery visual states and BM at 130% were not exercised on hardware.

## Raster provenance

No new shipping bitmap asset was created. Panels, star, swatches and handles are native geometry; the existing launcher icon is unchanged. Review PNGs are unedited adb screencaps of the connected Nothing A142 (1084 x 2412), 2026-10-21. They contain real clock/prayer data and truthful missing battery/notification access, not mock content. `before-phone.png` predates this change and was already present. Required final captures: phone-light.png, phone-dark.png, phone-font130.png, phone-tone-navy.png; extra language check: phone-bm.png.
