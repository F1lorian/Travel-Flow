import { Component, EventEmitter, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EventRecommendation, TripService } from '../../services/trip.service';

@Component({
  selector: 'app-event-search',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './event-search.html',
  styleUrl: './event-search.css'
})
export class EventSearchComponent {
  @Output() addRecommendation = new EventEmitter<EventRecommendation>();

  private tripService = inject(TripService);

  location = '';
  isLoading = false;
  errorMessage = '';
  results = signal<EventRecommendation[]>([]);

  searchEvents() {
    const searchLocation = this.location.trim();
    if (!searchLocation || this.isLoading) return;

    this.isLoading = true;
    this.errorMessage = '';
    this.results.set([]);

    this.tripService.searchEventRecommendations(searchLocation).subscribe({
      next: (events) => {
        this.results.set(events);
        this.isLoading = false;
        if (events.length === 0) {
          this.errorMessage = 'No events found for this place. Try another city or country.';
        }
      },
      error: (err) => {
        console.error('Event search error:', err);
        this.errorMessage = 'Could not search events. Check your Gemini API key and try again.';
        this.isLoading = false;
      }
    });
  }

  addEvent(event: EventRecommendation) {
    this.addRecommendation.emit(event);
  }

  formatDateTime(dateTime: string): string {
    if (!dateTime) return 'Date not listed';

    const parsedDate = new Date(dateTime);
    if (Number.isNaN(parsedDate.getTime())) {
      return dateTime;
    }

    return parsedDate.toLocaleString();
  }

  formatPrice(price: number | null): string {
    if (price === null || price === undefined) return 'Price not listed';
    if (price === 0) return 'Free';
    return `${price} EUR`;
  }
}
