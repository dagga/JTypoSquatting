#!/usr/bin/env bash
# Restores a backup into the MySQL database of the deployment. Stop the api and worker services first.
#   deploy/backup/mysql-restore.sh /var/backups/jtyposquatting/jtypo-20261014T033000Z.sql.gz
set -euo pipefail

file="${1:?backup file required}"
MYSQL_EXEC="${MYSQL_EXEC:-docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env exec -T mysql}"

gunzip -c "$file" | $MYSQL_EXEC sh -c 'exec mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" jtypo'
echo "Restored $file"
