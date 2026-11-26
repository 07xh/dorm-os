# Complete upstream baseline — no UI rewrites

Active Android redesign baseline: upstream commit `bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed` from https://github.com/znjhahaha/zhengfang-apk. Expected Git tree: `9284dad51679829d1182f05b57b33490b464e611`.

The exact entire Git tree is imported into `apps/zhengfang-upstream/` by the isolated baseline workflow. All tracked files, executable modes, resources, licenses, dependencies, Gradle wrapper and directory structure are preserved. The previous Android implementation is retained outside that directory solely for business extraction and rollback; it must not become a UI foundation again. No upstream release or deployment workflow is enabled by nesting its original .github folder.

Gate 0: exact Git tree equality, original unit tests, original release-derived benchmark build. No school login or private credentials needed.
Gate 1: same physical device, original installed release vs benchmark baseline: matching refresh rate, page transition/scrolling, p50/p95/p99 and jank, page readiness. Human comparison of original screenshots and interactions is mandatory. No pass without actual evidence.
Gate 2: only after Gate 1 passes, integrate business data adapters and school-specific routes into original module boundaries. Preserve original Activity, renderer, animation, navigation, page code and dependency versions. Do not introduce a parallel design system, wrapper framework or custom replacement components. Review GPL-3.0 obligations before distributing modified builds.

Old branch backup: backup/android-component-rebuild-16640da. Production main is not changed. No original app source edited in this stage.

## Same-device baseline capture

The imported version.properties identifies version 1.0.135 (code 135). The upstream build uses its existing release-derived `benchmark` variant (`com.tyust.course.benchmark`, version suffix `-benchmark`, UI_PREVIEW=true); it is not identical to a production release build. No upstream source modification is needed for the isolated variant.

Use the existing read-only local ADB collector with the new baseline mode, after the APK artifact is available:

```powershell
node scripts/xiaoxu-ace6-visual-qa.mjs --upstream-baseline --apk "PATH_TO_BENCHMARK_APK" --commit bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed --page-timing
````

It defaults to comparing the benchmark package with com.tyust.course on the same Ace 6. Match original version, theme, wallpaper, refresh rate, workload, data, and device thermal state. Version strings do not establish source or production-build equivalence. If an equivalent reference is unavailable, collect evidence but do not claim Gate 1 passes. No credentials, school requests, or task mutations are needed. Captured data remains local and screenshots may include private information. Compare frame duration, jank and page-ready measurements manually before any business integration.
