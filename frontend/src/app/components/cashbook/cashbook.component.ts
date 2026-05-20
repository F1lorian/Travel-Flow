import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ExpenseService } from '../../services/expense.service';

@Component({
  selector: 'app-cashbook',
  standalone: true,
  imports: [FormsModule],
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
        this.errorMessage.set('Ausgabe konnte nicht gespeichert werden.');
      }
    });
  }
}
