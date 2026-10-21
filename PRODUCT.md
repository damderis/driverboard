# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
One driver: the owner, in his own Proton e.MAS 5 (EV hatchback, Slate Grey; a rebadged Geely Xingyuan / EX2) in Malaysia, using wireless Android Auto daily. He glances at the car screen while driving and sets things up on the phone while parked. Muslim; listens to Morphe YouTube Music and the Quran app in the car.

## Product Purpose
DriveBoard is a personal car screen for Android Auto. The phone app arranges and configures it; the car shows it live. Success: the car screen answers "what time is it, when is the next prayer, what is playing, how much battery is left" in one glance, and changes made on the phone appear in the car without fuss.

## Positioning
Personal, not a product for sale. It is tuned to one car (Proton e.MAS head unit) and one person's routine, so it can skip the generality a public app needs. Reference for quality bar only: autodeck.my (a Malaysian Android Auto widget app). DriveBoard does not copy its code, assets or brand.

## Operating Context
- Car side: drawn on the Android Auto navigation surface (sideloaded, "Unknown sources" enabled). Viewed at arm's length, in daylight and at night, in short glances while driving. Touch is unsafe while moving.
- Phone side: used parked, to drag/resize widgets on a 12×6 grid and change settings.
- Companion project: audio-switch-android (routes Morphe/Quran to the car). May be merged in later if needed.

## Capabilities and Constraints
- Widgets in v1: clock and date, next prayer (JAKIM e-solat zone), now playing (MediaSession via notification access).
- Requested next: car battery % and range (via Android Auto CarInfo EnergyLevel if the e.MAS head unit exposes it; the Proton e.MAS phone app `com.malaysia.emas` is a possible second source), JAKIM zone from the phone's location, Google Maps.
- Open decisions: how Google Maps appears (a navigation shortcut that opens Google Maps on Android Auto vs. a map view inside the board); a 3D replica of the car (needs a licensed model asset; not started).
- Native Java Views drawn onto the car surface with `View.draw()`; WebView, video and map views need a VirtualDisplay later.
- Languages: English and Bahasa Melayu, with a setting. Prayer names stay in their Malay form (Subuh, Zohor, Asar, Maghrib, Isyak) in both.

## Brand Commitments
Name: DriveBoard. Visual language pinned by the owner: Material 3 Expressive (2026-10-21).

## Evidence on Hand
No user data beyond the owner's own phone. Do not invent features, car data or screenshots that have not been seen on the real car.

## Product Principles
1. Glance first: each widget answers one question at driving distance; nothing needs reading twice.
2. The phone is the workshop, the car is the display: configuration lives on the phone, the car never asks for input while moving.
3. Truthful data: show "—" or a clear state when data is missing or stale; never a guess dressed as fact.
4. One car, done well: tune to the e.MAS screen before generalising.

## Accessibility & Inclusion
Driving-distance legibility (large type, high contrast, day and night), no motion that distracts the driver.
