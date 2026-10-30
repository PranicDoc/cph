import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useForm } from "@tanstack/react-form";
import axios from "axios";
import api from "../lib/axios";
import { extractDetail } from "../lib/utils";
import { DynamicFormEngine } from "../components/DynamicFormEngine";
import { AuthShell } from "../components/AuthShell";
import ErrorState from "../components/ErrorState";
import { resetPasswordSchema } from "./schemas/AuthSchema";
import { Button } from "@/components/ui/button";

/** Opened from the forgot-password email; the token in the link is the only credential. */
export default function ResetPassword() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");

  const [error, setError] = useState("");
  const [linkInvalid, setLinkInvalid] = useState(false);
  const [done, setDone] = useState(false);

  const form = useForm({
    defaultValues: { newPassword: "", confirmPassword: "" },
    onSubmit: async ({ value }) => {
      setError("");
      try {
        await api.post("/api/v1/auth/reset-password", {
          token,
          newPassword: value.newPassword,
          confirmPassword: value.confirmPassword,
        });
        setDone(true);
      } catch (err: unknown) {
        if (axios.isAxiosError(err) && err.response?.status === 401) {
          setLinkInvalid(true);
        } else {
          setError(extractDetail(err) ?? "Could not reset the password. Please try again.");
        }
      }
    },
  });

  if (!token || linkInvalid) {
    return (
      <ErrorState
        variant="expired"
        title="Reset link expired"
        message="This password reset link is invalid, expired, or already used. Request a new one to continue."
        actions={
          <Button asChild>
            <Link to="/forgot-password">Request a new link</Link>
          </Button>
        }
      />
    );
  }

  return (
    <AuthShell title="Choose a new password">
      {done ? (
        <div className="text-center space-y-4">
          <h3 className="text-lg font-medium text-text">Password updated</h3>
          <p className="text-sm text-text-light">You can now sign in with your new password.</p>
          <Button className="w-full py-2.5 h-auto" onClick={() => navigate("/login")}>
            Go to sign in
          </Button>
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
          <DynamicFormEngine form={form} schema={resetPasswordSchema} />

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
                {isSubmitting ? "Saving..." : "Update password"}
              </Button>
            )}
          />
        </form>
      )}
    </AuthShell>
  );
}
