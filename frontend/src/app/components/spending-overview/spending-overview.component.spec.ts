import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { SpendingOverviewComponent } from './spending-overview.component';
import { ExpenseService } from '../../services/expense.service';
import { TripService } from '../../TripManagement/services/trip.service';

describe('SpendingOverviewComponent', () => {
  let component: SpendingOverviewComponent;
  let fixture: ComponentFixture<SpendingOverviewComponent>;
  let expenseServiceSpy: jasmine.SpyObj<ExpenseService>;
  let tripServiceSpy: jasmine.SpyObj<TripService>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;

  beforeEach(async () => {
    expenseServiceSpy = jasmine.createSpyObj<ExpenseService>('ExpenseService', [
      'getExpensesByPeriod',
      'getCategorySummary',
      'deleteExpense',
      'exportExpensesByPeriod'
    ]);
    tripServiceSpy = jasmine.createSpyObj<TripService>('TripService', ['getAllTrips']);
    dialogSpy = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    expenseServiceSpy.getExpensesByPeriod.and.returnValue(of([]));
    expenseServiceSpy.getCategorySummary.and.returnValue(of([]));
    expenseServiceSpy.deleteExpense.and.returnValue(of(undefined));
    expenseServiceSpy.exportExpensesByPeriod.and.returnValue(of(new Blob()));
    tripServiceSpy.getAllTrips.and.returnValue(of([]));
    const dialogRefSpy = jasmine.createSpyObj<MatDialogRef<unknown>>('MatDialogRef', [
      'afterClosed'
    ]);
    dialogRefSpy.afterClosed.and.returnValue(of(true));
    dialogSpy.open.and.returnValue(dialogRefSpy);

    await TestBed.configureTestingModule({
      imports: [SpendingOverviewComponent],
      providers: [
        provideRouter([]),
        { provide: ExpenseService, useValue: expenseServiceSpy },
        { provide: TripService, useValue: tripServiceSpy },
        { provide: MatDialog, useValue: dialogSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SpendingOverviewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should delete an expense after confirmation', () => {
    spyOn(component, 'loadExpenses');

    component.deleteExpense({ id: 42, sourceType: 'EXPENSE' });

    expect(dialogSpy.open).toHaveBeenCalled();
    expect(expenseServiceSpy.deleteExpense).toHaveBeenCalledWith(42);
    expect(component.loadExpenses).toHaveBeenCalled();
  });

  it('should export expenses as csv', () => {
    const blob = new Blob(['Date,Title,Trip,Category,Amount\n']);
    expenseServiceSpy.exportExpensesByPeriod.and.returnValue(of(blob));
    spyOn<any>(component, 'downloadCsv');

    component.startDate = '2026-05-01';
    component.endDate = '2026-05-10';
    component.exportCsv();

    expect(expenseServiceSpy.exportExpensesByPeriod).toHaveBeenCalledWith('2026-05-01', '2026-05-10');
    expect((component as any).downloadCsv).toHaveBeenCalledWith(blob, 'cashbook-export.csv');
  });
});
