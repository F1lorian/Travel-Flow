import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Expense } from '../models/expense.models';

@Injectable({
  providedIn: 'root'
})
export class ExpenseService {

  private apiUrl = 'http://localhost:8080/ausgaben';

  expenses = signal<Expense[]>([]);

  constructor(private http: HttpClient) {
    this.loadExpenses();
  }

  loadExpenses() {
    this.http.get<Expense[]>(this.apiUrl).subscribe(data => {
      this.expenses.set(data);
    });
  }

  getExpenses() {
    return this.expenses;
  }

  addExpense(expense: Partial<Expense>) {
    this.http.post<Expense>(this.apiUrl, expense).subscribe(() => {
      this.loadExpenses();
    });
  }

  deleteExpense(id: number) {
    this.http.delete(`${this.apiUrl}/${id}`).subscribe(() => {
      this.loadExpenses();
    });
  }
}
