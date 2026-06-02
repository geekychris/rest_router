#!/usr/bin/env bash
# End-to-end smoke test against a running gateway (defaults: docker-compose).
#
# Usage:
#   ./scripts/smoke.sh [GATEWAY_URL] [ADMIN_KEY] [API_KEY]
#
# Exits non-zero on first failure.

set -euo pipefail

GW="${1:-http://localhost:8080}"
ADMIN="${2:-local-admin-key}"
KEY="${3:-demo-basic-secret}"

bold() { printf '\033[1m%s\033[0m\n' "$1"; }
ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }
fail() { printf '  \033[31m✗\033[0m %s\n' "$1"; exit 1; }

bold "1. Gateway is up"
curl -fsS "$GW/actuator/health/liveness" >/dev/null && ok "liveness 200" || fail "liveness"

bold "2. Anonymous request is allowed (servicea) and rate-limited"
status=$(curl -s -o /dev/null -w '%{http_code}' "$GW/servicea/health")
[ "$status" = "200" ] || [ "$status" = "404" ] && ok "anon -> $status" || fail "anon got $status"

bold "3. Authenticated request carries identity"
out=$(curl -s -D - -o /dev/null "$GW/servicea/test" -H "X-API-Key: $KEY")
echo "$out" | grep -qi 'X-RateLimit-Limit' && ok "rate-limit headers present" || fail "no rate-limit headers"

bold "4. Auth-required service rejects anonymous"
status=$(curl -s -o /dev/null -w '%{http_code}' "$GW/serviceb/anything")
[ "$status" = "401" ] && ok "401 as expected" || fail "expected 401, got $status"

bold "5. Auth-required service accepts API key"
status=$(curl -s -o /dev/null -w '%{http_code}' "$GW/serviceb/anything" -H "X-API-Key: $KEY")
[ "$status" != "401" ] && ok "auth accepted -> $status" || fail "still 401 with key"

bold "6. Admin endpoint demands admin key"
status=$(curl -s -o /dev/null -w '%{http_code}' "$GW/admin/services")
[ "$status" = "401" ] && ok "no key -> 401" || fail "expected 401, got $status"
status=$(curl -s -o /dev/null -w '%{http_code}' "$GW/admin/services" -H "X-Admin-Key: $ADMIN")
[ "$status" = "200" ] && ok "with key -> 200" || fail "expected 200, got $status"

bold "7. Issue and use an API key end-to-end"
issued=$(curl -fsS -X POST "$GW/admin/apikeys" \
  -H "X-Admin-Key: $ADMIN" -H 'Content-Type: application/json' \
  -d '{"principalId":"smoke-user","tier":"premium"}')
new_key=$(echo "$issued" | sed -E 's/.*"key":"([^"]+)".*/\1/')
[ -n "$new_key" ] && ok "minted key" || fail "key mint failed: $issued"
status=$(curl -s -o /dev/null -w '%{http_code}' "$GW/servicea/x" -H "X-API-Key: $new_key")
[ "$status" != "401" ] && ok "minted key works -> $status" || fail "minted key rejected"

bold "8. Prometheus metrics exposed"
curl -fsS "$GW/actuator/prometheus" | grep -q '^gateway_requests_total' && ok "gateway metrics present" || fail "metrics missing"

echo
bold "All smoke checks passed."
