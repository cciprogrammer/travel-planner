#!/usr/bin/env bash
# ------------------------------------------------------------------
# Re-encode project .cmd files: UTF-8 (editor output) -> GBK (cp936) + CRLF.
#
# WHY: Chinese Windows cmd.exe reads .cmd files with the OEM codepage (936).
#      If a .cmd contains UTF-8 Chinese, cmd mis-decodes byte pairs and can
#      swallow line breaks, which silently skips real statements (e.g. a
#      `set "NODE_HOME=..."` line disappears and later steps see empty vars).
#      So: edit .cmd files in UTF-8 (as usual), then run this script.
#      Already-GBK files are detected and skipped untouched — safe to re-run.
#
# USAGE: bash scripts/tools/to-gbk.sh
# VERIFY: iconv -f GBK -t UTF-8 start.cmd | head
# ------------------------------------------------------------------
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1

TARGETS="start.cmd stop.cmd \
scripts/_common.cmd scripts/start-backend.cmd scripts/start-frontend.cmd \
scripts/status.cmd scripts/init-db.cmd"

rc=0
for f in $TARGETS; do
  if [ ! -f "$f" ]; then
    echo "missing, skip: $f"
    continue
  fi
  if ! iconv -f UTF-8 -t GBK "$f" > /dev/null 2>&1; then
    echo "skip (not UTF-8, likely already GBK): $f"
    continue
  fi
  # strip BOM -> normalise to CRLF -> convert UTF-8 to GBK
  sed '1s/^\xEF\xBB\xBF//' "$f" \
    | sed -e 's/\r$//' -e 's/$/\r/' \
    | iconv -f UTF-8 -t GBK > "$f.tmp" || { echo "FAIL: $f"; rc=1; continue; }
  mv "$f.tmp" "$f"
  echo "gbk ok: $f"
done
exit $rc
