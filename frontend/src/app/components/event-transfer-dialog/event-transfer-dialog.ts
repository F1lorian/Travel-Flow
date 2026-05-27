import {Component, inject, Inject} from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import {FormsModule} from '@angular/forms';
import {EventService} from '../../../event-adding-manual/service/event.service';

@Component({
  selector: 'app-event-transfer-dialog',
  imports: [FormsModule],
  templateUrl: './event-transfer-dialog.html',
  styleUrl: './event-transfer-dialog.css',
})
export class EventTransferDialog {
  mode: 'import' | 'export' = 'import';
  file?: File;
  fileName = '';
  eventService = inject(EventService);

  constructor(private dialogRef: MatDialogRef<EventTransferDialog>, @Inject(MAT_DIALOG_DATA) public data: any) {
    if (data) {
      this.mode = data.mode;

    }
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;

    if (!input.files?.length) {
      return;
    }

    this.file = input.files[0];
    this.fileName = this.file.name;
  }

  onFileDrop(event: DragEvent) {
    event.preventDefault();

    const files = event.dataTransfer?.files;

    if (!files?.length) {
      return;
    }

    this.file = files[0];
    this.fileName = this.file.name;
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
  }

  uploadFile() {
    if (!this.file) {
      return;
    }

    const formData = new FormData();
    formData.append('file', this.file);

    this.eventService.importEvents(formData).subscribe({
      next: (events) => {
        this.dialogRef.close(events);
      },
      error: () => {
        console.error('Import fehlgeschlagen');
      }
    });
  }

  downloadEvents() {
    const data = [
      {
        title: 'Meeting',
        start: '2026-05-11T10:00'
      }
    ];

    const blob = new Blob(
      [JSON.stringify(data, null, 2)],
      { type: 'application/json' }
    );

    const url = window.URL.createObjectURL(blob);

    const a = document.createElement('a');
    a.href = url;
    a.download = 'events.json';
    a.click();

    window.URL.revokeObjectURL(url);
  }

  close(){
    this.dialogRef.close();
  }
}

