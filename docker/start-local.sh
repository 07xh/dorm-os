#!/bin/sh
set -eu
cd /app
# Use exactly the same local database ID and persistence path for both commands.
# The fixed config contains no production database ID or remote bindings.
wrangler d1 migrations apply ACADEMIC_DB --local --config wrangler.local.toml --persist-to /data
exec wrangler pages dev public --config wrangler.local.toml --ip 0.0.0.0 --port 8788 --persist-to /data --show-interactive-dev-session=false
