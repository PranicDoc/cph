import { useState } from "react";
import { Link } from "react-router-dom";
import { useForm } from "@tanstack/react-form";
import api from "../lib/axios";
import { extractDetail } from "../lib/utils";
import { DynamicFormEngine } from "../components/DynamicFormEngine";
import { AuthShell } from "../components/AuthShell";
import { signupSchema } from "./schemas/AuthSchema";
import { Button } from "@/components/ui/button";

export default function ForgotPassword() {
  const [error, setError] = useState("");
  const [submitted, setSubmitted] = useState(false);

  const form = useForm({
    defaultValues: { email: "" },
    onSubmit: async ({ value }) => {
      setError("");
      try {
        await api.post("/api/v1/auth/forgot-password", { email: value.email });
        setSubmitted(true);
      } catch (err: unknown) {
        setError(extractDetail(err) ?? "Something went wrong. Please try again.");
      }
    },
  });

  return (
    <AuthShell title="Forgot your password?">
      {submitted ? (
        <div className="text-center space-y-4">
          <h3 className="text-lg font-medium text-text">Check your email</h3>
          <p className="text-sm text-text-light">
            If that address belongs to an account, we've sent a link to reset your password. It
            works once and expires in 30 minutes.
          </p>
          <Link to="/login" className="inline-block text-sm font-semibold text-primary">
            Back to sign in
          </Link>
        </div>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            e.stopPropagation();
            form.handleSubmit();
          }}
          className="space-y-6"
        >
          <p className="text-sm text-text-light">
            Enter the email you registered with and we'll send you a link to choose a new password.
          </p>
          {/* Same single email field as the sign-up page. */}
          <DynamicFormEngine form={form} schema={signupSchema} />

          {error && (
            <div className="text-danger text-sm bg-danger/10 p-3 rounded-lg border border-danger/20">
              {error}
            </div>
          )}

          <form.Subscribe
            selector={(state) => [state.canSubmit, state.isSubmitting]}
            children={([canSubmit, isSubmitting]) => (
              <Button
                type="submit"
                disabled={!canSubmit || isSubmitting}
                className="w-full py-2.5 h-auto shadow-sm"
              >
                {isSubmitting ? "Sending..." : "Send reset link"}
              </Button>
            )}
          />
          <p className="text-center text-sm">
            <Link to="/login" className="font-semibold text-primary">
              Back to sign in
            </Link>
          </p>
        </form>
      )}
    </AuthShell>
  );
}
