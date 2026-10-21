---
version: 1
slug: "n-java-local-driveboard-mainactivity-java-f08d8bf9"
primary_target: "app/src/main/java/local/driveboard/MainActivity.java"
related_targets: ["app/src/main/java/local/driveboard/BoardView.java","app/src/main/java/local/driveboard/BoardScreen.java"]
---

# DriveBoard surfaces: phone editor + Android Auto car board

Scope: the phone editor (MainActivity) and the car board (BoardView drawn by BoardScreen), plus the car lyrics view. Mode: Operate. Build path: code-led.

Audience and job: the owner, parked, arranges widgets and settings on the phone; while driving, glances at the car screen for time, next prayer, now playing, battery; taps now playing for lyrics.

History: Masjid Stone & Teal (seed:84ca9e5bc0fa4afd) was built and rejected by the user on 2026-10-21 as cheap-looking. The user pinned Material 3 Expressive; a user-pinned direction beats the roll.

## Direction contract

THESIS: Google's Material 3 Expressive on the car: colour lifted from the song's album art or the wallpaper, every widget in its own expressive shape, confident flexible type. Refuses identical dark tiles with one accent.

OWN-WORLD: M3 tonal dark scheme from a seed (wallpaper by default, album art, or presets); containers in distinct roles (surfaceContainerHigh, primaryContainer, secondaryContainer, tertiaryContainer); shapes from the M3 shape library: large rounded squares, pill-ended prayer bar, cookie-clipped album art, a rounded eight-point burst marking the next prayer; Google Sans Flex or Roboto Flex with variable weight and width; the M3 wavy progress line.

STORY: One glance tells the time, the next prayer and how long until it, what is playing (with its art), and battery; "—" means missing. Tapping the song opens synced lyrics.

FIRST VIEWPORT: Car (16:9): clock container left at full height; pill-ended prayer bar across the top right with the burst, name, time and countdown, five-prayer row beneath; now-playing container with large cookie art, two-line title, artist and wavy progress; battery container. Phone: M3 Expressive top app bar, the board preview, M3 settings list.

FORM: user-pinned Material 3 Expressive (no roll). seed: none (unavailable)

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Constraints
- Car side draws with View.draw() onto the navigation surface; no animation while driving; Android Auto touch only for opening/closing lyrics.
- Phone follows Material 3 (Expressive) components, system Back, edge-to-edge, 48dp targets.
