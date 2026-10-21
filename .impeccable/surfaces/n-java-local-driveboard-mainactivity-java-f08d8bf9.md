---
version: 1
slug: "n-java-local-driveboard-mainactivity-java-f08d8bf9"
primary_target: "app/src/main/java/local/driveboard/MainActivity.java"
related_targets: ["app/src/main/java/local/driveboard/BoardView.java","app/src/main/java/local/driveboard/BoardScreen.java"]
---

# DriveBoard surfaces: phone editor + Android Auto car board

Scope: the phone editor (MainActivity) and the car board (BoardView drawn by BoardScreen). Mode: Operate. Build path: code-led. Chosen sketch: .impeccable/mocks/decision/safer-masjid.html.

Audience and job: the owner, parked, arranges widgets and settings on the phone; while driving, glances at the car screen for time, next prayer, now playing, battery.

## Direction contract

THESIS: Deep teal panels with brass details, like a Malaysian masjid interior; one eight-point star marks the next prayer and nothing else is ornamented. Refuses the category default of near-black tiles with a neon accent.

OWN-WORLD: Car board: teal #0F3D3E ground, #16494A panels, cream #F4EFE4 type, sage #A9C2BD labels, brass #C8A55A for the next prayer and its countdown only. Phone: limestone #EDE6DA ground, #FAF7F1 list card, ink #1D2423, muted #5F6763, hairline #E3DCCF, filled teal pill buttons; dark mode swaps to the car palette. Type: Atkinson Hyperlegible Next, bundled; tabular numerals. The star is two overlapping squares stroked in brass.

STORY: The driver reads the next prayer and its countdown in one glance, knows what is playing and how much battery is left, and trusts that "—" means missing data, never a guess.

FIRST VIEWPORT: Phone: title "DriveBoard" in teal; the board preview at the car's aspect ratio, full width; one hint line; a single settings card of 60dp rows: Prayer zone with a "Use location" button, Now playing access, Car battery status, Language EN/BM. Car: clock at left spanning full height, star plus "Zohor 13:01" plus brass countdown top right, now playing and battery below.

FORM: Masjid Stone & Teal, grounded candidate 3 of the safer re-roll (seed:84ca9e5bc0fa4afd).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Constraints
- Native Android Views (Java), no Material Components library; system Back, edge-to-edge insets, 48dp touch targets, sp text sizes.
- Car side draws with View.draw() onto the navigation surface; no touch while driving.
- State vocabulary: live, stale (dimmed plus "updated HH:mm"), missing ("—"), needs permission, waiting (amber #8A6100 text).

## Open decisions
- Google Maps on the car board (shortcut vs. embedded map).
- 3D car replica (needs a licensed model).
