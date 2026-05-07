import { Component, EventEmitter, inject, Output } from '@angular/core';
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
  @Output() closed = new EventEmitter<void>();
  @Output() eventCreated = new EventEmitter<AddEventResponse>();

  closeDialog() {
    this.closed.emit();
  }
  public newItemText: string = '';
  public newItemCost: number = 0;
  public newItemTrip: string = '';

  sendEvent() {
    this.isSending = true;

    let newEvent: AddEventRequest = {
      name: this.newItemText,
      cost : this.newItemCost,
      tripId : this.newItemTrip
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
