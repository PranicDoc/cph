import { useState } from "react";
import { useForm } from "@tanstack/react-form";
import { Link, useNavigate } from "react-router-dom";
import { Activity } from "lucide-react";
import api from "../lib/axios";
import { DynamicFormEngine } from "../components/DynamicFormEngine";
import { registerSchema } from "./schemas/AuthSchema";
import { useAuth } from "../context/AuthContext";
import { PlanPicker } from "../components/PlanPicker";

export default function Register() {
  const navigate = useNavigate();
  const { login, logout } = useAuth();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [step, setStep] = useState(1);
  const [healerId, setHealerId] = useState("");

  const registerForm = useForm({
    defaultValues: {
      fullName: "",
      email: "",
      password: "",
      phone: "",
      clinicCode: "",
    },
    onSubmit: async ({ value }) => {
      setIsSubmitting(true);
      setError("");
      try {
        const res = await api.post("/api/v1/auth/register", {
          fullName: value.fullName,
          email: value.email,
          password: value.password,
          phone: value.phone || undefined,
          role: "HEALER",
          clinicCode: value.clinicCode,
        });
        login(res.data);
        setHealerId(res.data.id);
        setStep(2);
      } catch (err: any) {
        console.error("Registration failed:", err);
        setError(
          err.response?.data?.message ||
            "Registration failed. Please try again."
        );
      } finally {
        setIsSubmitting(false);
      }
    },
  });

  const handlePlanComplete = () => {
    try {
      logout();
    } catch {
      // ignore logout errors — navigate regardless
    }
    navigate("/login");
  };

  return (
    <div className="min-h-screen bg-background flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className={`sm:mx-auto sm:w-full ${step === 2 ? "sm:max-w-2xl" : "sm:max-w-md"}`}>
        <div className="flex justify-center">
          <div className="w-12 h-12 bg-primary rounded-xl flex items-center justify-center shadow-lg shadow-primary/20">
            <Activity className="w-8 h-8 text-white" />
          </div>
        </div>
        <h2 className="mt-6 text-center text-3xl font-extrabold text-text">
          {step === 1 ? "Create a new account" : "Choose Your Plan"}
        </h2>
        {step === 1 && (
          <p className="mt-2 text-center text-sm text-text-light">
            Or{" "}
            <Link
              to="/login"
              className="font-medium text-primary hover:text-primary-dark"
            >
              sign in to your existing account
            </Link>
          </p>
        )}
      </div>

      <div className={`mt-8 sm:mx-auto sm:w-full ${step === 2 ? "sm:max-w-2xl" : "sm:max-w-md"}`}>
        <div className="bg-card py-8 px-4 shadow sm:rounded-xl sm:px-10 border border-border">
          {step === 1 && (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                e.stopPropagation();
                registerForm.handleSubmit();
              }}
              className="space-y-6"
            >
              <DynamicFormEngine form={registerForm} schema={registerSchema} />

              {error && (
                <div className="text-danger text-sm bg-danger/10 p-3 rounded-lg border border-danger/20">
                  {error}
                </div>
              )}

              <registerForm.Subscribe
                selector={(state) => [state.canSubmit, state.isSubmitting]}
                children={([canSubmit, isFormSubmitting]) => (
                  <button
                    type="submit"
                    disabled={!canSubmit || isSubmitting || isFormSubmitting}
                    className="w-full flex justify-center py-2.5 px-4 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-primary hover:bg-primary-dark focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary disabled:opacity-50 transition-colors"
                  >
                    {isSubmitting ? "Creating account..." : "Register"}
                  </button>
                )}
              />
            </form>
          )}
          {step === 2 && (
            <PlanPicker
              ownerId={healerId}
              ownerType="HEALER"
              onComplete={handlePlanComplete}
            />
          )}
        </div>
      </div>
    </div>
  );
}
