import { useUiVersion } from "../ui-version";
import { BrandMark } from "../ui-new/components/BrandMark";
import { AuthLogo } from "../components/AuthLogo";
import { useState } from "react";
import { useForm } from "@tanstack/react-form";
import { useSearchParams, useNavigate } from "react-router-dom";
import api from "../lib/axios";
import { DynamicFormEngine } from "../components/DynamicFormEngine";
import { acceptInviteSchema } from "./schemas/AuthSchema";
import { useAuth } from "../context/AuthContext";
import ErrorState from "../components/ErrorState";
import { Button } from "@/components/ui/button";

export default function AcceptInvite() {
  const { isNewView } = useUiVersion();
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
      {!isNewView && (
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="flex justify-center">
          <AuthLogo />
        </div>
        <h2 className="mt-6 text-center text-2xl font-semibold text-text">
          Set Up Your Account
        </h2>
      </div>
      )}

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-card py-8 px-4 shadow sm:rounded-xl sm:px-10 border border-border">
          {isNewView && (
            <div className="mb-6">
              <div className="mb-5 flex items-center gap-2.5">
                <BrandMark size={32} />
                <span className="text-base font-semibold">HealingARK</span>
              </div>
              <h2 className="text-[22px] font-semibold">Set up your account</h2>
            </div>
          )}
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
                <Button
                  type="submit"
                  disabled={!canSubmit || isSubmitting || isFormSubmitting}
                  className="w-full py-2.5 h-auto shadow-sm"
                >
                  {isSubmitting ? "Activating..." : "Activate Account"}
                </Button>
              )}
            />
          </form>
        </div>
      </div>
    </div>
  );
}
