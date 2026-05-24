import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ExpenseService } from '../../services/expense.service';
import { Trip, TripService } from '../../TripManagement/services/trip.service';

@Component({
  selector: 'app-cashbook',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './cashbook.component.html',
  styleUrls: ['./cashbook.component.css']
})
export class CashbookComponent implements OnInit {

  categories = ['ESSEN', 'TRANSPORT', 'HOTEL', 'FREIZEIT', 'SONSTIGES'];

  amount = signal(0);
  tripName = signal('');
  date = signal('');
  category = signal('');

  trips = signal<Trip[]>([]);
  loadingTrips = signal(false);

  errorMessage = signal('');
  successMessage = signal('');

  constructor(
    private expenseService: ExpenseService,
    private tripService: TripService
  ) {}

  ngOnInit(): void {
    this.loadTrips();
  }

  loadTrips(): void {
    this.loadingTrips.set(true);
    this.errorMessage.set('');

    this.tripService.getAllTrips().subscribe({
      next: (trips) => {
        this.trips.set(trips);
        this.loadingTrips.set(false);
      },
      error: (error) => {
        console.error('Fehler beim Laden der Trips:', error);
        this.loadingTrips.set(false);
        this.errorMessage.set('Trips konnten nicht geladen werden.');
      }
    });
  }

  addExpense(): void {
    this.errorMessage.set('');
    this.successMessage.set('');

    if (!this.amount() || this.amount() <= 0) {
      this.errorMessage.set('Bitte gib einen gültigen Betrag ein.');
      return;
    }

    if (!this.tripName()) {
      this.errorMessage.set('Bitte wähle einen Trip aus.');
      return;
    }

    if (!this.date()) {
      this.errorMessage.set('Bitte wähle ein Datum aus.');
      return;
    }

    if (!this.category()) {
      this.errorMessage.set('Bitte wähle eine Kategorie aus.');
      return;
    }

    this.expenseService.addExpense({
      kosten: this.amount(),
      trip: {
        name: this.tripName()
      },
      date: this.date(),
      kategorie: this.category()
    }).subscribe({
      next: () => {
        this.amount.set(0);
        this.tripName.set('');
        this.date.set('');
        this.category.set('');
        this.errorMessage.set('');
        this.successMessage.set('Ausgabe wurde hinzugefuegt.');
      },
      error: (error) => {
        console.error('Fehler beim Speichern der Ausgabe:', error);
        this.successMessage.set('');

        if (error.status === 404) {
          this.errorMessage.set('Trip wurde nicht gefunden. Bitte pruefe den Tripnamen.');
        } else {
          this.errorMessage.set('Ausgabe konnte nicht gespeichert werden.');
        }
      }
    });
  }
}
