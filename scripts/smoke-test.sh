#!/usr/bin/env sh
set -eu

AUTH_BASE_URL="${AUTH_BASE_URL:-http://localhost:8081}"
TASK_BASE_URL="${TASK_BASE_URL:-http://localhost:8082}"

wait_for_health() {
  name="$1"
  url="$2"
  attempts=0
  until curl --fail --silent --show-error "$url/actuator/health" >/dev/null; do
    attempts=$((attempts + 1))
    if [ "$attempts" -ge 60 ]; then
      echo "$name did not become healthy: $url" >&2
      exit 1
    fi
    sleep 2
  done
}

json_string() {
  key="$1"
  sed -n "s/.*\"$key\":\"\([^\"]*\)\".*/\1/p"
}

json_number() {
  key="$1"
  sed -n "s/.*\"$key\":\([0-9][0-9]*\).*/\1/p"
}

wait_for_health "auth-service" "$AUTH_BASE_URL"
wait_for_health "task-service" "$TASK_BASE_URL"

email="smoke-$(date +%s)-$$@example.com"
password="smoke-password"

curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\",\"password\":\"$password\"}" \
  "$AUTH_BASE_URL/api/v1/auth/register" >/dev/null

login_response="$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\",\"password\":\"$password\"}" \
  "$AUTH_BASE_URL/api/v1/auth/login")"
token="$(printf '%s' "$login_response" | json_string accessToken)"
test -n "$token"

create_response="$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $token" \
  -d '{"title":"Smoke test","description":"Verify the deployed API","status":"TODO","dueDate":"2030-01-01"}' \
  "$TASK_BASE_URL/api/v1/tasks")"
task_id="$(printf '%s' "$create_response" | json_string id)"
version="$(printf '%s' "$create_response" | json_number version)"
test -n "$task_id"
test -n "$version"

curl --fail --silent --show-error \
  -H "Authorization: Bearer $token" \
  "$TASK_BASE_URL/api/v1/tasks/$task_id" >/dev/null

list_response="$(curl --fail --silent --show-error \
  -H "Authorization: Bearer $token" \
  "$TASK_BASE_URL/api/v1/tasks?limit=20")"
case "$list_response" in
  *"$task_id"*) ;;
  *)
    echo "Created task was not returned by the list endpoint" >&2
    exit 1
    ;;
esac

curl --fail --silent --show-error \
  -X PUT \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $token" \
  -d "{\"title\":\"Smoke test complete\",\"description\":\"Verify the deployed API\",\"status\":\"DONE\",\"dueDate\":\"2030-01-01\",\"version\":$version}" \
  "$TASK_BASE_URL/api/v1/tasks/$task_id" >/dev/null

curl --fail --silent --show-error \
  -X DELETE \
  -H "Authorization: Bearer $token" \
  "$TASK_BASE_URL/api/v1/tasks/$task_id" >/dev/null

deleted_status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
  -H "Authorization: Bearer $token" \
  "$TASK_BASE_URL/api/v1/tasks/$task_id")"
test "$deleted_status" = "404"

echo "Smoke test passed: registration, login, and task lifecycle"
