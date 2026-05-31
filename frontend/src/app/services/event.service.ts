import { HttpClient } from "@angular/common/http";
import { Injectable, inject } from "@angular/core";
import { Observable } from "rxjs";

@Injectable({
	providedIn: "root",
})
export class EventService {
	private readonly http = inject(HttpClient);
	private readonly manualEventEndpoint = "/api/event/create/manual";
}
