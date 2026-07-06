/**
 * Types shared between the Gmail import service and its page component. Field names and formats
 * mirror the backend DTOs: dates are ISO `yyyy-MM-dd`, times are ISO `HH:mm[:ss]`.
 */

/** Whether the current user has connected a Gmail account. */
export interface GmailConnectionStatus {
  connected: boolean;
  emailAddress: string | null;
}

/** The Google consent URL the user is redirected to when connecting Gmail. */
export interface GmailAuthUrl {
  authorizationUrl: string;
}

/** Event information extracted from one email; any field may be empty. */
export interface ExtractedEvent {
  sourceMessageId: string | null;
  title: string | null;
  description: string | null;
  date: string | null;
  startTime: string | null;
  endTime: string | null;
  timeZone: string | null;
  location: string | null;
  organizer: string | null;
}

/** The values the user confirmed on the review form, sent back to create the event. */
export interface GmailImportRequest {
  title: string;
  description: string | null;
  date: string | null;
  startTime: string | null;
  endTime: string | null;
  /** Cost of the event in EUR; 0 means free/no price. */
  kosten: number;
  /** Optional trip the created event should be attached to. */
  tripId?: number | null;
}

/** Confirmation returned after an event was created. */
export interface CreatedEvent {
  id: string;
  title: string;
  start: string;
  end: string;
  message: string;
}
