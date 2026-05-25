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
    console.log(data);
  }

  save() {
    console.log(this.start);
    this.dialogRef.close({action: 'save',title: this.title, kosten: this.kosten, tripId: this.tripId, start:this.start});
  }

  close() {
    this.dialogRef.close();
  }

  delete() {
    this.dialogRef.close({ action: 'delete' });
  }

  edit(){
    this.dialogRef.close({ action: 'edit' });
  }

}
