import { Component, EventEmitter, inject, Output, signal } from "@angular/core";
import { FormsModule } from "@angular/forms";
import {
	type EventRecommendation,
	TripService,
} from "../../../TripManagement/services/trip.service";

/**
 * Search panel shown inside the trip page's recommendation drawer. The user types
 * a place, this calls the backend, and each result has an "Add" button that emits
 * the chosen event up to the parent trip page via {@link addRecommendation}.
 */
@Component({
	selector: "app-event-search",
	standalone: true,
	imports: [FormsModule],
	templateUrl: "./event-search.html",
	styleUrl: "./event-search.css",
})
export class EventSearchComponent {
	// Parent (trip-detail) listens to this to add the picked event to the trip.
	@Output() addRecommendation = new EventEmitter<EventRecommendation>();

	private tripService = inject(TripService);

	location = "";
	isLoading = false;
	errorMessage = "";
	// Signal so the template re-renders automatically when results change.
	results = signal<EventRecommendation[]>([]);

	searchEvents() {
		const searchLocation = this.location.trim();
		// Ignore empty input and double-clicks while a search is already running.
		if (!searchLocation || this.isLoading) return;

		this.isLoading = true;
		this.errorMessage = "";
		this.results.set([]);

		this.tripService.searchEventRecommendations(searchLocation).subscribe({
			next: (events) => {
				this.results.set(events);
				this.isLoading = false;
				// Request succeeded but the model found nothing for this place.
				if (events.length === 0) {
					this.errorMessage =
						"No events found for this place. Try another city or country.";
				}
			},
			error: (err) => {
				// Most common cause is a missing/invalid Gemini API key on the backend.
				console.error("Event search error:", err);
				this.errorMessage =
					"Could not search events. Check your Gemini API key and try again.";
				this.isLoading = false;
			},
		});
	}

	addEvent(event: EventRecommendation) {
		this.addRecommendation.emit(event);
	}

	// Render the ISO date-time in the user's locale; fall back to the raw string
	// if it isn't parseable, or a placeholder if it's missing.
	formatDateTime(dateTime: string): string {
		if (!dateTime) return "Date not listed";

		const parsedDate = new Date(dateTime);
		if (Number.isNaN(parsedDate.getTime())) {
			return dateTime;
		}

		return parsedDate.toLocaleString();
	}

	// null/undefined = unknown price, 0 = free, otherwise show the amount in EUR.
	formatPrice(price: number | null): string {
		if (price === null || price === undefined) return "Price not listed";
		if (price === 0) return "Free";
		return `${price} EUR`;
	}
}
