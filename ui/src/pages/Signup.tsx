import { AuthLogo } from "../components/AuthLogo";
import { useState } from "react";
import { useForm } from "@tanstack/react-form";
import api from "../lib/axios";
import { DynamicFormEngine } from "../components/DynamicFormEngine";
import { signupSchema } from "./schemas/AuthSchema";
import { Button } from "@/components/ui/button";

export default function Signup() {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [submitted, setSubmitted] = useState(false);

  const signupForm = useForm({
    defaultValues: {
      email: "",
    },
    onSubmit: async ({ value }) => {
      setIsSubmitting(true);
      setError("");
      try {
        await api.post("/api/v1/auth/signup", { email: value.email });
        setSubmitted(true);
      } catch (err: any) {
        console.error("Signup request failed:", err);
        setError(
          err.response?.data?.detail || "Something went wrong. Please try again."
        );
      } finally {
        setIsSubmitting(false);
      }
    },
  });

  return (
    <div className="min-h-screen bg-background flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="flex justify-center">
          <AuthLogo />
        </div>
        <h2 className="mt-6 text-center text-2xl font-semibold text-text">
          Create Your Account
        </h2>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-card py-8 px-4 shadow sm:rounded-xl sm:px-10 border border-border">
          {submitted ? (
            <div className="text-center space-y-2">
              <h3 className="text-lg font-medium text-text">Check your email</h3>
              <p className="text-sm text-text-light">
                Check your email — if that address is eligible, we've sent a
                verification link to continue.
              </p>
            </div>
          ) : (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                e.stopPropagation();
                signupForm.handleSubmit();
              }}
              className="space-y-6"
            >
              <DynamicFormEngine form={signupForm} schema={signupSchema} />

              {error && (
                <div className="text-danger text-sm bg-danger/10 p-3 rounded-lg border border-danger/20">
                  {error}
                </div>
              )}

              <signupForm.Subscribe
                selector={(state) => [state.canSubmit, state.isSubmitting]}
                children={([canSubmit, isFormSubmitting]) => (
                  <Button
                    type="submit"
                    disabled={!canSubmit || isSubmitting || isFormSubmitting}
                    className="w-full py-2.5 h-auto shadow-sm"
                  >
                    {isSubmitting ? "Sending..." : "Send verification link"}
                  </Button>
                )}
              />
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
