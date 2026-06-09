import { useState } from "react";
import { useForm } from "@tanstack/react-form";
import { Link, useNavigate, Navigate } from "react-router-dom";
import { Activity } from "lucide-react";
import api from "../lib/axios";
import { DynamicFormEngine } from "../components/DynamicFormEngine";
import { loginSchema } from "./schemas/AuthSchema";
import { useAuth } from "../context/AuthContext";
import { useQueryClient } from "@tanstack/react-query";

export default function Login() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { login, isAuthenticated } = useAuth();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");

  const savedEmail = localStorage.getItem("rememberedEmail") || "";

  const loginForm = useForm({
    defaultValues: {
      email: savedEmail,
      password: "",
      rememberMe: !!savedEmail,
    },
    onSubmit: async ({ value }) => {
      setIsSubmitting(true);
      setError("");
      try {
        // Send only email and password to the API
        const res = await api.post("/api/v1/auth/login", {
          email: value.email,
          password: value.password,
        });

        // Handle Remember Me
        if (value.rememberMe) {
          localStorage.setItem("rememberedEmail", value.email);
        } else {
          localStorage.removeItem("rememberedEmail");
        }

        if (res.status === 200) {
          login(res.data);
          queryClient.clear();
          navigate("/");
        } else {
          setError("Please enter correct username and password");
        }
      } catch (err: any) {
        console.error("Login failed:", err);
        setError(err.message);
      } finally {
        setIsSubmitting(false);
      }
    },
  });

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return (
    <div className="min-h-screen bg-background flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="flex justify-center">
          <div className="w-12 h-12 bg-primary rounded-xl flex items-center justify-center shadow-lg shadow-primary/20">
            <Activity className="w-8 h-8 text-white" />
          </div>
        </div>
        <h2 className="mt-6 text-center text-3xl font-extrabold text-text">
          Sign in to your account
        </h2>
        <p className="mt-2 text-center text-sm text-text-light">
          Or{" "}
          <Link
            to="/register"
            className="font-medium text-primary hover:text-primary-dark"
          >
            create a new account
          </Link>
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-card py-8 px-4 shadow sm:rounded-xl sm:px-10 border border-border">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              e.stopPropagation();
              loginForm.handleSubmit();
            }}
            className="space-y-6"
          >
            <DynamicFormEngine form={loginForm} schema={loginSchema} />

            {error && (
              <div className="text-danger text-sm bg-danger/10 p-3 rounded-lg border border-danger/20">
                {error}
              </div>
            )}

            <loginForm.Subscribe
              selector={(state) => [state.canSubmit, state.isSubmitting]}
              children={([canSubmit, isFormSubmitting]) => (
                <button
                  type="submit"
                  disabled={!canSubmit || isSubmitting || isFormSubmitting}
                  className="w-full flex justify-center py-2.5 px-4 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-primary hover:bg-primary-dark focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary disabled:opacity-50 transition-colors"
                >
                  {isSubmitting ? "Signing in..." : "Sign in"}
                </button>
              )}
            />
          </form>
        </div>
      </div>
    </div>
  );
}
