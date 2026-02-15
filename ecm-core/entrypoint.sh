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

JAVA_BIN="${JAVA_BIN:-/opt/java/openjdk/bin/java}"
if [ ! -x "$JAVA_BIN" ]; then
  JAVA_BIN="$(command -v java)"
fi

exec su -s /bin/sh "$APP_USER" -c "$JAVA_BIN -jar -Djava.security.egd=file:/dev/./urandom /app/app.jar"
