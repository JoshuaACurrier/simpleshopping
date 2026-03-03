# Simple Shopping

A fully offline Android shopping list app with a notepad-inspired design.

![Screenshot](screenshot.png)

## Features

- **Section-based lists** -- Organize items under categories like Produce, Dairy, Bakery, etc.
- **Create & Shopping modes** -- Build your list in Create mode, then switch to Shopping mode to cross items off
- **Recurring items** -- Star items so they come back every trip
- **Inline add** -- Tap a category header to quickly add items with autocomplete from history
- **Drag to reorder** -- Long-press categories to rearrange or delete them
- **Collapsible sections** -- Tap category headers in Shopping mode to collapse/expand
- **"I got it!" grouping** -- Optionally move checked items to a separate section while shopping
- **New Trip / Repeat Last Trip** -- Clear checked items or replay a previous trip's list
- **Store route sorting** -- Sort items by the order you typically check them off
- **Interactive tutorial** -- 9-step guided walkthrough using a demo list (real data untouched)
- **Notepad theme** -- Handwritten font (Patrick Hand), ruled lines, and red margin

## Tech Stack

- **Language:** Kotlin
- **Min SDK:** 26 (Android 8.0) / **Target SDK:** 35 (Android 15)
- **UI:** View Binding, Material Design 3, RecyclerView
- **Persistence:** Room (fully offline, no network)
- **Architecture:** MVVM -- ViewModel + StateFlow, single Activity
- **Tutorial:** [Spotlight](https://github.com/TakuSemba/Spotlight) library

## Building

```bash
./gradlew assembleDebug    # Build debug APK
./gradlew installDebug     # Build and install on connected device/emulator
```

Requires JDK 17 and the Android SDK.

## License

All rights reserved.
