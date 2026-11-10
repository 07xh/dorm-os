#!/usr/bin/env python3
"""Optional, pinned upstream NCWU public-notice collector.

Only emits a bounded, read-only intermediate snapshot. The existing Node
collector validates official article URLs and owns publication, archival state,
deduplication, safety policy, and notification UI.
"""
import json
import pathlib
import re
import sys
from urllib.parse import urlparse

ROOT = pathlib.Path(__file__).resolve().parent.parent
UPSTREAM = ROOT / "_third_party" / "ncwu-crawler"
sys.path.insert(0, str(UPSTREAM))

# Scoped to public notification columns with verified source definitions.
SOURCES = {
    "dianqi": "dianqi",
    "cxcy": "gcxlzx",  # Upstream source key differs from its official URL path.
    "qysy": "qysy",
}
ARTICLE_RE = re.compile(r"^/([a-z0-9]+)/info/\d{3,6}/\d{1,8}\.htm$")


def capture(site_key, article_prefix, crawler):
    try:
        notices = crawler.list_notices(site=site_key, column="tzgg", page=1)
        items = []
        seen = set()
        for notice in notices[:60]:
            link = urlparse(str(notice.url or ""))
            title = " ".join(str(notice.title or "").split())
            match = ARTICLE_RE.fullmatch(link.path)
            if (link.scheme != "https" or link.hostname != "www2.ncwu.edu.cn" or
                    link.port is not None or link.username or link.password or
                    link.query or link.fragment or match is None or
                    match.group(1) != article_prefix or len(title) < 8 or len(title) > 160):
                continue
            url = link._replace(params="", query="", fragment="").geturl()
            if url in seen:
                continue
            seen.add(url)
            # Upstream list dates may be inferred from adjacent items or today's
            # year. Preserve only the independently verifiable title and URL;
            # never silently certify a guessed publication year.
            items.append({"url": url, "title": title, "date": None})
        if len(items) < 2:
            return {"error": "官方列表解析不足两条有效公告，不更新此来源"}
        return {"items": items[:40]}
    except Exception as exc:
        # Never print raw upstream pages, secrets or response bodies.
        return {"error": "公开来源读取失败: " + type(exc).__name__}


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: ncwu-upstream-notices.py <output-json>")
    from ncwu_crawler import Crawler
    crawler = Crawler(sleep=1.0, timeout=15, retries=1)
    results = {prefix: capture(site_key, prefix, crawler)
               for site_key, prefix in SOURCES.items()}
    target = pathlib.Path(sys.argv[1])
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps({"sources": results}, ensure_ascii=False), encoding="utf-8")
    for key, result in results.items():
        print(key + ": " + ("error" if "error" in result else str(len(result["items"])) + " public notices"))


if __name__ == "__main__":
    main()
