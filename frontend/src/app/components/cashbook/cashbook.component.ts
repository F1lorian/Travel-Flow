import { Component, signal } from '@angular/core';
import { ExpenseService } from '../../services/expense.service';

@Component({
  selector: 'app-cashbook',
  standalone: true,
  templateUrl: './cashbook.component.html',
  styleUrl: './cashbook.component.css'
})
export class CashbookComponent {

  // Dropdown Kategorien (an Backend anpassen falls nötig)
  categories = ['FOOD', 'TRAVEL', 'SHOPPING', 'OTHER'];

  // Daten vom Service
  expenses!: ReturnType<ExpenseService['getExpenses']>;

  // Form Felder
  amount = signal(0);
  date = signal('');
  category = signal('');

  constructor(private expenseService: ExpenseService) {
    this.expenses = this.expenseService.getExpenses();
  }

  // ➕ NEU: Backend-konformes Add
  addExpense() {
    this.expenseService.addExpense({
      kosten: this.amount(),
      date: this.date(),
      kategorie: this.category()
    });

    // Reset Formular
    this.amount.set(0);
    this.date.set('');
    this.category.set('');
  }

  // 🗑️ Delete bleibt gleich
  deleteExpense(id: number) {
    this.expenseService.deleteExpense(id);
  }
}
