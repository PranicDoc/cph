import { useUiVersion } from "../ui-version";
import { useEffect, useRef, useState } from "react";
import { useForm } from "@tanstack/react-form";
import { useParams } from "react-router-dom";
import { CheckCircle2 } from "lucide-react";
import { toast } from "sonner";
import api from "../lib/axios";
import { extractDetail } from "../lib/utils";
import { DynamicFormEngine } from "../components/DynamicFormEngine";
import { patientSessionSchema } from "./schemas/patientSessionSchema";
import { TreatmentStep } from "../interfaces";
import { Button } from "@/components/ui/button";

type PatientFormValues = {
  medicalCondition: string;
  symptoms: any[];
  medications: any[];
  basicBookPg: string;
  advancedBookPg: string;
  psychotherapyPg: string;
  labFindings: File[];
  complementaryDetails: string;
  infectiousDetails: string;
  healingDetails: {
    protocolName: string;
    bookReference: string;
    detailedProtocol: string;
    treatmentSteps: TreatmentStep[];
  };
};

export default function PatientHealthForm() {
  const { isNewView } = useUiVersion();
  const { caseId } = useParams();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [caseStatus, setCaseStatus] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const hasFetched = useRef(false);

  const form = useForm({
    defaultValues: {
      medicalCondition: "",
      symptoms: [],
      medications: [],
      basicBookPg: "",
      advancedBookPg: "",
      psychotherapyPg: "",
      labFindings: [] as File[],
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
        const { labFindings, medications, ...rest } = value;
        const payload = {
          ...rest,
          // Omit the key entirely when the patient added no medications, rather than
          // sending medications: []. CaseService.updateCaseDetails treats a null/absent
          // medications field as "don't touch" and an explicit [] as "clear existing" —
          // a stale patient-tab submission with none added must not wipe out medications
          // a healer has since entered on the same still-DRAFT case.
          ...(medications.length > 0 ? { medications } : {}),
          healingCaseId: caseId,
          complementaryTreatments: !!value.complementaryDetails,
          infectiousIllness: !!value.infectiousDetails,
        };
        const formData = new FormData();
        formData.append("data", new Blob([JSON.stringify(payload)], { type: "application/json" }));
        labFindings.forEach((f) => formData.append("labFindings", f));
        await api.patch("/api/v1/cases/self-service", formData, { headers: { "Content-Type": undefined } });
        await api.post("/api/v1/auth/clear-patient-session");
        setIsSubmitted(true);
      } catch (err) {
        console.error(err);
        toast.error(extractDetail(err) || "Failed to submit your details — please try again", {
          duration: 5000,
        });
      } finally {
        setIsSubmitting(false);
      }
    },
  });

  useEffect(() => {
    if (hasFetched.current) return;
    hasFetched.current = true;

    const fetchCase = async () => {
      try {
        const { data } = await api.get("/api/v1/cases/self-service", { params: { caseId } });
        setCaseStatus(data.status);

        const values: PatientFormValues = {
          medicalCondition: data.medicalCondition || "",
          symptoms: data.symptoms || [],
          medications: data.medications || [],
          basicBookPg: data.basicBookPg || "",
          advancedBookPg: data.advancedBookPg || "",
          psychotherapyPg: data.psychotherapyPg || "",
          labFindings: [],
          complementaryDetails: data.complementaryModalities || "",
          infectiousDetails: data.infectiousIllness || "",
          healingDetails: {
            protocolName: data.healingDetails?.protocolName || "",
            bookReference: data.healingDetails?.bookReference || "",
            detailedProtocol: data.healingDetails?.detailedProtocol || "",
            treatmentSteps: data.healingDetails?.treatmentSteps || [],
          },
        };

        Object.entries(values).forEach(([key, value]) => {
          form.setFieldValue(key as keyof PatientFormValues, value as any);
        });
      } catch (err) {
        console.error(err);
      } finally {
        setIsLoading(false);
      }
    };

    fetchCase();
  }, [caseId]);

  // The patient fills the form in before the healer starts (unlocks) the case, or once it is a draft.
  const patientCanEdit = caseStatus === "LOCKED" || caseStatus === "DRAFT";

  if (isLoading) {
    return <div className="p-6 text-center text-text-light">Loading...</div>;
  }

  if ((isSubmitted || !patientCanEdit) && isNewView) {
    return (
      <div className="hlx">
        <section className="surface card" style={{ maxWidth: 520, margin: "40px auto", textAlign: "center", padding: 34 }}>
          <div className="avatar" style={{ width: 60, height: 60, margin: "0 auto 14px", background: "var(--success-soft)", color: "var(--success)" }}>
            <CheckCircle2 style={{ width: 28, height: 28 }} aria-hidden="true" />
          </div>
          <h2 style={{ fontSize: 22, marginBottom: 8 }}>Details submitted</h2>
          <p className="muted">
            Your health details have been submitted. Your healer will review them before the session.
          </p>
        </section>
      </div>
    );
  }

  if (isSubmitted || !patientCanEdit) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] space-y-4 px-4">
        <CheckCircle2 className="w-16 h-16 text-green-500" />
        <h2 className="text-2xl font-bold text-primary-dark">Details Submitted</h2>
        <p className="text-text-light text-center max-w-md">
          Your health details have been submitted. Your healer will review
          them before the session.
        </p>
      </div>
    );
  }

  return (
    <div className={isNewView ? "hlx" : undefined}>
    {isNewView && (
      <div className="page-head" style={{ marginBottom: 16 }}>
        <div>
          <h1>Your health details</h1>
          <p className="muted" style={{ marginTop: 6 }}>Your healer will review these before the session.</p>
        </div>
      </div>
    )}
    <form
      onSubmit={(e) => {
        e.preventDefault();
        form.handleSubmit();
      }}
      className={`bg-card border border-border rounded-xl p-6 shadow-sm space-y-6${isNewView ? " session-form" : ""}`}
    >
      {!isNewView && <h2 className="text-xl font-semibold">Your Health Details</h2>}
      <DynamicFormEngine form={form} schema={patientSessionSchema} />
      <div className="flex justify-end pt-4 border-t border-border">
        <Button
          type="submit"
          disabled={isSubmitting}
          className="px-6 py-2.5 h-auto gap-2"
        >
          {isSubmitting ? "Submitting..." : isNewView ? "Submit my details" : "Submit My Details"}
        </Button>
      </div>
    </form>
    </div>
  );
}
