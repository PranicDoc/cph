export interface HealingSession {
  id: string;
  patientId: string;
  healerId: string;

  medicalCondition?: string;
  healingDetails?: string;
  labFindings?: string;

  complementaryTreatments?: boolean;
  complementaryDetails?: string;

  infectiousIllness?: boolean;
  infectiousDetails?: string;

  basicBookPg?: string;
  advancedBookPg?: string;
  psychotherapyPg?: string;

  status: CaseStatus;

  createdAt: string; // ISO string (Instant equivalent)
  updatedAt: string;
  medicalSnapshot: MedicalSnapshot;
  healingSession: any
}

export interface MedicalSnapshot {
  generalHealth: string;
  diet: string;
  exercise: string;
  exerciseFrequency: string;
  hypertension: boolean;
  giIssues: boolean;
  stressDepressionAnxiety: boolean;
  pregnant: boolean;
  cancer: boolean;
  cancerLocation: string;
  cancerDetails: string;
  additionalNotes: string;
}

export interface PageNumbers {
  basicBookPg: string;
  advancedBookPg: string;
  psychotherapyPg: string;
}

export type CaseStatus = "DRAFT" | "ACTIVE" | "COMPLETED" | "CANCELLED";
