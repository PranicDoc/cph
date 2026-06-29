import { useEffect, useRef, useState } from "react";
import { useForm } from "@tanstack/react-form";
import { useParams, useNavigate } from "react-router-dom";
import { Pencil } from "lucide-react";
import api from "../lib/axios";
import { DynamicFormEngine } from "../components/DynamicFormEngine";
import { sessionSchema } from "./schemas/sessionSchema";
import { SessionHeader } from "./SessionHeader";
import { TreatmentSteps } from "../components/TreatmentSteps";
import { HealingSession, TreatmentStep } from "../interfaces";
import { FieldDisplay } from "../components/FieldDisplay";
import { HealingSessionWizard } from "../components/HealingSession";
import { LabFindingsView, MedicationsView } from "../components/CaseViewWidgets";
import { usePatient } from "../context/PatientContext";

type SessionFormValues = {
  medicalCondition: string;
  symptoms: string[];
  medications: string[];
  basicBookPg: string;
  advancedBookPg: string;
  psychotherapyPg: string;
  labFindings: (File | string)[];
  complementaryDetails: string;
  infectiousDetails: string;
  healingDetails: {
    protocolName: string;
    bookReference: string;
    detailedProtocol: string;
    treatmentSteps: TreatmentStep[];
  };
};

export default function Session() {
  const { healingCaseId } = useParams();
  const { selectPatient } = usePatient();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [sessionData, setSessionData] = useState<HealingSession | null>(null);
  const [isEdit, setIsEdit] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);
  const navigate = useNavigate();

  // Persist edit mode per step (only step 0 for now)
  const stepEditModeRef = useRef<Record<number, boolean>>({ 0: false });

  // Snapshot of the last‑fetched form values (used by Cancel)
  const initialFormValuesRef = useRef<SessionFormValues>({
    medicalCondition: "",
    symptoms: [],
    medications: [],
    basicBookPg: "",
    advancedBookPg: "",
    psychotherapyPg: "",
    labFindings: [] as (File | string)[],
    complementaryDetails: "",
    infectiousDetails: "",
    healingDetails: {
      protocolName: "",
      bookReference: "",
      detailedProtocol: "",
      treatmentSteps: [],
    },
  });

  const sessionForm = useForm({
    defaultValues: {
      medicalCondition: "",
      symptoms: [] as string[],
      medications: [] as string[],
      basicBookPg: "",
      advancedBookPg: "",
      psychotherapyPg: "",
      labFindings: [] as (File | string)[],
      complementaryDetails: "",
      infectiousDetails: "",
      healingDetails: {
        protocolName: "",
        bookReference: "",
        detailedProtocol: "",
        treatmentSteps: [],
      },
    },
    onSubmit: async ({ value }) => {
      setIsSubmitting(true);
      try {
        const { labFindings, ...rest } = value;
        const files = labFindings.filter((f): f is File => f instanceof File);
        const existingKeys = labFindings.filter((f): f is string => typeof f === "string");
        const payload = {
          ...rest,
          labFindings: existingKeys,
          healingCaseId,
          complementaryTreatments: !!value.complementaryDetails,
          infectiousIllness: !!value.infectiousDetails,
        };
        const formData = new FormData();
        formData.append(
          "data",
          new Blob([JSON.stringify(payload)], { type: "application/json" })
        );
        files.forEach((file) => formData.append("labFindings", file));
        const { data } = await api.patch("/api/v1/cases", formData, {
          headers: { "Content-Type": undefined },
        });
        setSessionData(data);
        const updatedValues = mapResponseToFormValues(data);
        populateForm(updatedValues);
        initialFormValuesRef.current = updatedValues;
      } catch (err) {
        console.error(err);
        throw err;
      } finally {
        setIsSubmitting(false);
      }
    },
  });

  const mapResponseToFormValues = (data: any): SessionFormValues => ({
    medicalCondition: data.medicalCondition || "",
    symptoms: data.symptoms || [],
    medications: data.medications || [],
    basicBookPg: data.basicBookPg || "",
    advancedBookPg: data.advancedBookPg || "",
    psychotherapyPg: data.psychotherapyPg || "",
    labFindings: data.labFindings || [],
    complementaryDetails: data.complementaryModalities || "",
    infectiousDetails: data.infectiousIllness || "",
    healingDetails: {
      protocolName: data.healingDetails?.protocolName || "",
      bookReference: data.healingDetails?.bookReference || "",
      detailedProtocol: data.healingDetails?.detailedProtocol || "",
      treatmentSteps: data.healingDetails?.treatmentSteps || [],
    },
  });

  const hasFetched = useRef(false);

  useEffect(() => {
    if (hasFetched.current) return;
    hasFetched.current = true;

    const fetchData = async () => {
      try {
        const { data } = await api.get(`/api/v1/cases/${healingCaseId}`);
        setSessionData(data);
        if (data.patientId) selectPatient(data.patientId);

        const initialEdit = data.status === "DRAFT";
        setIsEdit(initialEdit);
        stepEditModeRef.current[0] = initialEdit;

        const resetValues: SessionFormValues = {
          medicalCondition: data.medicalCondition || "",
          symptoms: data.symptoms || [],
          medications: data.medications || [],
          basicBookPg: data.basicBookPg || "",
          advancedBookPg: data.advancedBookPg || "",
          psychotherapyPg: data.psychotherapyPg || "",
          labFindings: data?.labFindings || [],
          complementaryDetails: data.complementaryModalities || "",
          infectiousDetails: data.infectiousIllness || "",
          healingDetails: {
            protocolName: data.healingDetails?.protocolName || "",
            detailedProtocol: data.healingDetails?.detailedProtocol || "",
            bookReference: data.healingDetails?.bookReference || "",
            treatmentSteps: data.healingDetails?.treatmentSteps || [],
          },
        };

        populateForm(resetValues);
        initialFormValuesRef.current = resetValues;
      } catch (error) {
        console.error("Error fetching healing session:", error);
      }
    };

    fetchData();
  }, [healingCaseId, sessionForm.reset]);

  // ---- Navigation handlers ----
  const goToNextStep = () => {
    // Save current edit mode for step 0
    stepEditModeRef.current[0] = isEdit;
    setCurrentStep(1); // Proceed to next step (placeholder)
  };

  const goToPreviousStep = () => {
    if (currentStep > 0) {
      // Go back to step 0 and restore its edit mode
      const savedMode = stepEditModeRef.current[0];
      setIsEdit(savedMode);
      setCurrentStep(0);
    }
  };

  // Cancel editing (only when not DRAFT)
  const handleCancel = () => {
    populateForm(initialFormValuesRef.current);
    setIsEdit(false);
    stepEditModeRef.current[0] = false;
  };

  // Toggle edit mode manually (via edit icon)
  const toggleEdit = () => {
    setIsEdit(true);
    populateForm(initialFormValuesRef.current);
    stepEditModeRef.current[0] = true;
  };

  const populateForm = (data: SessionFormValues) => {
    Object.entries(data).forEach(([key, value]) => {
      sessionForm.setFieldValue(key as keyof SessionFormValues, value);
    });
  };

  // Dynamic label for the next action button
  const getNextButtonLabel = () => {
    if (!sessionData) return "Proceed to Session";
    if (sessionData.status === "DRAFT") return "Start Session";
    if (sessionData.status === "COMPLETED") return "Get Report";
    return "Proceed to Session";
  };

  // ----------------- Loading state -----------------
  if (!sessionData) {
    return <div className="p-6 text-center">Loading session...</div>;
  }

  // ---------- STEP 1 (placeholder for future step) ----------
  if (currentStep === 1) {
    return (
      <div className="space-y-6">
        <SessionHeader healingSession={sessionData} />
        {healingCaseId && (
          <HealingSessionWizard
            caseId={healingCaseId}
            symptoms={sessionData?.symptoms}
            onClose={goToPreviousStep}
          />
        )}
      </div>
    );
  }

  // ---------- VIEW MODE (step 0) ----------
  if (!isEdit) {
    return (
      <div className="space-y-6">
        <SessionHeader healingSession={sessionData} />

        <div className="bg-card border border-border rounded-xl p-6 shadow-sm">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-semibold">Patient Medical Details</h2>
            {sessionData.status !== "COMPLETED" && (
              <button
                type="button"
                onClick={toggleEdit}
                className="p-2 rounded-lg hover:bg-background transition-colors"
                aria-label="Edit session"
              >
                <Pencil className="w-5 h-5 text-primary" />
              </button>
            )}
          </div>

          <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <FieldDisplay
              label="Chief Complaint"
              value={sessionData.medicalCondition}
            />
            <FieldDisplay
              label="Symptoms"
              value={sessionData.symptoms
                ?.map((s: any) => s.symptomName)
                .join(", ")}
            />
            <div className="space-y-1">
              <dt className="text-sm font-medium text-muted-foreground">Medications</dt>
              <dd><MedicationsView medications={sessionData.medications ?? []} /></dd>
            </div>
            <div className="space-y-1">
              <dt className="text-sm font-medium text-muted-foreground">Lab Findings</dt>
              <dd><LabFindingsView keys={sessionData.labFindings ?? []} /></dd>
            </div>
            <FieldDisplay
              label="Complementary Details"
              value={sessionData.complementaryDetails}
            />
            <FieldDisplay
              label="Infectious Details"
              value={sessionData.infectiousIllness}
            />
            <FieldDisplay
              label="Protocol Details"
              value={
                sessionData.healingDetails ? (
                  <div>
                    <p>{sessionData.healingDetails.protocolName}</p>
                    {sessionData?.basicBookPg && (
                      <p>Basic book pg: {sessionData?.basicBookPg}</p>
                    )}
                    {sessionData?.advancedBookPg && (
                      <p>Advanced book pg: {sessionData?.advancedBookPg}</p>
                    )}
                    {sessionData?.psychotherapyPg && (
                      <p>Psychotherapy book pg: {sessionData?.psychotherapyPg}</p>
                    )}
                  </div>
                ) : null
              }
            />
          </dl>

          {/* Navigation buttons */}
          <div className="flex flex-col sm:flex-row justify-between items-center gap-4 pt-4 mt-6 border-t border-border">
            <button
              type="button"
              onClick={() => navigate('/patients')}
              className="w-full sm:w-auto px-6 py-2.5 border-2 border-border text-text font-medium rounded-lg hover:bg-background transition-colors"
            >
              ← Back to Patients
            </button>
            <button
              type="button"
              onClick={goToNextStep}
              className="flex-1 sm:flex-none px-6 py-2.5 bg-primary text-white font-medium rounded-lg hover:bg-primary-dark transition-colors"
            >
              {getNextButtonLabel()}
            </button>
          </div>
        </div>
      </div>
    );
  }

  // ---------- EDIT MODE (step 0) ----------
  return (
    <div className="space-y-6">
      <SessionHeader healingSession={sessionData} />

      <form
        onSubmit={async (e) => {
          e.preventDefault();
          try {
            await sessionForm.handleSubmit();
            goToNextStep();
          } catch {
            // error already logged in onSubmit
          }
        }}
        className="bg-card border border-border rounded-xl p-6 shadow-sm space-y-6"
      >
        <DynamicFormEngine form={sessionForm} schema={sessionSchema} />

        {/* <sessionForm.Field name="healingDetails">
          {(field) => (
            <TreatmentSteps
              value={field.state.value}
              onChange={field.handleChange}
            />
          )}
        </sessionForm.Field> */}

        {/* Navigation buttons */}
        <div className="flex flex-col sm:flex-row justify-between items-center gap-4 pt-4 border-t border-border">
          <div className="flex w-full sm:w-auto gap-3">
            <button
              type="button"
              onClick={() => navigate('/patients')}
              className="w-full sm:w-auto px-6 py-2.5 border-2 border-border text-text font-medium rounded-lg hover:bg-background transition-colors"
            >
              ← Back to Patients
            </button>

            {sessionData.status !== "DRAFT" && (
              <button
                type="button"
                onClick={handleCancel}
                className="w-full sm:w-auto px-6 py-2.5 border-2 border-border text-text font-medium rounded-lg hover:bg-background transition-colors"
              >
                Cancel
              </button>
            )}
          </div>

          <button
            type={isEdit ? "submit" : "button"}
            onClick={isEdit ? undefined : goToNextStep}
            disabled={isSubmitting}
            className="flex-1 sm:flex-none px-6 py-2.5 bg-primary text-white font-medium rounded-lg hover:bg-primary-dark transition-colors flex items-center justify-center gap-2"
          >
            {getNextButtonLabel()}
          </button>
        </div>
      </form>
    </div>
  );
}
