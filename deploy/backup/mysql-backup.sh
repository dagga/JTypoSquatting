#!/usr/bin/env bash
# Consistent MySQL backup (InnoDB, no lock), compressed, with rotation.
#   deploy/backup/mysql-backup.sh [backup directory]
# Schedule it daily (cron, systemd timer). Screenshots live in the S3 storage: enable versioning or
# replication there, they are not part of this backup.
set -euo pipefail

BACKUP_DIR="${1:-${BACKUP_DIR:-/var/backups/jtyposquatting}}"
KEEP="${KEEP:-14}"
# Command that runs a program in the MySQL container
MYSQL_EXEC="${MYSQL_EXEC:-docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env exec -T mysql}"

mkdir -p "$BACKUP_DIR"
file="$BACKUP_DIR/jtypo-$(date -u +%Y%m%dT%H%M%S.%3NZ).sql.gz"
tmp="$file.partial"

$MYSQL_EXEC sh -c 'exec mysqldump --default-character-set=utf8mb4 --single-transaction --quick --routines --triggers --set-gtid-purged=OFF \
    -uroot -p"$MYSQL_ROOT_PASSWORD" jtypo' | gzip -9 > "$tmp"
# An interrupted dump has no completion marker
gunzip -c "$tmp" | tail -n 1 | grep -q 'Dump completed' || { echo "Incomplete dump: $tmp" >&2; exit 1; }
mv "$tmp" "$file"
echo "Backup written: $file ($(du -h "$file" | cut -f1))"

# Keep the most recent backups only
ls -1t "$BACKUP_DIR"/jtypo-*.sql.gz | tail -n +"$((KEEP + 1))" | xargs -r rm --
