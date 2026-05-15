import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Expense {
  kosten: number;
  trip: string;
  date: string;
  kategorie: string;
}

export interface CategorySummary {
  [key: string]: number;
}

@Injectable({
  providedIn: 'root'
})
export class ExpenseService {
  private apiUrl = '/api';

  constructor(private http: HttpClient) { }

  addExpense(expense: {
    kosten: number;
    trip: { name: string };
    date: string;
    kategorie: string;
  }): Observable<unknown> {
    return this.http.post(`${this.apiUrl}/ausgabe`, expense);
  }

  getExpensesByPeriod(startDate: string, endDate: string): Observable<Expense[]> {
    return this.http.get<Expense[]>(`${this.apiUrl}/ausgabenliste?start=${startDate}&end=${endDate}`);
  }

  getCategorySummary(startDate: string, endDate: string): Observable<CategorySummary> {
    return this.http.get<CategorySummary>(`${this.apiUrl}/proKategorie?start=${startDate}&end=${endDate}`);
  }
}
