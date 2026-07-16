import {Component, inject} from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';

type MessageType = 'info' | 'warning' | 'error';

interface MessageDialogData {
  message: string;
  /** Optional heading; defaults to a label matching the type. */
  title?: string;
  /** Picks icon and badge color; defaults to 'warning'. */
  type?: MessageType;
}

const DEFAULT_TITLES: Record<MessageType, string> = {
  info: 'Info',
  warning: 'Notice',
  error: 'Error',
};

const ICONS: Record<MessageType, string> = {
  info: 'info',
  warning: 'warning_amber',
  error: 'error_outline',
};

@Component({
  selector: 'app-message-dialog',
  imports: [],
  templateUrl: './message-dialog.html',
  styleUrl: './message-dialog.css',
})
export class MessageDialog {
  private readonly dialogRef = inject(MatDialogRef<MessageDialog>);
  readonly data = inject<MessageDialogData>(MAT_DIALOG_DATA);

  readonly type: MessageType = this.data?.type ?? 'warning';
  readonly title = this.data?.title ?? DEFAULT_TITLES[this.type];
  readonly icon = ICONS[this.type];
  readonly message = this.data?.message ?? '';

  close(){
    this.dialogRef.close();
  }
}
