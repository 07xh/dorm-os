# Second-class automatic read

Spec: ../specs/2026-11-06-secondclass-auto-read-design.md

- [x] JVM RED: policy did not exist in run42243840889; eligibility, foreground/background consent, cooldown, binding and stale-save tests then compiled/passed in run42245978329.
- [x] Implement bounded cancellable WebView driver reusing asset/parsers/Room, manual reservation, atomic binding and stale transaction guard. API36 detached-context synthetic smoke passed in run42245978116; owner runtime remains separate.
- [x] Wire Activity-owned foreground lifecycle, opt-in WorkManager and existing card controls. Review's Compose-scope foreground job issue removed; controls only configure.
- [ ] Local checks, Android/JVM, independent review, exact-head CI, merge/main verification; installed-package/signing and device acceptance separately.
