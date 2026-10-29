#!/usr/bin/env bash
# Teste de fumaça da API no ar: health, negação por padrão e documentação.
# Uso: scripts/smoke-test.sh [url-base]    (padrão: http://localhost:8080)
# Precisa só de bash, curl e grep. Sai com código 1 na primeira verificação que falhar.
set -euo pipefail

BASE="${1:-http://localhost:8080}"
TRACE_ID="4bf92f3577b34da6a3ce929d0e0e4736"

fail() { echo "FALHOU: $*" >&2; exit 1; }
ok() { echo "ok   $*"; }

# 1. Health público, só com o status agregado.
body=$(curl -sS -w '\n%{http_code}' "$BASE/actuator/health") || fail "health inacessível em $BASE"
[ "$(tail -n1 <<<"$body")" = 200 ] || fail "health: esperado 200, veio $(tail -n1 <<<"$body")"
[ "$(head -n1 <<<"$body")" = '{"status":"UP"}' ] || fail "health: corpo inesperado: $(head -n1 <<<"$body")"
ok "GET /actuator/health -> 200 {\"status\":\"UP\"}"

# 2. Rota não pública sem credencial: 401 no formato RFC 9457, com o traceId do traceparent.
headers=$(mktemp)
trap 'rm -f "$headers"' EXIT
body=$(curl -sS -D "$headers" -H "traceparent: 00-$TRACE_ID-00f067aa0ba902b7-01" -w '\n%{http_code}' "$BASE/api/v1/qualquer")
[ "$(tail -n1 <<<"$body")" = 401 ] || fail "/api/v1/qualquer: esperado 401, veio $(tail -n1 <<<"$body")"
grep -qi '^content-type: application/problem+json' "$headers" || fail "/api/v1/qualquer: content-type não é application/problem+json"
grep -q '"type":"urn:nexa-finance:problem:unauthorized"' <<<"$body" || fail "/api/v1/qualquer: type diferente de unauthorized"
grep -q "\"traceId\":\"$TRACE_ID\"" <<<"$body" || fail "/api/v1/qualquer: traceId do traceparent não voltou no corpo"
ok "GET /api/v1/qualquer -> 401 problem+json, traceId $TRACE_ID"

# 3. Documentação pública: OpenAPI 3.1 e Swagger UI.
curl -sS "$BASE/v3/api-docs" | grep -q '"openapi":"3\.1' || fail "/v3/api-docs não é OpenAPI 3.1"
ok "GET /v3/api-docs -> OpenAPI 3.1"
[ "$(curl -sS -L -o /dev/null -w '%{http_code}' "$BASE/swagger-ui.html")" = 200 ] || fail "/swagger-ui.html não abre"
ok "GET /swagger-ui.html -> 200 (após redirect)"

echo "Teste de fumaça: tudo certo em $BASE"
