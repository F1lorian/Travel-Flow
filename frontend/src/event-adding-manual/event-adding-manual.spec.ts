import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EventAddingManual } from './event-adding-manual';

describe('EventAddingManual', () => {
  let component: EventAddingManual;
  let fixture: ComponentFixture<EventAddingManual>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EventAddingManual]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EventAddingManual);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
