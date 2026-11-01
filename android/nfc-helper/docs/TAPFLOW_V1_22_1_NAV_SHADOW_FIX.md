# TapFlow V1.22.1 — bottom navigation overlay and motion fix

## Reported symptom
An Android screenshot showed a large gray rectangular area occupying the full
width of one bottom navigation cell, and transitions felt worse than V1.21.

## Changes
- Replaced the nested Material3 NavigationBar (which adds layout/window-inset
  defaults) with a lightweight single 64dp Box and explicit bottom system
  navigation-bar inset. No shadow, elevation, blur, or large color block.
- The current full-cell clickable used its default rectangular ripple. The
  revised tab target is rounded, and uses a no-ripple interaction source because
  selected state already has the moving capsule and tint transition.
- The moving selection capsule remains but is limited to 54×29dp, not a cell-wide
  shadow. Its placement animation runs for 185ms.
- Screen transition no longer travels the entire screen width: entering pages
  move 1/9 of the viewport and exiting pages 1/16, in 210ms. Clipping prevents
  outgoing content from drawing into the fixed bottom navigation.
- Removed one redundant Compose key scope, retaining SaveableStateProvider and
  the existing three per-tab scroll states.
- No HorizontalPager, liquid-glass module, background root service, or NFC
  protocol changes.

## Verification
Android Debug APK/Unit tests and KSU ZIP CI must pass on this exact commit.
Device validation on OnePlus Ace 6 Android 16 is still needed for rapid taps,
rotation/insets, keyboard visibility, and smoothness. No claim of measured FPS.
