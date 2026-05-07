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

  constructor(private expenseService: ExpenseService) {}

  addExpense() {
    this.expenseService.addExpense({
      kosten: this.amount(),
      trip: this.tripName(),
      date: this.date(),
      kategorie: this.category()
    });

    this.amount.set(0);
    this.tripName.set('');
    this.date.set('');
    this.category.set('');
  }
}
