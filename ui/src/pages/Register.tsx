import { useState } from "react";
import { useForm } from "@tanstack/react-form";
import { Link, useNavigate } from "react-router-dom";
import { Activity } from "lucide-react";
import api from "../lib/axios";
import { DynamicFormEngine } from "../components/DynamicFormEngine";
import { registerSchema } from "./schemas/AuthSchema";

export default function Register() {
  const navigate = useNavigate();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");

  const registerForm = useForm({
    defaultValues: {
      email: "",
      password: "",
    },
    onSubmit: async ({ value }) => {
      setIsSubmitting(true);
      setError("");
      try {
        // Assuming the endpoint for registration is /api/v1/auth/register
        await api.post("/api/v1/auth/register", value);
        // After successful registration, you might want to log them in automatically
        // or redirect to login page
        navigate("/login");
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

  return (
    <div className="min-h-screen bg-background flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="flex justify-center">
          <div className="w-12 h-12 bg-primary rounded-xl flex items-center justify-center shadow-lg shadow-primary/20">
            <Activity className="w-8 h-8 text-white" />
          </div>
        </div>
        <h2 className="mt-6 text-center text-3xl font-extrabold text-text">
          Create a new account
        </h2>
        <p className="mt-2 text-center text-sm text-text-light">
          Or{" "}
          <Link
            to="/login"
            className="font-medium text-primary hover:text-primary-dark"
          >
            sign in to your existing account
          </Link>
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-card py-8 px-4 shadow sm:rounded-xl sm:px-10 border border-border">
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
        </div>
      </div>
    </div>
  );
}
