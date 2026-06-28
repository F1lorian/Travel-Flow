#!/bin/sh
# Regenerate the runtime config from the container environment before nginx starts.
# Runs via the nginx image's /docker-entrypoint.d/ mechanism.
set -e

if [ -n "${GOOGLE_OAUTH_CLIENT_ID}" ]; then
  cat > /usr/share/nginx/html/config.js <<EOF
window.__APP_CONFIG__ = {
  googleClientId: "${GOOGLE_OAUTH_CLIENT_ID}"
};
EOF
  echo "app-config: googleClientId set from GOOGLE_OAUTH_CLIENT_ID"
else
  echo "app-config: GOOGLE_OAUTH_CLIENT_ID not set, using bundled config.js default"
fi
