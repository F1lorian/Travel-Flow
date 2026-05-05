import { Component } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import {FormsModule} from '@angular/forms';

@Component({
  selector: 'app-event-dialog',
  standalone: true,
  imports: [
    FormsModule
  ],
  templateUrl: './event-dialog.html',
  styleUrl: './event-dialog.css',
})
export class EventDialog {
  title: string = "";
  constructor(private dialogRef: MatDialogRef<EventDialog>) {}

  save() {
    this.dialogRef.close(this.title);
  }

  close() {
    this.dialogRef.close();
  }

}
