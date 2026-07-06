import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

// Fail loud early when the runtime Google OAuth client id is missing. An empty client_id makes
// "Sign in with Google" fail with Google's cryptic "Missing required parameter: client_id" error,
// so surface the real cause here instead. The value comes from public/config.js, which Docker
// regenerates from GOOGLE_OAUTH_CLIENT_ID at container start (see frontend/docker/40-app-config.sh).
const googleClientId = (globalThis as { __APP_CONFIG__?: { googleClientId?: string } }).__APP_CONFIG__
  ?.googleClientId;
if (!googleClientId) {
  console.warn(
    '[config] googleClientId is empty — "Sign in with Google" and Gmail import will fail. ' +
      'Set GOOGLE_OAUTH_CLIENT_ID (Docker regenerates config.js from it) or check ' +
      'frontend/public/config.js.'
  );
}

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
