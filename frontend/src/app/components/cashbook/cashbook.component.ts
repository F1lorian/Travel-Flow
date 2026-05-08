import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ExpenseService } from '../../services/expense.service';

@Component({
  selector: 'app-cashbook',
  standalone: true,
  imports: [FormsModule, CommonModule],
  templateUrl: './cashbook.component.html',
  styleUrls: ['./cashbook.component.css']
})
export class CashbookComponent {

  categories = ['ESSEN', 'TRANSPORT', 'HOTEL', 'FREIZEIT', 'SONSTIGES'];

  amount = signal(0);
  tripName = signal('');
  date = signal('');
  category = signal('');
  errorMessage = signal('');
  successMessage = signal('');

  constructor(private expenseService: ExpenseService) {}

  addExpense() {
    this.expenseService.addExpense({
      // ruft die methode addExpence() von expenceService() auf
      // er erwatet hier ein expence, deswegen geben wir ihm die  Parameter, die er benoetigt
      kosten: this.amount(),
      trip: {
        name: this.tripName()
      },
      date: this.date(),
      kategorie: this.category()
    }).subscribe({
      // nach der methode, wird alles zurueckgesetzt
      next: () => {
        this.amount.set(0);
        this.tripName.set('');
        this.date.set('');
        this.category.set('');
        this.errorMessage.set('');
        this.successMessage.set('Ausgabe wurde hinzugefuegt.');
      },
      error: (error) => {
        // falls ein Fehler kommt, dann soll er das ausgeben
        console.error('Fehler beim Speichern der Ausgabe:', error);
        this.successMessage.set('');
        this.errorMessage.set('Ausgabe konnte nicht gespeichert werden.');
      }
    });
  }
}
