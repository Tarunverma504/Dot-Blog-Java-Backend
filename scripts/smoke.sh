#!/usr/bin/env bash
# =============================================================================
# Dot-Blog end-to-end smoke test
# -----------------------------------------------------------------------------
# Walks the full happy path against the gateway:
#   register -> login -> isAuthenticated -> create blog -> publish ->
#   list (visible) -> like -> comment -> delete-blog (owner) ->
#   list (gone) -> get-blog (404) -> delete-blog as non-owner (403).
#
# Prereqs (any one of):
#   - `docker compose up` from dot-blog-backend/, all services healthy
#   - Local Maven processes running on the default ports
#   - A live deploy reachable via BASE_URL=https://your-host
#
# Usage:
#   ./scripts/smoke.sh                       # defaults to http://localhost:8080
#   BASE_URL=https://api.example.com ./scripts/smoke.sh
#
# Requires: bash, curl, python3 (for JSON parsing).
# =============================================================================

set -u
BASE_URL="${BASE_URL:-http://localhost:8080}"
TIMESTAMP=$(date +%s)
USER_A_EMAIL="smoke-a-${TIMESTAMP}@dot-blog.local"
USER_B_EMAIL="smoke-b-${TIMESTAMP}@dot-blog.local"
PASSWORD="smoke-test-12345"
BLOG_TITLE="SMOKE-${TIMESTAMP}"

PASS_COUNT=0
FAIL_COUNT=0
FAILED_STEPS=()

# ---------- helpers ---------------------------------------------------------

step()  { echo ""; echo "=== $* ==="; }
ok()    { echo "  OK: $*"; PASS_COUNT=$((PASS_COUNT + 1)); }
fail()  { echo "  FAIL: $*"; FAIL_COUNT=$((FAIL_COUNT + 1)); FAILED_STEPS+=("$CURRENT_STEP: $*"); }
set_step() { CURRENT_STEP="$1"; }

# Parse a JSON string field out of a response without pulling in jq.
json_field() {
  python3 -c "import sys,json
try:
    d = json.loads(sys.stdin.read())
    keys = '$1'.split('.')
    for k in keys:
        if isinstance(d, list):
            d = d[int(k)]
        elif d is None:
            print('')
            sys.exit(0)
        else:
            d = d.get(k)
    print(d if d is not None else '')
except Exception as e:
    print('')"
}

json_len() {
  python3 -c "import sys,json
try:
    d = json.loads(sys.stdin.read())
    keys = '$1'.split('.')
    for k in keys:
        d = d.get(k, []) if isinstance(d, dict) else []
    print(len(d) if isinstance(d, list) else 0)
except Exception:
    print(0)"
}

# `req METHOD PATH [json-body] [auth-token]` -> sets HTTP_CODE and BODY.
req() {
  local method="$1"; local path="$2"; local body="${3:-}"; local token="${4:-}"
  local headers=(-H "Content-Type: application/json")
  [[ -n "$token" ]] && headers+=(-H "Authorization: Bearer $token")
  if [[ -n "$body" ]]; then
    RESP=$(curl -s -w $'\n%{http_code}' -X "$method" "${headers[@]}" -d "$body" "$BASE_URL$path")
  else
    RESP=$(curl -s -w $'\n%{http_code}' -X "$method" "${headers[@]}" "$BASE_URL$path")
  fi
  HTTP_CODE=$(echo "$RESP" | tail -n1)
  BODY=$(echo "$RESP" | sed '$d')
}

# ---------- 0. gateway health ----------------------------------------------

step "0. Gateway health"
set_step "0. gateway /actuator/health"
req GET /actuator/health
if [[ "$HTTP_CODE" == "200" ]]; then
  ok "gateway healthy ($HTTP_CODE)"
else
  fail "gateway /actuator/health returned $HTTP_CODE - $BODY"
  echo ""
  echo "Aborting: gateway is not reachable at $BASE_URL."
  exit 1
fi

# ---------- 1. register user A ----------------------------------------------

step "1. Register user A"
set_step "1. register A"
req POST /api/v2/register "{\"username\":\"SmokeA\",\"email\":\"$USER_A_EMAIL\",\"password\":\"$PASSWORD\"}"
if [[ "$HTTP_CODE" == "200" ]]; then
  ok "user A registered"
else
  fail "register A returned $HTTP_CODE - $BODY"
fi

# ---------- 2. login user A -------------------------------------------------

step "2. Login user A"
set_step "2. login A"
req POST /api/v2/login "{\"email\":\"$USER_A_EMAIL\",\"password\":\"$PASSWORD\"}"
TOKEN_A=$(echo "$BODY" | json_field authToken)
if [[ "$HTTP_CODE" == "200" && -n "$TOKEN_A" ]]; then
  ok "user A logged in, authToken length=${#TOKEN_A}"
else
  fail "login A returned $HTTP_CODE - $BODY"
  echo ""
  echo "Aborting: cannot continue without an authToken for user A."
  exit 1
fi

# ---------- 3. isAuthenticated (A) ------------------------------------------

step "3. isAuthenticated (user A)"
set_step "3. isAuthenticated A"
req GET /api/v2/isAuthenticated "" "$TOKEN_A"
USER_ID_A=$(echo "$BODY" | json_field userId)
[[ -z "$USER_ID_A" ]] && USER_ID_A=$(echo "$BODY" | json_field _id)
if [[ "$HTTP_CODE" == "200" && -n "$USER_ID_A" ]]; then
  ok "userId A = $USER_ID_A"
else
  fail "isAuthenticated A returned $HTTP_CODE - $BODY"
fi

# ---------- 4. create blog as A ---------------------------------------------

step "4. Create blog as user A"
set_step "4. create-blog-save"
BLOG_BODY=$(cat <<JSON
{
  "url": "http://example.com/smoke.jpg",
  "publicId": "smoke/$TIMESTAMP",
  "heading": "$BLOG_TITLE",
  "subText": "End-to-end smoke test entry.",
  "content": "<p>Body for the smoke test.</p>",
  "category": "Tech"
}
JSON
)
req POST /api/v2/create-blog-save "$BLOG_BODY" "$TOKEN_A"
if [[ "$HTTP_CODE" == "200" ]]; then
  ok "blog created"
else
  fail "create-blog-save returned $HTTP_CODE - $BODY"
fi

# ---------- 5. find the new blog's _id via get-user-blogs ------------------

step "5. Find the new blog ID"
set_step "5. get-user-blogs"
req GET /api/v2/get-user-blogs "" "$TOKEN_A"
BLOG_ID=$(echo "$BODY" | python3 -c "
import sys, json
d = json.loads(sys.stdin.read())
for arr in (d.get('Draft') or []) + (d.get('Published') or []):
    if arr.get('Title') == '$BLOG_TITLE':
        print(arr.get('_id', '')); break
")
if [[ "$HTTP_CODE" == "200" && -n "$BLOG_ID" ]]; then
  ok "found blogId = $BLOG_ID"
else
  fail "get-user-blogs returned $HTTP_CODE - couldn't find $BLOG_TITLE"
  echo ""
  echo "Aborting: cannot continue without the blog ID."
  exit 1
fi

# ---------- 6. publish ------------------------------------------------------

step "6. Publish the blog"
set_step "6. publish-blog"
# Body field is intentionally "Blogid" (Node casing quirk) — matches
# blog-service's PublishBlogRequest @JsonProperty("Blogid").
req POST /api/v2/publish-blog "{\"Blogid\":\"$BLOG_ID\"}" "$TOKEN_A"
if [[ "$HTTP_CODE" == "200" ]]; then
  ok "blog published"
else
  fail "publish-blog returned $HTTP_CODE - $BODY"
fi

# ---------- 7. list - assert visible ----------------------------------------

step "7. Blog visible in /get-all-blogs"
set_step "7. get-all-blogs (visible)"
req GET /api/v2/get-all-blogs
HITS=$(echo "$BODY" | python3 -c "
import sys, json
try:
    arr = json.loads(sys.stdin.read())
    print(sum(1 for x in arr if x.get('Title') == '$BLOG_TITLE'))
except Exception:
    print(0)")
if [[ "$HTTP_CODE" == "200" && "$HITS" -ge 1 ]]; then
  ok "$BLOG_TITLE visible in published list"
else
  fail "expected to find $BLOG_TITLE; HTTP=$HTTP_CODE hits=$HITS"
fi

# ---------- 8. get-blog -----------------------------------------------------

step "8. Direct fetch /get-blog/{id}"
set_step "8. get-blog (visible)"
req GET "/api/v2/get-blog/$BLOG_ID" "" "$TOKEN_A"
GOT_TITLE=$(echo "$BODY" | json_field Title)
if [[ "$HTTP_CODE" == "200" && "$GOT_TITLE" == "$BLOG_TITLE" ]]; then
  ok "blog fetched: '$GOT_TITLE'"
else
  fail "get-blog returned $HTTP_CODE / title='$GOT_TITLE'"
fi

# ---------- 9. like + comment -----------------------------------------------

step "9. Like the blog"
set_step "9. like-post"
req POST /api/v2/like-post "{\"userId\":\"$TOKEN_A\",\"BlogId\":\"$BLOG_ID\"}"
if [[ "$HTTP_CODE" == "200" ]]; then ok "liked"; else fail "like-post $HTTP_CODE - $BODY"; fi

step "10. Add a comment"
set_step "10. add-commnet"
req POST /api/v2/add-commnet "{\"userId\":\"$TOKEN_A\",\"BlogId\":\"$BLOG_ID\",\"Comment\":\"smoke comment\"}"
if [[ "$HTTP_CODE" == "200" ]]; then ok "commented"; else fail "add-commnet $HTTP_CODE - $BODY"; fi

# ---------- 11. register + login user B (for the 403 case) ------------------

step "11. Register + login user B"
set_step "11. register/login B"
req POST /api/v2/register "{\"username\":\"SmokeB\",\"email\":\"$USER_B_EMAIL\",\"password\":\"$PASSWORD\"}"
if [[ "$HTTP_CODE" != "200" ]]; then
  fail "register B $HTTP_CODE - $BODY"
fi
req POST /api/v2/login "{\"email\":\"$USER_B_EMAIL\",\"password\":\"$PASSWORD\"}"
TOKEN_B=$(echo "$BODY" | json_field authToken)
if [[ "$HTTP_CODE" == "200" && -n "$TOKEN_B" ]]; then
  ok "user B logged in"
else
  fail "login B $HTTP_CODE - $BODY"
fi

# ---------- 12. delete-blog as non-owner -> 403 -----------------------------

step "12. delete-blog as user B (non-owner) -> expect 403"
set_step "12. delete-blog (non-owner)"
req POST "/api/v2/delete-blog/$BLOG_ID" "" "$TOKEN_B"
if [[ "$HTTP_CODE" == "403" ]]; then
  ok "non-owner correctly forbidden ($HTTP_CODE)"
else
  fail "expected 403, got $HTTP_CODE - $BODY"
fi

# ---------- 13. delete-blog as owner -> 200 ---------------------------------

step "13. delete-blog as user A (owner) -> expect 200"
set_step "13. delete-blog (owner)"
req POST "/api/v2/delete-blog/$BLOG_ID" "" "$TOKEN_A"
if [[ "$HTTP_CODE" == "200" ]]; then
  ok "owner soft-deleted the blog"
else
  fail "owner delete returned $HTTP_CODE - $BODY"
fi

# ---------- 14. list - assert gone ------------------------------------------

step "14. Blog no longer in /get-all-blogs"
set_step "14. get-all-blogs (gone)"
req GET /api/v2/get-all-blogs
HITS=$(echo "$BODY" | python3 -c "
import sys, json
try:
    arr = json.loads(sys.stdin.read())
    print(sum(1 for x in arr if x.get('Title') == '$BLOG_TITLE'))
except Exception:
    print(0)")
if [[ "$HITS" == "0" ]]; then
  ok "blog is hidden from the public list"
else
  fail "expected 0 occurrences of $BLOG_TITLE in /get-all-blogs, got $HITS"
fi

# ---------- 15. direct fetch -> 404 -----------------------------------------

step "15. /get-blog/{id} now 404"
set_step "15. get-blog (gone)"
req GET "/api/v2/get-blog/$BLOG_ID" "" "$TOKEN_A"
if [[ "$HTTP_CODE" == "404" ]]; then
  ok "direct fetch returns 404 even for the owner"
else
  fail "expected 404, got $HTTP_CODE - $BODY"
fi

# ---------- 16. owner dashboard hides the blog ------------------------------

step "16. Owner's /get-user-blogs hides the deleted blog"
set_step "16. get-user-blogs (gone)"
req GET /api/v2/get-user-blogs "" "$TOKEN_A"
HITS=$(echo "$BODY" | python3 -c "
import sys, json
try:
    d = json.loads(sys.stdin.read())
    all_blogs = (d.get('Published') or []) + (d.get('Draft') or [])
    print(sum(1 for x in all_blogs if x.get('Title') == '$BLOG_TITLE'))
except Exception:
    print(0)")
if [[ "$HITS" == "0" ]]; then
  ok "blog hidden from the owner's dashboard too"
else
  fail "expected 0 occurrences of $BLOG_TITLE in /get-user-blogs, got $HITS"
fi

# ---------- summary ---------------------------------------------------------

echo ""
echo "================================================================"
echo "Smoke summary: $PASS_COUNT passed, $FAIL_COUNT failed"
if [[ $FAIL_COUNT -gt 0 ]]; then
  echo ""
  echo "Failures:"
  for f in "${FAILED_STEPS[@]}"; do echo "  - $f"; done
  echo "================================================================"
  exit 1
fi
echo "================================================================"
exit 0
