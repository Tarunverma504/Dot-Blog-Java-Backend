#!/usr/bin/env bash
# Run auth-service (port 8081) and MongoDB first, then: ./scripts/test-auth-curl.sh
# Or run via Gateway (port 8080): set BASE_URL=http://localhost:8080

set -e
BASE_URL="${BASE_URL:-http://localhost:8081}"
echo "Testing auth endpoints at $BASE_URL"

echo "1. Register..."
REGISTER_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v2/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"CurlTest","email":"curltest@example.com","password":"test123"}')
HTTP_CODE=$(echo "$REGISTER_RESP" | tail -n1)
BODY=$(echo "$REGISTER_RESP" | sed '$d')
if [ "$HTTP_CODE" != "200" ]; then
  echo "  FAIL: expected 200, got $HTTP_CODE - $BODY"
  exit 1
fi
echo "$BODY" | grep -q verificationToken && echo "  OK: verificationToken present" || { echo "  FAIL: no verificationToken"; exit 1; }

echo "2. Login..."
LOGIN_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v2/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"curltest@example.com","password":"test123"}')
HTTP_CODE=$(echo "$LOGIN_RESP" | tail -n1)
BODY=$(echo "$LOGIN_RESP" | sed '$d')
if [ "$HTTP_CODE" != "200" ]; then
  echo "  FAIL: expected 200, got $HTTP_CODE - $BODY"
  exit 1
fi
TOKEN=$(echo "$BODY" | sed -n 's/.*"authToken":"\([^"]*\)".*/\1/p')
[ -z "$TOKEN" ] && { echo "  FAIL: no authToken in response"; exit 1; }
echo "  OK: authToken received"

echo "3. IsAuthenticated (with Bearer token)..."
AUTH_RESP=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/v2/isAuthenticated" \
  -H "Authorization: Bearer $TOKEN")
HTTP_CODE=$(echo "$AUTH_RESP" | tail -n1)
BODY=$(echo "$AUTH_RESP" | sed '$d')
if [ "$HTTP_CODE" != "200" ]; then
  echo "  FAIL: expected 200, got $HTTP_CODE - $BODY"
  exit 1
fi
echo "$BODY" | grep -q '"name":"CurlTest"' && echo "  OK: name and auth OK" || { echo "  FAIL: $BODY"; exit 1; }

echo "4. IsAuthenticated (no token) -> 401..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X GET "$BASE_URL/api/v2/isAuthenticated")
[ "$HTTP_CODE" = "401" ] && echo "  OK: 401 as expected" || { echo "  FAIL: expected 401, got $HTTP_CODE"; exit 1; }

echo ""
echo "All auth curl tests passed."
