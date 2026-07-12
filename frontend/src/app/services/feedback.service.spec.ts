import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';

import { FeedbackService, apiErrorMessage } from './feedback.service';

describe('FeedbackService', () => {
  let service: FeedbackService;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  beforeEach(() => {
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);
    TestBed.configureTestingModule({
      providers: [{ provide: MatSnackBar, useValue: snackBar }]
    });
    service = TestBed.inject(FeedbackService);
  });

  it('shows a success toast with the success style', () => {
    service.success('Trip "Paris" created');

    expect(snackBar.open).toHaveBeenCalledWith(
      'Trip "Paris" created',
      'OK',
      jasmine.objectContaining({ panelClass: 'feedback-success' })
    );
  });

  it('shows an error toast with the error style', () => {
    service.error('Trip can\'t be created: name missing');

    expect(snackBar.open).toHaveBeenCalledWith(
      'Trip can\'t be created: name missing',
      'OK',
      jasmine.objectContaining({ panelClass: 'feedback-error' })
    );
  });
});

describe('apiErrorMessage', () => {
  it('reads the {"message": ...} body the backend sends for errors', () => {
    const err = { error: { message: 'Trip name cannot be empty' } };
    expect(apiErrorMessage(err, 'fallback')).toBe('Trip name cannot be empty');
  });

  it('accepts plain-text error bodies from older endpoints', () => {
    expect(apiErrorMessage({ error: 'Event outside trip' }, 'fallback')).toBe('Event outside trip');
  });

  it('falls back when the error carries no readable body', () => {
    expect(apiErrorMessage({ error: {} }, 'server not reachable')).toBe('server not reachable');
    expect(apiErrorMessage(null, 'server not reachable')).toBe('server not reachable');
    expect(apiErrorMessage({ error: '   ' }, 'server not reachable')).toBe('server not reachable');
  });
});
