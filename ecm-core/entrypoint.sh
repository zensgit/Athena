#!/bin/sh
set -e

APP_USER="${APP_USER:-app}"
APP_UID="$(id -u "$APP_USER")"

mkdir -p /var/ecm/content /var/ecm/temp /var/ecm/import /app/logs

content_owner="$(stat -c %u /var/ecm/content 2>/dev/null || echo "")"
if [ "$content_owner" != "$APP_UID" ]; then
  chown -R "$APP_USER":"$APP_USER" /var/ecm/content
fi

chown -R "$APP_USER":"$APP_USER" /var/ecm/temp /var/ecm/import /app/logs

exec su -s /bin/sh "$APP_USER" -c "java -jar -Djava.security.egd=file:/dev/./urandom /app/app.jar"
