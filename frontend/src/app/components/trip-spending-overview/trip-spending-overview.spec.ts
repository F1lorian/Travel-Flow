import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { TripSpendingOverviewComponent } from './trip-spending-overview';
import { ExpenseService } from '../../services/expense.service';
import { TripService } from '../../TripManagement/services/trip.service';

describe('TripSpendingOverviewComponent', () => {
  let component: TripSpendingOverviewComponent;
  let fixture: ComponentFixture<TripSpendingOverviewComponent>;
  let expenseServiceSpy: jasmine.SpyObj<ExpenseService>;
  let tripServiceSpy: jasmine.SpyObj<TripService>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;

  beforeEach(async () => {
    expenseServiceSpy = jasmine.createSpyObj<ExpenseService>('ExpenseService', [
      'getBudget',
      'getWarningRange',
      'getSummedExpensesTrip',
      'getAvailable',
      'getExpensesByTrip',
      'getCategorySummaryTrip',
      'deleteExpense',
      'exportExpensesByTrip'
    ]);
    tripServiceSpy = jasmine.createSpyObj<TripService>('TripService', ['getAllTrips', 'getTrip']);
    dialogSpy = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    expenseServiceSpy.getBudget.and.returnValue(of(0));
    expenseServiceSpy.getWarningRange.and.returnValue(of(0));
    expenseServiceSpy.getSummedExpensesTrip.and.returnValue(of(0));
    expenseServiceSpy.getAvailable.and.returnValue(of(0));
    expenseServiceSpy.getExpensesByTrip.and.returnValue(of([]));
    expenseServiceSpy.getCategorySummaryTrip.and.returnValue(of([]));
    expenseServiceSpy.deleteExpense.and.returnValue(of(undefined));
    expenseServiceSpy.exportExpensesByTrip.and.returnValue(of(new Blob()));
    tripServiceSpy.getAllTrips.and.returnValue(of([]));
    tripServiceSpy.getTrip.and.returnValue(
      of({
        id: 1,
        name: 'London',
        events: [],
        invitedByUserId: 1,
        invited: false
      })
    );
    const dialogRefSpy = jasmine.createSpyObj<MatDialogRef<unknown>>('MatDialogRef', [
      'afterClosed'
    ]);
    dialogRefSpy.afterClosed.and.returnValue(of(true));
    dialogSpy.open.and.returnValue(dialogRefSpy);

    await TestBed.configureTestingModule({
      imports: [TripSpendingOverviewComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { params: of({ id: '1' }) } },
        { provide: ExpenseService, useValue: expenseServiceSpy },
        { provide: TripService, useValue: tripServiceSpy },
        { provide: MatDialog, useValue: dialogSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(TripSpendingOverviewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should delete an expense after confirmation', () => {
    spyOn(component, 'loadTripExpenses');

    component.deleteExpense({ id: 42, sourceType: 'EXPENSE' });

    expect(dialogSpy.open).toHaveBeenCalled();
    expect(expenseServiceSpy.deleteExpense).toHaveBeenCalledWith(42);
    expect(component.loadTripExpenses).toHaveBeenCalled();
  });

  it('should export trip expenses as csv', () => {
    const blob = new Blob(['Date,Title,Trip,Category,Amount\n']);
    expenseServiceSpy.exportExpensesByTrip.and.returnValue(of(blob));
    spyOn<any>(component, 'downloadCsv');

    component.tripname = 'London';
    component.exportCsv();

    expect(expenseServiceSpy.exportExpensesByTrip).toHaveBeenCalledWith('London');
    expect((component as any).downloadCsv).toHaveBeenCalledWith(blob, 'London-expenses.csv');
  });
});
