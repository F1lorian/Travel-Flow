import { Component, inject, signal } from "@angular/core";
import { Router, RouterLink, RouterOutlet } from "@angular/router";
import { CalendarComponent } from "./components/calendar/calendar";

@Component({
	selector: "app-root",
	standalone: true,
	imports: [RouterOutlet, RouterLink, CalendarComponent],
	templateUrl: "./app.html",
	styleUrl: "./app.css",
})
export class App {
	private router = inject(Router);
	protected readonly title = signal("sep");

	get isCalendar(): boolean {
		const url = this.router.url;
		return url === "/" || url === "";
	}

	get navTarget(): string {
		return this.isCalendar ? "/trips" : "/";
	}

	get navLabel(): string {
		return this.isCalendar ? "Manage Trips" : "Calendar";
	}
}
