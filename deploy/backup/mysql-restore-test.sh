#!/usr/bin/env bash
# Checks that a backup can be restored: loads it into a throw-away MySQL container and verifies the schema.
# Run it regularly (e.g. weekly): an untested backup is not a backup.
#   deploy/backup/mysql-restore-test.sh /var/backups/jtyposquatting/jtypo-....sql.gz
set -euo pipefail

file="${1:?backup file required}"
ENGINE="${CONTAINER_ENGINE:-docker}"
name="jtypo-restore-test-$$"

$ENGINE run -d --rm --name "$name" -e MYSQL_ROOT_PASSWORD=restore-test -e MYSQL_DATABASE=jtypo mysql:8.4 >/dev/null
trap '$ENGINE rm -f "$name" >/dev/null 2>&1' EXIT

for _ in $(seq 1 60); do
  $ENGINE exec "$name" mysql -uroot -prestore-test -e 'SELECT 1' jtypo >/dev/null 2>&1 && break
  sleep 2
done

gunzip -c "$file" | $ENGINE exec -i "$name" mysql --default-character-set=utf8mb4 -uroot -prestore-test jtypo
query() { $ENGINE exec "$name" mysql --default-character-set=utf8mb4 -N -uroot -prestore-test jtypo -e "$1" 2>/dev/null; }

migrations=$(query "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1")
[ "$migrations" -ge 4 ] || { echo "Restore test FAILED: $migrations migrations" >&2; exit 1; }
echo "Restore test OK: $migrations migrations, $(query 'SELECT COUNT(*) FROM users') users, $(query 'SELECT COUNT(*) FROM scans') scans, $(query 'SELECT COUNT(*) FROM scan_results') results"
