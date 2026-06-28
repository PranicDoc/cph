export interface UpcomingSession {
  sessionId: string;
  patientFirstName: string;
  patientLastName: string;
  sessionDate: string;
  startTime: string | null;
  status: "DRAFT" | "COMPLETED";
}

export interface PendingReport {
  sessionId: string;
  patientFirstName: string;
  patientLastName: string;
  sessionDate: string;
}

export interface FollowupPatient {
  patientId: string;
  patientFirstName: string;
  patientLastName: string;
  lastSessionDate: string;
  daysSinceLastSession: number;
}
