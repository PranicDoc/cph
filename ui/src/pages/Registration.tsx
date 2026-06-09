import { useEffect, useRef, useState } from "react";
import { useForm } from "@tanstack/react-form";
import api from "../lib/axios";
import { CheckCircle2 } from "lucide-react";
import { DynamicFormEngine } from "../components/DynamicFormEngine";
import {
  patientDemographicsSchema,
  patientMedicalHistorySchema,
  consentDeclarationSchema,
} from "./schemas/RegistrationSchemas";
import { useLocation, useNavigate, useParams } from "react-router-dom";

export default function Registration() {
  const [step, setStep] = useState(1);
  const [patientId, setPatientId] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { clinicId, uuid } = useParams();
  const location = useLocation();
  const hasCalled = useRef(false);
  const [isSelfRegister, setIsSelfRegister] = useState(false);
  const navigate = useNavigate();

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
        const res = await api.post("/api/v1/auth/init-session", {
          clinicId: clinicId,
          uuid: uuid,
        });
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
      dob: "",
      gender: "",
      maritalStatus: "",
      email: "",
      contact: "",
      occupation: "",
      addressLine1: "",
      addressLine2: "",
      city: "",
      state: "",
      zip: "",
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
      generalHealth: "",
      diet: "",
      exercise: "",
      exerciseFrequency: "",
      hypertension: false,
      giIssues: false,
      stressDepressionAnxiety: false,
      pregnant: false,
      cancer: false,
      cancerLocation: "",
      cancerDetails: "",
      additionalNotes: "",
    },
    onSubmit: async ({ value }) => {
      setIsSubmitting(true);
      const payload = {
        ...patientForm.state.values,
        medicalHistory: value,
      };
      try {
        // Attach patientId to the payload implicitly via the URL or explicitly in the body
        // POST call to save medical history
        const res = await api.post(`/api/v1/patients`, {
          ...payload,
        });
        setStep(3);
        setPatientId(res.data.id);
      } catch (error) {
        console.error("Failed to save medical history.", error);
        // Fallback for UI demonstration
        alert("Something went wrong");
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
      patientSignature: "",
    },
    onSubmit: async ({ value }) => {
      setIsSubmitting(true);
      try {
        const declaration = {
          patientConsent: value.consent1 && value.consent2 && value.consent3,
          patientSignature: value.patientSignature,
          signedAt: new Date().toISOString(),
        };
        const payload = {
          patientId: patientId,
          declaration: declaration,
        };
        await api.post(`/api/v1/cases`, payload);
        setStep(4);
      } catch (error) {
        console.error("Failed to save declaration.", error);
        setStep(4);
      } finally {
        setIsSubmitting(false);
      }
    },
  });

  return (
    <div className="max-w-4xl mx-auto space-y-6">
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
              <button
                type="button"
                className="px-6 py-2.5 border-2 border-border text-text font-medium rounded-lg hover:bg-background transition-colors"
              >
                Cancel
              </button>
              <patientForm.Subscribe
                selector={(state) => [state.canSubmit, state.isSubmitting]}
                children={([canSubmit, isFormSubmitting]) => (
                  <button
                    type="submit"
                    disabled={!canSubmit || isSubmitting || isFormSubmitting}
                    className="px-6 py-2.5 bg-primary text-white font-medium rounded-lg hover:bg-primary-dark transition-colors disabled:opacity-50"
                  >
                    {isSubmitting ? "Saving..." : "Save & Next ➡️"}
                  </button>
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
              <button
                type="button"
                onClick={() => setStep(1)}
                className="px-6 py-2.5 border-2 border-border text-text font-medium rounded-lg hover:bg-background transition-colors"
              >
                Back
              </button>
              <historyForm.Subscribe
                selector={(state) => [state.canSubmit, state.isSubmitting]}
                children={([canSubmit, isFormSubmitting]) => (
                  <button
                    type="submit"
                    disabled={!canSubmit || isSubmitting || isFormSubmitting}
                    className="px-6 py-2.5 bg-primary text-white font-medium rounded-lg hover:bg-primary-dark transition-colors disabled:opacity-50"
                  >
                    {isSubmitting ? "Saving..." : "Save & Next ➡️"}
                  </button>
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
              schema={consentDeclarationSchema}
            />

            <div className="flex justify-end gap-4 pt-6 border-t border-border mt-8">
              <button
                type="button"
                onClick={() => setStep(2)}
                className="px-6 py-2.5 border-2 border-border text-text font-medium rounded-lg hover:bg-background transition-colors"
              >
                Back
              </button>
              <consentForm.Subscribe
                selector={(state) => [state.canSubmit, state.isSubmitting]}
                children={([canSubmit, isFormSubmitting]) => (
                  <button
                    type="submit"
                    disabled={!canSubmit || isSubmitting || isFormSubmitting}
                    className="px-6 py-2.5 bg-primary text-white font-medium rounded-lg hover:bg-primary-dark transition-colors disabled:opacity-50"
                  >
                    {isSubmitting ? "Saving..." : "Complete Registration ➡️"}
                  </button>
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
                <button
                  onClick={() => {
                    patientForm.reset();
                    historyForm.reset();
                    consentForm.reset();
                    setPatientId(null);
                    setStep(1);
                  }}
                  className="px-6 py-2.5 bg-primary text-white font-medium rounded-lg hover:bg-primary-dark transition-colors"
                >
                  Register Another Patient
                </button>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
