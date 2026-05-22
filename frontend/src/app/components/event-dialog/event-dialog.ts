import {Component, Inject} from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
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
  kosten: number | undefined;
  tripId: bigint | undefined;
  start: string = "";

  mode: 'create' | 'edit' | 'show'= 'create';

  constructor(private dialogRef: MatDialogRef<EventDialog>, @Inject(MAT_DIALOG_DATA) public data: any) {
    if(data) {
      this.mode = data.mode || 'create';
      this.title = data.title || '';
      this.kosten = data.kosten || undefined;
      this.tripId = data.tripId || undefined;
      this.start = data.start || "";
    }
  }

  save() {
    if(this.mode === 'create') {
      this.dialogRef.close({action: 'save',title: this.title, kosten: this.kosten, tripid: this.tripId});
    }
    if(this.mode === 'edit') {
      this.dialogRef.close({action: 'save',title: this.title, kosten: this.kosten, tripid: this.tripId, start:this.start +"T00:00:00"});
    }
  }

  close() {
    this.dialogRef.close();
  }

  delete() {
    this.dialogRef.close({ action: 'delete' });
  }

}
