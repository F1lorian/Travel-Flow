import { TestBed } from '@angular/core/testing';

import { EventService } from './event.service';

describe('Event', () => {
  let service: Event;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Event);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
