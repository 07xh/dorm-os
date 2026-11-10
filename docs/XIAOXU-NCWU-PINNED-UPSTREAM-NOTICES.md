# 华水公开通知：固定版本上游采集器接入

## Architecture (one archive, not two services)

Existing system is the owner of truth:
- GitHub Actions: .github/workflows/collect-ncwu-notices.yml
- Built-in Node collector: scripts/ncwu-notices.mjs
- Archive: public/data/notices.json (xiaoxu/notices-v1)
- Cloudflare Pages API: /api/notices
- Public notice inbox: public/xiaoxu-notices.js

An **optional** read-only subprocess runs the pinned MIT-licensed public
awecsfgvs/ncwu-crawler at commit 31c505b74bcf40c47aa720c2e282afe081a3609b
in the existing GitHub Actions run. It is checked out transiently and is not
vendored or shipped in the Android APK. No new cron worker, database, login,
push provider, or UI is introduced.

The adapter scripts/ncwu-upstream-notices.py emits a bounded intermediate
JSON file into RUNNER_TEMP. The Node collector independently checks titles,
article URLs, allowed official department paths, dates and record counts,
then merges successful batches into the *existing* verified archive.

## Phase 1: three targeted public sources

- dianqi: https://www2.ncwu.edu.cn/dianqi/xydt/tzgg.htm
- gcxlzx: https://www2.ncwu.edu.cn/gcxlzx/zxdt/tzgg.htm
  (upstream parser key: cxcy)
- qysy: https://www2.ncwu.edu.cn/qysy/sydt/tzgg.htm

The upstream project already supplies dedicated parser types for these sources:
sudy, portal, and card, respectively.

Only article links on these exact host/path families may be admitted to the
archive. Other hosts, unknown departments, non-HTTPS links, query parameters,
and invalid titles are rejected. Missing or MM-DD-only dates remain null;
the upstream's neighboring-article year inference is not imported.

## Failure behavior

Upstream checkout, Python execution, or an individual public site may fail.
The original three-source collector continues; invalid supplemental batches
never wipe previous verified articles. The existing API and user read/unread
state remain unchanged. This cannot bypass university authorization or scraping
restrictions.

## Verification

- npm run notices:test (fixtures and explicit allowlist / fail-closed tests)
- node scripts/xiaoxu-notice-inbox-tests.cjs
- GitHub Actions scheduled/dispatch run with authenticated live source status
- Verify actual new official article URLs, missing-year behavior, and no
  duplicate notice alerts between the original and additional public sources

## Licensing

Upstream ncwu-crawler is MIT, copyright (c) 2026 ncwu-crawler contributors.
No upstream source files are copied into this repository; the action checks
out the upstream repository at the pinned SHA for runtime use. Preserve its
LICENSE file, retain this provenance, and audit any future SHA upgrade.
