import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
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
  warningMessage = signal('');

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
    this.warningMessage.set('');

    const amountValue = Number(this.amount());
    const selectedTrip = this.tripName();

    if (!amountValue || amountValue <= 0) {
      this.errorMessage.set('Bitte gib einen gültigen Betrag ein.');
      return;
    }

    if (!selectedTrip) {
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

    forkJoin({
      available: this.expenseService.getAvailable(selectedTrip),
      warningRange: this.expenseService.getWarningRange(selectedTrip)
    }).subscribe({
      next: ({ available, warningRange }) => {
        const availableValue = Number(available);
        const warningRangeValue = Number(warningRange);
        const remainingAfterExpense = availableValue - amountValue;

        if (amountValue > availableValue) {
          const exceededBy = amountValue - availableValue;

          const shouldStillAdd = window.confirm(
            `Achtung: Für diesen Trip sind nur noch ${availableValue.toFixed(2)} € verfügbar.\n` +
            `Diese Ausgabe beträgt ${amountValue.toFixed(2)} €.\n\n` +
            `Das Budget wird um ${exceededBy.toFixed(2)} € überschritten.\n\n` +
            `Ausgabe trotzdem hinzufügen?`
          );

          if (!shouldStillAdd) {
            this.warningMessage.set(
              'Ausgabe wurde nicht hinzugefügt, weil das verfügbare Budget nicht ausreicht.'
            );
            return;
          }

          this.saveExpense();
          return;
        }

        if (warningRangeValue > 0 && remainingAfterExpense <= warningRangeValue) {
          const shouldStillAdd = window.confirm(
            `Achtung: Nach dieser Ausgabe bleiben nur noch ${remainingAfterExpense.toFixed(2)} € verfügbar.\n` +
            `Das liegt innerhalb deines Warnbereichs von ${warningRangeValue.toFixed(2)} €.\n\n` +
            `Ausgabe trotzdem hinzufügen?`
          );

          if (!shouldStillAdd) {
            this.warningMessage.set(
              'Ausgabe wurde nicht hinzugefügt, weil sie innerhalb des Warnbereichs liegt.'
            );
            return;
          }
        }

        this.saveExpense();
      },
      error: (error) => {
        console.error('Fehler beim Prüfen des verfügbaren Budgets/Warnbereichs:', error);
        this.errorMessage.set('Budget und Warnbereich konnten nicht geprüft werden.');
      }
    });
  }

  private saveExpense(): void {
    this.expenseService.addExpense({
      kosten: Number(this.amount()),
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
        this.warningMessage.set('');
        this.successMessage.set('Ausgabe wurde hinzugefuegt.');
      },
      error: (error) => {
        console.error('Fehler beim Speichern der Ausgabe:', error);
        this.successMessage.set('');
        this.warningMessage.set('');

        if (error.status === 404) {
          this.errorMessage.set('Trip wurde nicht gefunden. Bitte pruefe den Tripnamen.');
        } else {
          this.errorMessage.set('Ausgabe konnte nicht gespeichert werden.');
        }
      }
    });
  }
}
