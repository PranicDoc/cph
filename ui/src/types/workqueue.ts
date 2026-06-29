export interface UpcomingSession {
  id: string;
  patientFirstName: string;
  patientLastName: string;
  date: string;
  startTime: string | null;
  type: "DRAFT_SESSION" | "ACTIVE_CASE";
}

export interface PendingReport {
  caseId: string;
  patientFirstName: string;
  patientLastName: string;
  closureDate: string | null;
}

export interface FollowupPatient {
  caseId: string;
  patientFirstName: string;
  patientLastName: string;
  feedbackToken: string;
  sentAt: string;
  isFinal: boolean;
}
