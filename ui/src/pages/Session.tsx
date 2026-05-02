import { useEffect, useRef, useState } from "react";
import { useForm } from "@tanstack/react-form";
import { useParams } from "react-router-dom";
import { CheckCircle2 } from "lucide-react";
import api from "../lib/axios";
import { DynamicFormEngine } from "../components/DynamicFormEngine";
import { sessionSchema } from "./sessionSchema";
import { SessionHeader } from "./SessionHeader";
import { ChakraScan } from "../components/ChakraScan";
import { TreatmentSteps } from "../components/TreatmentSteps";
import { HealingSession } from "../interfaces";

export default function Session() {
  const { healingCaseId } = useParams();
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [sessionData, setSessionData] = useState<HealingSession>(null);

  const sessionForm = useForm({
    defaultValues: {
      medicalCondition: "",
      symptoms: [] as string[],
      protocols: [] as string[],
      medications: [] as string[],
      basicPageNumber: "",
      advancedPageNumber: "",
      psychotherapyPageNumber: "",
      labFindings: "",
      complementaryDetails: "",
      infectiousDetails: "",
      healerNotes: "",
      patientFeedback: "",
      treatmentPlan: {
        protocolName: "",
        bookReference: "",
        steps: [],
      },
    },
    onSubmit: async ({ value }) => {
      setIsSubmitting(true);
      try {
        const payload = {
          ...value,
          healingCaseId: healingCaseId,
          complementaryTreatments: !!value.complementaryDetails,
          infectiousIllness: !!value.infectiousDetails,
        };
        await api.patch("/api/v1/cases", payload);
      } catch (err) {
        console.error(err);
      } finally {
        setIsSubmitting(false);
      }
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
        sessionForm.reset({
          medicalCondition: data.medicalCondition || "",
          symptoms: data.symptoms || [],
          protocols: data.protocols || [],
          medications: data.medications || [],
          basicPageNumber: data.pageNumbers?.basic || "",
          advancedPageNumber: data.pageNumbers?.advanced || "",
          psychotherapyPageNumber: data.pageNumbers?.psychotherapy || "",
          labFindings: data.labFindings || "",
          complementaryDetails: data.complementaryModalities || "",
          infectiousDetails: data.infectiousIllness || "",
          healerNotes: data.healerNotes || "",
          patientFeedback: data.patientFeedback || "",
          treatmentPlan: {
            protocolName: "",
            bookReference: "",
            steps: [],
          },
        });
      } catch (error) {
        console.error("Error fetching healing session:", error);
      }
    };

    fetchData();
  }, [healingCaseId, sessionForm.reset]);

  return (
    <div className="space-y-6">
      <SessionHeader healingSession={sessionData} />

      <form
        onSubmit={(e) => {
          e.preventDefault();
          sessionForm.handleSubmit();
        }}
        className="bg-card border border-border rounded-xl p-6 shadow-sm space-y-6"
      >
        <DynamicFormEngine form={sessionForm} schema={sessionSchema} />

        {/* <sessionForm.Field name="chakras">
          {(field) => (
            <ChakraScan
              value={field.state.value}
              onChange={field.handleChange}
            />
          )}
        </sessionForm.Field> */}

        <sessionForm.Field name="treatmentPlan">
          {(field) => (
            <TreatmentSteps
              value={field.state.value}
              onChange={field.handleChange}
            />
          )}
        </sessionForm.Field>

        <div className="flex flex-col sm:flex-row justify-between items-center gap-4 pt-4 border-t border-border">
          <button
            type="button"
            className="w-full sm:w-auto px-6 py-2.5 border-2 border-border text-text font-medium rounded-lg hover:bg-background transition-colors"
          >
            💾 Save as Draft
          </button>
          <div className="flex w-full sm:w-auto gap-3">
            <button
              type="button"
              className="flex-1 sm:flex-none px-6 py-2.5 border-2 border-border text-text font-medium rounded-lg hover:bg-background transition-colors"
            >
              Post-Scan ➡️
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="flex-1 sm:flex-none px-6 py-2.5 bg-primary text-white font-medium rounded-lg hover:bg-primary-dark transition-colors flex items-center justify-center gap-2"
            >
              <CheckCircle2 className="w-5 h-5" /> Complete Session
            </button>
          </div>
        </div>
      </form>
    </div>
  );
}
