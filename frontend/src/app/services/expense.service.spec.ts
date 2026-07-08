import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ExpenseService } from './expense.service';

describe('ExpenseService', () => {
  let service: ExpenseService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(ExpenseService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should delete an expense', () => {
    service.deleteExpense(42).subscribe();

    const request = httpMock.expectOne('/api/ausgabe/42');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });

  it('should export expenses by period as csv', () => {
    service.exportExpensesByPeriod('2026-05-01', '2026-05-10').subscribe();

    const request = httpMock.expectOne('/api/ausgabenliste/export?start=2026-05-01&end=2026-05-10');
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['Date,Title,Trip,Category,Amount\n']));
  });

  it('should export expenses by trip as csv', () => {
    service.exportExpensesByTrip('Paris, France').subscribe();

    const request = httpMock.expectOne('/api/ausgabenlisteTrip/export?tripname=Paris%2C%20France');
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['Date,Title,Trip,Category,Amount\n']));
  });
});
