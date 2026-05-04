import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EventDialog } from './event-dialog';

describe('EventDialog', () => {
  let component: EventDialog;
  let fixture: ComponentFixture<EventDialog>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EventDialog]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EventDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
