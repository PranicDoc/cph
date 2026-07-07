import { useState } from "react";
import { useForm } from "@tanstack/react-form";
import { useSearchParams, useNavigate } from "react-router-dom";
import { Activity } from "lucide-react";
import api from "../lib/axios";
import { DynamicFormEngine } from "../components/DynamicFormEngine";
import { acceptInviteSchema } from "./schemas/AuthSchema";
import { useAuth } from "../context/AuthContext";
import ErrorState from "../components/ErrorState";

export default function AcceptInvite() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [tokenInvalid, setTokenInvalid] = useState(false);

  const acceptInviteForm = useForm({
    defaultValues: {
      fullName: "",
      password: "",
      phone: "",
    },
    onSubmit: async ({ value }) => {
      setIsSubmitting(true);
      setError("");
      try {
        const res = await api.post(
          "/api/v1/auth/accept-invite",
          {
            fullName: value.fullName,
            password: value.password,
            phone: value.phone,
          },
          { params: { token } }
        );
        login(res.data);
        navigate("/");
      } catch (err: any) {
        console.error("Accept invite failed:", err);
        if (err.response?.status === 401) {
          setTokenInvalid(true);
        } else {
          setError(
            err.response?.data?.detail || "Activation failed. Please try again."
          );
        }
      } finally {
        setIsSubmitting(false);
      }
    },
  });

  if (!token || tokenInvalid) {
    return <ErrorState variant="inviteExpired" />;
  }

  return (
    <div className="min-h-screen bg-background flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="flex justify-center">
          <div className="w-12 h-12 bg-primary rounded-xl flex items-center justify-center shadow-lg shadow-primary/20">
            <Activity className="w-8 h-8 text-white" />
          </div>
        </div>
        <h2 className="mt-6 text-center text-2xl font-semibold text-text">
          Set Up Your Account
        </h2>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-card py-8 px-4 shadow sm:rounded-xl sm:px-10 border border-border">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              e.stopPropagation();
              acceptInviteForm.handleSubmit();
            }}
            className="space-y-6"
          >
            <DynamicFormEngine form={acceptInviteForm} schema={acceptInviteSchema} />

            {error && (
              <div className="text-danger text-sm bg-danger/10 p-3 rounded-lg border border-danger/20">
                {error}
              </div>
            )}

            <acceptInviteForm.Subscribe
              selector={(state) => [state.canSubmit, state.isSubmitting]}
              children={([canSubmit, isFormSubmitting]) => (
                <button
                  type="submit"
                  disabled={!canSubmit || isSubmitting || isFormSubmitting}
                  className="w-full flex justify-center py-2.5 px-4 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-primary hover:bg-primary-dark focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary disabled:opacity-50 transition-colors"
                >
                  {isSubmitting ? "Activating..." : "Activate Account"}
                </button>
              )}
            />
          </form>
        </div>
      </div>
    </div>
  );
}
