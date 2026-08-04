import { useEffect, useMemo, useRef, useState } from "react";
import { useForm } from "@tanstack/react-form";
import { useParams, useNavigate } from "react-router-dom";
import { Pencil } from "lucide-react";
import api from "../lib/axios";
import { DynamicFormEngine } from "../components/DynamicFormEngine";
import { buildSessionSchema } from "./schemas/sessionSchema";
import { SessionHeader } from "./SessionHeader";
import { HealingSession, TreatmentStep } from "../interfaces";
import { useProtocols, type Protocol } from "../hooks/admin/useMasterData";
import { FieldDisplay } from "../components/FieldDisplay";
import { HealingSessionWizard } from "../components/HealingSession";
import {
  LabFindingsView,
  MedicationsView,
} from "../components/CaseViewWidgets";
import { usePatient } from "../context/PatientContext";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "../components/ui/accordion";
import { DataTable } from "@/components/ui/data-table";
import { Button } from "@/components/ui/button";
import type { ColumnDef } from "@tanstack/react-table";

const scanReadingColumns: ColumnDef<
  { id?: string; chakraName: string; energyLevel?: string; activationLevel?: string },
  unknown
>[] = [
  { accessorKey: "chakraName", header: "Chakra" },
  { accessorKey: "energyLevel", header: "Energy" },
  { accessorKey: "activationLevel", header: "Activation" },
];

// Which of the 3 legacy page-number fields a protocol's source book buckets into.
const BOOK_FIELD_MAP: Record<string, "basicBookPg" | "advancedBookPg" | "psychotherapyPg"> = {
  "Advanced Pranic Healing": "advancedBookPg",
  "The Ancient Science and Art of Pranic Healing": "basicBookPg",
  "Pranic Psychotherapy": "psychotherapyPg",
  "Practical Psychic Self-Defense for Home and Office": "basicBookPg",
};
const bookToField = (book: string) => BOOK_FIELD_MAP[book] ?? "basicBookPg";

type SessionFormValues = {
  medicalCondition: string;
  symptoms: string[];
  medications: string[];
  labFindings: (File | string)[];
  complementaryDetails: string;
  infectiousDetails: string;
  healingDetails: {
    protocolName: string;
    bookReference: string;
    detailedProtocol: string;
    treatmentSteps: TreatmentStep[];
    selectedProtocols: Protocol[];
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

  const { data: protocols = [] } = useProtocols("PROTOCOL");
  const schema = useMemo(
    () => buildSessionSchema(protocols, sessionData?.status === "DRAFT"),
    [protocols, sessionData?.status]
  );

  // Best-effort: reconstruct which catalog protocols a comma-joined legacy
  // protocolName string refers to, by exact name match. Names that don't
  // match anything (pre-catalog free text) simply won't pre-select a chip.
  const reconstructSelectedProtocols = (protocolNameCsv: string): Protocol[] => {
    if (!protocolNameCsv) return [];
    const names = protocolNameCsv.split(",").map((n) => n.trim()).filter(Boolean);
    return names
      .map((name) => protocols.find((p) => p.name === name))
      .filter((p): p is Protocol => !!p);
  };

  // Persist edit mode per step (only step 0 for now)
  const stepEditModeRef = useRef<Record<number, boolean>>({ 0: false });

  // Snapshot of the last‑fetched form values (used by Cancel)
  const initialFormValuesRef = useRef<SessionFormValues>({
    medicalCondition: "",
    symptoms: [],
    medications: [],
    labFindings: [] as (File | string)[],
    complementaryDetails: "",
    infectiousDetails: "",
    healingDetails: {
      protocolName: "",
      bookReference: "",
      detailedProtocol: "",
      treatmentSteps: [],
      selectedProtocols: [],
    },
  });

  const sessionForm = useForm({
    defaultValues: {
      medicalCondition: "",
      symptoms: [] as string[],
      medications: [] as string[],
      labFindings: [] as (File | string)[],
      complementaryDetails: "",
      infectiousDetails: "",
      healingDetails: {
        protocolName: "",
        bookReference: "",
        detailedProtocol: "",
        treatmentSteps: [] as TreatmentStep[],
        selectedProtocols: [] as Protocol[],
      },
    },
    onSubmit: async ({ value }) => {
      setIsSubmitting(true);
      try {
        const { labFindings, healingDetails, ...rest } = value;
        const { selectedProtocols, ...restHealingDetails } = healingDetails;
        const files = labFindings.filter((f): f is File => f instanceof File);
        const existingKeys = labFindings.filter(
          (f): f is string => typeof f === "string"
        );

        const protocolName = selectedProtocols.map((p) => p.name).join(", ");
        const buckets: Record<"basicBookPg" | "advancedBookPg" | "psychotherapyPg", string[]> = {
          basicBookPg: [],
          advancedBookPg: [],
          psychotherapyPg: [],
        };
        selectedProtocols.forEach((p) => {
          buckets[bookToField(p.sourceBook)].push(String(p.pageNumber));
        });

        const payload = {
          ...rest,
          healingDetails: { ...restHealingDetails, protocolName },
          basicBookPg: buckets.basicBookPg.join(","),
          advancedBookPg: buckets.advancedBookPg.join(","),
          psychotherapyPg: buckets.psychotherapyPg.join(","),
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
    labFindings: data.labFindings || [],
    complementaryDetails: data.complementaryModalities || "",
    infectiousDetails: data.infectiousIllness || "",
    healingDetails: {
      protocolName: data.healingDetails?.protocolName || "",
      bookReference: data.healingDetails?.bookReference || "",
      detailedProtocol: data.healingDetails?.detailedProtocol || "",
      treatmentSteps: data.healingDetails?.treatmentSteps || [],
      selectedProtocols: reconstructSelectedProtocols(data.healingDetails?.protocolName || ""),
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
          labFindings: data?.labFindings || [],
          complementaryDetails: data?.complementaryDetails || "",
          infectiousDetails: data?.infectiousDetails || "",
          healingDetails: {
            protocolName: data.healingDetails?.protocolName || "",
            detailedProtocol: data.healingDetails?.detailedProtocol || "",
            bookReference: data.healingDetails?.bookReference || "",
            treatmentSteps: data.healingDetails?.treatmentSteps || [],
            selectedProtocols: reconstructSelectedProtocols(data.healingDetails?.protocolName || ""),
          },
        };

        populateForm(resetValues);
        initialFormValuesRef.current = resetValues;
      } catch (error) {
        console.error("Error fetching healing session:", error);
      }
    };

    fetchData();
  }, [healingCaseId]);

  // The protocol catalog loads asynchronously and may not be ready yet when
  // the case data above is first mapped into the form — re-derive the
  // multi-select's value once the catalog arrives so pre-selection isn't lost.
  useEffect(() => {
    if (!sessionData?.healingDetails?.protocolName || protocols.length === 0) return;
    const reconstructed = reconstructSelectedProtocols(sessionData.healingDetails.protocolName);
    if (reconstructed.length > 0) {
      sessionForm.setFieldValue("healingDetails.selectedProtocols", reconstructed);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [protocols, sessionData]);

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

  // Dynamic label for the next action button — driven by the case's own
  // status (DRAFT | ACTIVE | CLOSED), not any per-session sub-state.
  const getNextButtonLabel = () => {
    if (!sessionData) return "Proceed to Session";
    if (sessionData.status === "DRAFT") return "Start Session";
    if (sessionData.status === "CLOSED") return "Case Summary";
    return "Proceed to Session";
  };

  // For a closed case, "next" means viewing its generated report instead of
  // continuing the session wizard.
  const handleNextAction = () => {
    if (sessionData?.status === "CLOSED") {
      navigate(`/cases/${healingCaseId}/summary`);
      return;
    }
    goToNextStep();
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
            {sessionData.status !== "CLOSED" && (
              <Button
                type="button"
                variant="ghost"
                size="icon"
                onClick={toggleEdit}
                className="hover:bg-background"
                aria-label="Edit session"
              >
                <Pencil className="w-5 h-5 text-primary" />
              </Button>
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
              <dt className="text-sm font-medium text-muted-foreground">
                Medications
              </dt>
              <dd>
                <MedicationsView medications={sessionData.medications ?? []} />
              </dd>
            </div>
            <div className="space-y-1">
              <dt className="text-sm font-medium text-muted-foreground">
                Lab Findings
              </dt>
              <dd>
                <LabFindingsView keys={sessionData.labFindings ?? []} />
              </dd>
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
                      <p>
                        Psychotherapy book pg: {sessionData?.psychotherapyPg}
                      </p>
                    )}
                  </div>
                ) : null
              }
            />
            {sessionData?.healingSession?.length > 0 && (
              <div className="sm:col-span-2">
                <Accordion type="multiple">
                  {sessionData.healingSession.map((session) => (
                    <AccordionItem
                      key={session.id}
                      value={`session-${session.id}`}
                    >
                      <AccordionTrigger>
                        Session {session.sessionNumber}
                      </AccordionTrigger>

                      <AccordionContent className="space-y-6">
                        {session.scans.map((scan) => (
                          <div key={scan.id}>
                            <h4 className="font-semibold mb-2">
                              {scan.scanType} Scan
                            </h4>

                            <DataTable
                              columns={scanReadingColumns}
                              data={scan.readings}
                              enableSorting={false}
                              className="rounded-lg"
                              headerRowClassName="bg-muted hover:bg-muted"
                              headCellClassName="px-3 py-2 text-sm font-semibold text-text normal-case tracking-normal"
                              cellClassName="px-3 py-2"
                            />
                          </div>
                        ))}
                      </AccordionContent>
                    </AccordionItem>
                  ))}
                </Accordion>
              </div>
            )}
          </dl>

          {/* Navigation buttons */}
          <div className="flex flex-col sm:flex-row justify-between items-center gap-4 pt-4 mt-6 border-t border-border">
            <Button
              type="button"
              variant="outline"
              onClick={() => navigate("/patients")}
              className="w-full sm:w-auto px-6 py-2.5 h-auto border-2 text-text"
            >
              Back to Patients
            </Button>
            <Button
              type="button"
              onClick={handleNextAction}
              className="flex-1 sm:flex-none px-6 py-2.5 h-auto"
            >
              {getNextButtonLabel()}
            </Button>
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
        <DynamicFormEngine form={sessionForm} schema={schema} />

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
            <Button
              type="button"
              variant="outline"
              onClick={() => navigate("/patients")}
              className="w-full sm:w-auto px-6 py-2.5 h-auto border-2 text-text"
            >
              Back to Patients
            </Button>

            {sessionData.status !== "DRAFT" && (
              <Button
                type="button"
                variant="outline"
                onClick={handleCancel}
                className="w-full sm:w-auto px-6 py-2.5 h-auto border-2 text-text"
              >
                Cancel
              </Button>
            )}
          </div>

          <Button
            type={isEdit ? "submit" : "button"}
            onClick={isEdit ? undefined : goToNextStep}
            disabled={isSubmitting}
            className="flex-1 sm:flex-none px-6 py-2.5 h-auto gap-2"
          >
            {getNextButtonLabel()}
          </Button>
        </div>
      </form>
    </div>
  );
}
