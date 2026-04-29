import { createContext, useContext, useState, ReactNode } from 'react';

// ---- TYPES ----

type CaseStatus = "DRAFT" | "ACTIVE" | "COMPLETED" | "CANCELLED";

export type Patient = {
    healingCaseId: any;
    id: string;
    firstName: string;
    lastName: string;
    dob: string;
    gender: string;
    contact: string;
    status: "DRAFT" | "ACTIVE" | "COMPLETED";
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

type ComputedPatient = Patient & {
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
    setPatients: (patients: Patient[]) => void;
    setSessions: (sessions: HealingCase[]) => void;
    selectPatient: (patientId: string) => void;
};

// ---- CONTEXT ----

const PatientContext = createContext<PatientContextType | undefined>(undefined);

// ---- UTILS ----

const calculateAge = (dob: string): number => {
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
    if (gender === "M") {
        return "Male";
    } else if (gender === "F") {
        return "Female";
    } else {
        return "Other";
    }
}

// ---- PROVIDER ----

export const PatientProvider = ({ children }: { children: ReactNode }) => {
    const [patients, setPatientsState] = useState<ComputedPatient[]>([]);
    const [healingCases, setHealingCases] = useState<HealingCase[]>([]);
    const [selectedPatient, setSelectedPatient] = useState<ComputedPatient | null>(null);
    const [HealingCase, setHealingCase] = useState<HealingCase | null>(null);

    // ---- SET PATIENTS WITH COMPUTATION ----

    const setPatients = (rawPatients: Patient[]) => {
        const computed = rawPatients.map((p) => ({
            ...p,
            fullName: `${p.firstName} ${p.lastName}`,
            age: calculateAge(p.dob),
            gender: calculateGender(p.gender),
            visitCount: 0,
            lastVisit: null,
        }));

        setPatientsState(computed);
    };

    // ---- SET SESSIONS + UPDATE PATIENT DERIVED DATA ----

    const setSessions = (allSessions: HealingCase[]) => {
        setHealingCases(allSessions);

        setPatientsState((prev) =>
            prev.map((p) => {
                const patientSessions = allSessions
                    .filter((s) => s.patientId === p.id)
                    .sort(
                        (a, b) =>
                            new Date(b.createdAt).getTime() -
                            new Date(a.createdAt).getTime()
                    );

                return {
                    ...p,
                    visitCount: patientSessions.length,
                    lastVisit: patientSessions[0]?.createdAt || null,
                };
            })
        );
    };

    // ---- SELECT PATIENT ----

    const selectPatient = (patientId: string) => {
        const patient = patients.find((p) => p.id === patientId) || null;
        setSelectedPatient(patient);

        if (!patient) return;

        const patientSessions = healingCases
            .filter((s) => s.patientId === patientId)
            .sort(
                (a, b) =>
                    new Date(b.createdAt).getTime() -
                    new Date(a.createdAt).getTime()
            );

        setHealingCase(patientSessions[0] || null);
    };

    return (
        <PatientContext.Provider
            value={{
                patients,
                selectedPatient,
                HealingCase,
                setPatients,
                setSessions,
                selectPatient,
            }}
        >
            {children}
        </PatientContext.Provider>
    );
};

export const usePatient = () => {
    const context = useContext(PatientContext);
    if (!context) {
        throw new Error('usePatient must be used within PatientProvider');
    }
    return context;
};