import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import api from "../lib/axios";

export default function OAuth2Success() {
  const navigate = useNavigate();
  const { login } = useAuth();

  useEffect(() => {
    async function initialize() {
      try {
        const res = await api.get("/api/v1/auth/profile", {
          withCredentials: true,
        });

        login(res.data);

        navigate("/", { replace: true });
      } catch {
        navigate("/login", { replace: true });
      }
    }

    initialize();
  }, [login, navigate]);

  return (
    <div className="min-h-screen flex items-center justify-center">
      Signing you in...
    </div>
  );
}
