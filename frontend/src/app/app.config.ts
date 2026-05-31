import { registerLocaleData } from "@angular/common";
import { provideHttpClient } from "@angular/common/http";
import localeDe from "@angular/common/locales/de";
import {
	type ApplicationConfig,
	LOCALE_ID,
	provideBrowserGlobalErrorListeners,
	provideZoneChangeDetection,
} from "@angular/core";
import { provideRouter } from "@angular/router";
import { routes } from "./app.routes";

registerLocaleData(localeDe);

export const appConfig: ApplicationConfig = {
	providers: [
		provideBrowserGlobalErrorListeners(),
		provideZoneChangeDetection({ eventCoalescing: true }),
		provideRouter(routes),
		provideHttpClient(),
		{ provide: LOCALE_ID, useValue: "de-DE" },
	],
};
