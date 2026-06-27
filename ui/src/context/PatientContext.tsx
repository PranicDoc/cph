import {
  createContext,
  useContext,
  useState,
  useEffect,
  useMemo,
  ReactNode,
} from "react";
import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios"; // Make sure this path points to your axios instance
import { useAuth } from "./AuthContext";

// ---- TYPES ----

export type CaseStatus = "DRAFT" | "ACTIVE" | "CLOSED" | "CANCELLED";

export type Patient = {
  id: string;
  healingCaseId?: string | null; // Added to match your Patients.tsx component
  firstName: string;
  lastName: string;
  dob: string;
  gender: string;
  contact: string;
  status: "DRAFT" | "ACTIVE" | "CLOSED"; // Fixed to match Patients.tsx
};

export interface HealingCase {
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
  createdAt: string;
  updatedAt: string;
}

// ---- DERIVED TYPE ----

export type ComputedPatient = Patient & {
  fullName: string;
  age: number;
  visitCount: number;
  lastVisit: string | null;
};

// ---- CONTEXT TYPE ----

type PatientContextType = {
  patients: ComputedPatient[];
  selectedPatient: ComputedPatient | null;
  HealingCase: HealingCase | null;
  selectPatient: (patientId: string | null) => void;
  isLoading: boolean;
  error: Error | null;
};

// ---- CONTEXT ----

const PatientContext = createContext<PatientContextType | undefined>(undefined);

// ---- UTILS ----

const calculateAge = (dob: string): number => {
  if (!dob) return 0;
  const birth = new Date(dob);
  const today = new Date();
  let age = today.getFullYear() - birth.getFullYear();
  const m = today.getMonth() - birth.getMonth();
  if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) {
    age--;
  }
  return age;
};

const calculateGender = (gender: string) => {
  if (gender === "M") return "Male";
  if (gender === "F") return "Female";
  return "Other";
};

// ---- API FETCHERS ----

const fetchPatients = async (): Promise<Patient[]> => {
  const res = await api.get("/api/v1/patients");
  return res.data;
};

const fetchHealingCases = async (): Promise<HealingCase[]> => {
  //   const res = await api.get("/api/v1/cases");
  //   return res.data;
  return [];
};

// ---- PROVIDER ----

export const PatientProvider = ({ children }: { children: ReactNode }) => {
  const [selectedPatientId, setSelectedPatientId] = useState<string | null>(
    () => {
      return sessionStorage.getItem("selectedPatientId") || null;
    }
  );
  const { isAuthenticated } = useAuth();

  useEffect(() => {
    if (selectedPatientId) {
      sessionStorage.setItem("selectedPatientId", selectedPatientId);
    } else {
      sessionStorage.removeItem("selectedPatientId");
    }
  }, [selectedPatientId]);

  const {
    data: rawPatients = [],
    isPending: isLoadingPatients,
    error: patientsError,
  } = useQuery({
    queryKey: ["patients"],
    queryFn: fetchPatients,
    enabled: isAuthenticated,
  });

  const {
    data: rawCases = [],
    isPending: isLoadingCases,
    error: casesError,
  } = useQuery({
    queryKey: ["healingCases"],
    queryFn: fetchHealingCases,
    enabled: isAuthenticated,
  });

  const computedPatients = useMemo<ComputedPatient[]>(() => {
    if (!Array.isArray(rawPatients)) return [];
    return rawPatients.map((p) => {
      const patientSessions = rawCases
        .filter((s) => s.patientId === p.id)
        .sort(
          (a, b) =>
            new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        );

      return {
        ...p,
        fullName: `${p.firstName} ${p.lastName}`,
        age: calculateAge(p.dob),
        gender: calculateGender(p.gender),
        visitCount: patientSessions.length,
        lastVisit: patientSessions[0]?.createdAt || null,
      };
    });
  }, [rawPatients, rawCases]);

  const selectedPatient = useMemo(() => {
    if (!selectedPatientId) return null;
    return computedPatients.find((p) => p.id === selectedPatientId) || null;
  }, [computedPatients, selectedPatientId]);

  const activeHealingCase = useMemo(() => {
    if (!selectedPatientId) return null;
    return (
      rawCases
        .filter((s) => s.patientId === selectedPatientId)
        .sort(
          (a, b) =>
            new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        )[0] || null
    );
  }, [rawCases, selectedPatientId]);

  return (
    <PatientContext.Provider
      value={{
        patients: computedPatients,
        selectedPatient,
        HealingCase: activeHealingCase,
        selectPatient: setSelectedPatientId,
        isLoading: isLoadingPatients || isLoadingCases,
        error:
          patientsError || casesError ? new Error("Failed to load data") : null,
      }}
    >
      {children}
    </PatientContext.Provider>
  );
};

// ---- HOOK ----

export const usePatient = () => {
  const context = useContext(PatientContext);
  if (!context) {
    throw new Error("usePatient must be used within PatientProvider");
  }
  return context;
};
