export interface HealingSession {
  id: string;
  patientId: string;
  healerId: string;

  medicalCondition?: string;
  healingDetails?: HealingDetails;
  labFindings?: string[];

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
  healingSession: any;
  symptoms: any;
  medications: Medication[];
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

interface HealingDetails {
  protocolName: string;
  bookReference: string;
  treatmentSteps: TreatmentStep[];
}

export interface TreatmentStep {
  tag: string;
  sweepType?: string;
  notes?: string;
  cycles?: number;
  step: number;
  targets?: string[];
  color?: string;
}

export type CaseStatus = "DRAFT" | "ACTIVE" | "COMPLETED" | "CANCELLED";

export interface Medication {
  medicationName: string;
  dosage?: string;
  morningDose?: boolean;
  afternoonDose?: boolean;
  nightDose?: boolean;
}

// ChakraScan Data
export interface ChakraData {
  id: string;
  name: string;
  color: string;
  state: string;
  highlight?: string;
}

// HealingSession Wizard Form Interfaces
export interface ChakraReadingRequest {
  chakraId: string;
  chakraName: string;
  energyLevel?: string;
  activationLevel?: string;
  notes?: string;
}

export interface ChakraScanRequest {
  scanType: "PRE" | "POST";
  notes?: string;
  readings: ChakraReadingRequest[];
}

export interface SymptomFeedbackRequest {
  caseSymptomId: string;
  rating: number; // 0-10 scale
  notes?: string;
}

export interface ProtocolColorRequest {
  colorId: string;
  usageType: "CLEANSING" | "ENERGISING";
}

export interface ProtocolEntryRequest {
  chakraId?: string;
  targetArea?: string;
  technique?: string;
  notes?: string;
  colors?: ProtocolColorRequest[];
  protocolName?: string;
  referenceBook?: string;
  detailedProtocol?: string
}

export interface QualitativeFeedbackRequest {
  feedbackType: "INTERIM" | "FINAL";
  feedbackText?: string;
  patientRemarks?: string;
}

export interface HealingSessionFormData {
  sessionNumber?: number;
  sessionDate: string;
  startTime: string;
  endTime: string;
  status: "DRAFT" | "COMPLETED";
  scans: ChakraScanRequest[];
  symptomFeedbacks: SymptomFeedbackRequest[];
  protocolEntries: ProtocolEntryRequest[];
  qualitativeFeedbacks: QualitativeFeedbackRequest[];
}

export interface ChakraScanProps {
  value: ChakraData[];
  onChange: (chakras: ChakraData[]) => void;
}

export interface ColorOption {
  id: string;
  code: string;
  name: string;
  colorHex?: string;
  properties?: string[];
  healerId?: string | null;
}

export interface ChakraOption {
  id: string;
  code: string;
  name: string;
  category?: string;
  location?: string;
  healerId?: string | null;
}

export interface CreateSessionPayload {
  caseId: string;
  sessionDate: string;
  startTime: string;
  endTime: string;
  scans: ChakraScanRequest[];
  symptomFeedbacks?: SymptomFeedbackRequest[];
  qualitativeFeedbacks?: QualitativeFeedbackRequest[];
  protocolEntries?: Array<{
    chakraId: string | null;
    targetArea?: string;
    technique?: string;
    notes?: string;
    colors: ProtocolColorRequest[];
    protocolName?: string;
    referenceBook?: string;
    detailedProtocol?: string;
  }>;
}

// --------------------------------------------------------------

// types.ts (or inside component)
export interface ChakraWithLevels {
  id: string;
  name: string;
  color?: string; // optional styling
  energyLevel: "Normal" | "Congested" | "Depleted";
  activationLevel: "Normal" | "Overactivated" | "Underactivated";
}

// Region definitions
export type BodyRegion =
  | "Head"
  | "Throat"
  | "Chest"
  | "AbdomenPelvis"
  | "Arms"
  | "Legs";

export const regionChakras: Record<BodyRegion, string[]> = {
  Head: [
    "Crown Chakra",
    "Forehead Chakra",
    "Ajna Chakra",
    "Left Jaw Minor Chakra",
    "Right Jaw Minor Chakra",
  ],
  Throat: ["Throat Chakra"],
  Chest: [
    "Front Heart Chakra",
    "Back Heart Chakra",
    "Front Solar Plexus Chakra",
    "Back Solar Plexus Chakra",
  ],
  AbdomenPelvis: [
    "Navel Chakra",
    "Meng Mein Chakra",
    "Sex Chakra",
    "Basic Chakra",
    "Front Spleen Chakra",
    "Back Spleen Chakra",
    "Left Hip Minor Chakra",
    "Right Hip Minor Chakra",
  ],
  Arms: [
    "Left Shoulder Minor Chakra",
    "Right Shoulder Minor Chakra",
    "Left Elbow Minor Chakra",
    "Right Elbow Minor Chakra",
    "Left Hand Chakra",
    "Right Hand Chakra",
  ],
  Legs: [
    "Left Knee Minor Chakra",
    "Right Knee Minor Chakra",
    "Left Sole Chakra",
    "Right Sole Chakra",
  ],
};

interface Colors {
  color: string;
  properties: string[]
}

export interface HPUTable {
  chakra: string;
  energisingColors: Colors[];
  activationColors: Colors[];
}