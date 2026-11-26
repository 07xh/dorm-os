# Complete upstream baseline — no UI rewrites

Active Android redesign baseline: upstream commit `bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed` from https://github.com/znjhahaha/zhengfang-apk. Expected Git tree: `9284dad51679829d1182f05b57b33490b464e611`.

The exact entire Git tree is imported into `apps/zhengfang-upstream/` by the isolated baseline workflow. All tracked files, executable modes, resources, licenses, dependencies, Gradle wrapper and directory structure are preserved. The previous Android implementation is retained outside that directory solely for business extraction and rollback; it must not become a UI foundation again. No upstream release or deployment workflow is enabled by nesting its original .github folder.

Gate 0: exact Git tree equality, original unit tests, original release-derived benchmark build. No school login or private credentials needed.
Gate 1: same physical device, original installed release vs benchmark baseline: matching refresh rate, page transition/scrolling, p50/p95/p99 and jank, page readiness. Human comparison of original screenshots and interactions is mandatory. No pass without actual evidence.
Gate 2: only after Gate 1 passes, integrate business data adapters and school-specific routes into original module boundaries. Preserve original Activity, renderer, animation, navigation, page code and dependency versions. Do not introduce a parallel design system, wrapper framework or custom replacement components. Review GPL-3.0 obligations before distributing modified builds.

Old branch backup: backup/android-component-rebuild-16640da. Production main is not changed. No original app source edited in this stage.
