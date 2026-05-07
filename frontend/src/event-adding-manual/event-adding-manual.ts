import { Component, EventEmitter, inject, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EventService} from './service/event.service';
import {AddEventRequest} from './models/AddEventRequest';
import { AddEventResponse } from './models/AddEventResponse';

@Component({
  selector: 'app-event-adding-manual',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './event-adding-manual.html',
  styleUrl: './event-adding-manual.css'
})
export class EventAddingManual {
  private eventService = inject(EventService);

  isSending = false;
  message = '';
  @Input() selectedDate = '';
  @Output() closed = new EventEmitter<void>();
  @Output() eventCreated = new EventEmitter<AddEventResponse>();

  closeDialog() {
    this.closed.emit();
  }
  public newItemText: string = '';
  public newItemCost: number = 0;
  public newItemTrip: number = 0;

  sendEvent() {
    this.isSending = true;
    const start = this.selectedDate
      ? `${this.selectedDate}T00:00:00`
      : new Date().toISOString().slice(0, 19);

    let newEvent: AddEventRequest = {
      name: this.newItemText,
      kosten: this.newItemCost,
      tripId: this.newItemTrip,
      start,
    };
    this.eventService.createManualEvent(newEvent)
      .subscribe({
        next: (response) => {
          this.message = `${response.name} wurde gespeichert.`;
          this.eventCreated.emit(response);
          this.isSending = false;
          this.closeDialog();
        },
        error: () => {
          this.message = 'Event konnte nicht gespeichert werden.';
          this.isSending = false;
        }
      });
  }
}
