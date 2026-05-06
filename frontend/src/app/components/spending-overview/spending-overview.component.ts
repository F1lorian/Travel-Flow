import { Component, AfterViewInit } from '@angular/core';
import { Chart } from 'chart.js/auto';
import { ExpenseService } from '../../services/expense.service';

@Component({
  selector: 'app-spending-overview',
  standalone: true,
  templateUrl: './spending-overview.component.html'
})
export class SpendingOverviewComponent implements AfterViewInit {
  selectedYear = new Date().getFullYear();
  constructor(private expenseService: ExpenseService) {}

  ngAfterViewInit(): void {
    const expenses = this.expenseService.getExpenses()();

    const categoryMap: { [key: string]: number } = {};

    expenses.forEach(e => {
      if (!categoryMap[e.category]) {
        categoryMap[e.category] = 0;
      }
      categoryMap[e.category] += e.amount;
    });

    const labels = Object.keys(categoryMap);
    const amounts = Object.values(categoryMap);

    new Chart('myChart', {
      type: 'bar',
      data: {
        labels: labels,
        datasets: [{
          label: 'Ausgaben',
          data: amounts,
          backgroundColor: [
            '#42a5f5',
            '#66bb6a',
            '#ffa726',
            '#ef5350'
          ]
        }]
      }
    });
  }
}
