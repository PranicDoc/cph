import { useEffect, useRef, useState } from "react";
import { useForm } from "@tanstack/react-form";
import { useQueryClient } from "@tanstack/react-query";
import api from "../lib/axios";
import { toast } from "sonner";
import { extractDetail } from "../lib/utils";
import { CheckCircle2 } from "lucide-react";
import { DynamicFormEngine } from "../components/DynamicFormEngine";
import {
  patientDemographicsSchema,
  patientMedicalHistorySchema,
  consentDeclarationSchema,
} from "./schemas/RegistrationSchemas";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { usePatient } from "../context/PatientContext";
import { Button } from "@/components/ui/button";
import { serializeExercise } from "../lib/exercise";
import { buildDeclaration } from "../lib/declaration";
import { useUiVersion } from "../ui-version";

export default function Registration() {
  const { isNewView } = useUiVersion();
  const [step, setStep] = useState(1);
  const [patientId, setPatientId] = useState<string | null>(null);
  const [patientFullName, setPatientFullName] = useState<string>("");
  const [pendingHistory, setPendingHistory] = useState<Record<
    string,
    unknown
  > | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { healerId, uuid } = useParams();
  const { selectPatient } = usePatient();
  const location = useLocation();
  const hasCalled = useRef(false);
  const [isSelfRegister, setIsSelfRegister] = useState(false);
  const navigate = useNavigate();
  const qc = useQueryClient();

  useEffect(() => {
    if (!location.pathname.startsWith("/self-register")) {
      setIsSelfRegister(false);
      return;
    }
    setIsSelfRegister(true);
    if (!uuid) return;
    if (hasCalled.current) return;
    hasCalled.current = true;

    const initSession = async () => {
      try {
        // NOTE: backend InitSessionRequest is still (uuid, clinicId)-shaped until 26-11 lands
        // (same wave, 26-11 owns the DTO rename) — sending `healerId` here matches 26-11's
        // target contract but the self-register init-session call will not fully complete
        // end-to-end until 26-11 executes.
        const res = await api.post("/api/v1/auth/init-session", {
          healerId: healerId,
          uuid: uuid,
        });
        sessionStorage.setItem("role", res.data?.data);
        window.history.replaceState({}, "", "/self-register");
      } catch (err) {
        navigate("/unauthorized");
      }
    };

    initSession();
  }, [uuid, location.pathname]);

  // --- Step 1: Patient Demographics Form ---
  const patientForm = useForm({
    defaultValues: {
      firstName: "",
      lastName: "",
      dob: null,
      gender: null,
      maritalStatus: null,
      email: null,
      contact: null,
      occupation: null,
      addressLine1: null,
      addressLine2: null,
      city: null,
      state: null,
      zip: null,
    },
    onSubmit: async () => {
      setIsSubmitting(true);
      try {
        setStep(2);
      } catch (error) {
        console.error(
          "Failed to save patient. Using mock ID for UI flow.",
          error
        );
        // Fallback for UI demonstration if backend is not ready
        setPatientId("mock-uuid-1234-5678");
        setStep(2);
      } finally {
        setIsSubmitting(false);
      }
    },
  });

  // --- Step 2: Patient Medical History Form ---
  const historyForm = useForm({
    defaultValues: {
      generalHealth: null,
      diet: null,
      exercise: [] as string[],
      exerciseFrequency: null,
      hypertension: false,
      giIssues: false,
      stressDepressionAnxiety: false,
      pregnant: false,
      cancer: false,
      cancerLocation: null,
      cancerDetails: null,
      additionalNotes: null,
    },
    onSubmit: async ({ value }) => {
      setIsSubmitting(true);
      try {
        if (isSelfRegister) {
          const payload = {
            ...patientForm.state.values,
            firstName: patientForm.state.values.firstName.trim(),
            lastName: patientForm.state.values.lastName.trim(),
            medicalHistory: { ...value, exercise: serializeExercise(value.exercise) },
          };
          const res = await api.post("/api/v1/patients/self-register", payload);
          setPatientId(res.data.id);
          toast.success("Patient registered");
        } else {
          setPendingHistory({ ...value, exercise: serializeExercise(value.exercise) });
        }
        setPatientFullName(
          patientForm.state.values.firstName.trim() +
            " " +
            patientForm.state.values.lastName.trim()
        );
        setStep(3);
      } catch (error) {
        toast.error(
          extractDetail(error) || "Registration failed — please try again",
          { duration: 5000 }
        );
      } finally {
        setIsSubmitting(false);
      }
    },
  });

  // --- Step 3: Consent Declaration Form ---
  const consentForm = useForm({
    defaultValues: {
      consent1: false,
      consent2: false,
      consent3: false,
      healerConsentAttestation: false,
      patientSignature: "",
    },
    onSubmit: async ({ value }) => {
      setIsSubmitting(true);
      try {
        const declaration = buildDeclaration(value, !isSelfRegister);
        if (isSelfRegister) {
          const res = await api.post(`/api/v1/cases`, {
            patientId,
            declaration,
          });
          selectPatient(patientId);
          const caseId = res.data?.id;
          sessionStorage.setItem("caseId", caseId);
          toast.success("Registration complete");
          navigate(`/patient-health/${caseId}`);
        } else {
          await api.post("/api/v1/patients/register", {
            patient: {
              ...patientForm.state.values,
              medicalHistory: pendingHistory,
            },
            declaration,
          });
          toast.success("Registration complete");
          await qc.invalidateQueries({ queryKey: ["patients"] });
          navigate("/patients");
        }
      } catch (error) {
        if (isSelfRegister && (error as { response?: unknown })?.response) {
          // The server discards the half-registered patient when the declaration fails, so the
          // saved patientId is gone: start the form again rather than retrying against it.
          setPatientId(null);
          setStep(1);
          toast.error(
            extractDetail(error) ||
              "We couldn't complete your registration. Please fill in your details again.",
            { duration: 6000 }
          );
        } else {
          toast.error(
            extractDetail(error) ||
              "Failed to save declaration — please try again",
            { duration: 5000 }
          );
        }
      } finally {
        setIsSubmitting(false);
      }
    },
  });

  // A healer registering the patient: the signature is the patient's name, filled in automatically.
  useEffect(() => {
    if (step === 3 && !isSelfRegister && patientFullName) {
      consentForm.setFieldValue("patientSignature", patientFullName);
    }
  }, [step, isSelfRegister, patientFullName]);

  const stepTitle =
    step === 1 ? "Personal details" : step === 2 ? "Medical history" : step === 3 ? "Consent" : "Complete";

  return (
    <div className={isNewView ? "hlx" : "max-w-4xl mx-auto space-y-6"}>
      {isNewView ? (
        <>
          <div className="page-head">
            <div>
              <h1>New patient registration</h1>
              <p className="muted" style={{ marginTop: 6 }}>
                Step {step} of 4: {stepTitle.toLowerCase()}.
              </p>
            </div>
          </div>
          <div className="surface" style={{ padding: "16px 20px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10 }}>
              <strong>Step {step} of 4</strong>
              <span className="small muted">{stepTitle}</span>
            </div>
            <div style={{ display: "flex", gap: 8 }}>
              {[1, 2, 3, 4].map((n) => (
                <div key={n} style={{ height: 6, flex: 1, borderRadius: 99, background: step >= n ? "var(--primary)" : "var(--hair-strong)" }} />
              ))}
            </div>
          </div>
        </>
      ) : (
        <>
      <div className="bg-primary text-white p-4 rounded-xl flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <h1 className="text-xl font-bold flex items-center gap-2">
          👤 New Patient Registration
        </h1>
        <span className="text-sm font-medium bg-white/20 px-3 py-1 rounded-lg">
          Step {step} of 4 —{" "}
          {step === 1
            ? "Personal Details"
            : step === 2
            ? "Medical History"
            : step === 3
            ? "Consent"
            : "Complete"}
        </span>
      </div>

      {/* Progress Bar */}
      <div className="flex gap-2">
        <div
          className={`h-1.5 flex-1 rounded-full ${
            step >= 1 ? "bg-primary" : "bg-border"
          }`}
        ></div>
        <div
          className={`h-1.5 flex-1 rounded-full ${
            step >= 2 ? "bg-primary" : "bg-border"
          }`}
        ></div>
        <div
          className={`h-1.5 flex-1 rounded-full ${
            step >= 3 ? "bg-primary" : "bg-border"
          }`}
        ></div>
        <div
          className={`h-1.5 flex-1 rounded-full ${
            step >= 4 ? "bg-primary" : "bg-border"
          }`}
        ></div>
      </div>

        </>
      )}

      <div className="bg-card border border-border rounded-xl p-6 shadow-sm space-y-6">
        {step === 1 && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              e.stopPropagation();
              patientForm.handleSubmit();
            }}
            className="space-y-6"
          >
            <DynamicFormEngine
              form={patientForm}
              schema={patientDemographicsSchema}
            />

            <div className="flex justify-end gap-4 pt-6 border-t border-border mt-8">
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  if (!isSelfRegister) navigate("/patients");
                }}
                className="px-6 py-2.5 h-auto border-2 text-text"
              >
                Cancel
              </Button>
              <patientForm.Subscribe
                selector={(state) => [state.canSubmit, state.isSubmitting]}
                children={([canSubmit, isFormSubmitting]) => (
                  <Button
                    type="submit"
                    disabled={!canSubmit || isSubmitting || isFormSubmitting}
                    className="px-6 py-2.5 h-auto"
                  >
                    {isSubmitting ? "Saving..." : "Save & Next"}
                  </Button>
                )}
              />
            </div>
          </form>
        )}

        {step === 2 && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              e.stopPropagation();
              historyForm.handleSubmit();
            }}
            className="space-y-6"
          >
            <DynamicFormEngine
              form={historyForm}
              schema={patientMedicalHistorySchema}
            />

            <div className="flex justify-end gap-4 pt-6 border-t border-border mt-8">
              <Button
                type="button"
                variant="outline"
                onClick={() => setStep(1)}
                className="px-6 py-2.5 h-auto border-2 text-text"
              >
                Back
              </Button>
              <historyForm.Subscribe
                selector={(state) => [state.canSubmit, state.isSubmitting]}
                children={([canSubmit, isFormSubmitting]) => (
                  <Button
                    type="submit"
                    disabled={!canSubmit || isSubmitting || isFormSubmitting}
                    className="px-6 py-2.5 h-auto"
                  >
                    {isSubmitting ? "Saving..." : "Save & Next"}
                  </Button>
                )}
              />
            </div>
          </form>
        )}

        {step === 3 && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              e.stopPropagation();
              consentForm.handleSubmit();
            }}
            className="space-y-6"
          >
            <DynamicFormEngine
              form={consentForm}
              schema={consentDeclarationSchema(patientFullName, { onBehalf: !isSelfRegister })}
            />

            <div className="flex justify-end gap-4 pt-6 border-t border-border mt-8">
              <Button
                type="button"
                variant="outline"
                onClick={() => setStep(2)}
                className="px-6 py-2.5 h-auto border-2 text-text"
              >
                Back
              </Button>
              <consentForm.Subscribe
                selector={(state) => [state.canSubmit, state.isSubmitting]}
                children={([canSubmit, isFormSubmitting]) => (
                  <Button
                    type="submit"
                    disabled={!canSubmit || isSubmitting || isFormSubmitting}
                    className="px-6 py-2.5 h-auto"
                  >
                    {isSubmitting ? "Saving..." : "Complete Registration"}
                  </Button>
                )}
              />
            </div>
          </form>
        )}

        {step === 4 && (
          <div className="text-center py-12 space-y-4">
            <div className="w-20 h-20 bg-success/10 rounded-full flex items-center justify-center mx-auto">
              <CheckCircle2 className="w-10 h-10 text-success" />
            </div>
            <h2 className="text-2xl font-bold text-primary-dark">
              Registration Complete!
            </h2>
            <p className="text-text-light max-w-md mx-auto">
              {isSelfRegister
                ? "Thank you for registering with us. You can now proceed to the session."
                : "The patient demographics, medical history, and consent declaration have been successfully saved."}
            </p>
            <div className="pt-6">
              {!isSelfRegister && (
                <Button
                  onClick={() => {
                    patientForm.reset();
                    historyForm.reset();
                    consentForm.reset();
                    setPatientId(null);
                    setStep(1);
                  }}
                  className="px-6 py-2.5 h-auto"
                >
                  Register Another Patient
                </Button>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
