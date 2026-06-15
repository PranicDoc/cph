import { Activity } from "lucide-react";
import { useAuth } from "../context/AuthContext";

export default function Dashboard() {
  const { user } = useAuth();

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
      <div className="w-16 h-16 bg-primary/10 rounded-2xl flex items-center justify-center">
        <Activity className="w-8 h-8 text-primary" />
      </div>
      <div>
        <h1 className="text-2xl font-bold text-text">
          Welcome{user?.fullName ? `, ${user.fullName}` : ""}
        </h1>
        <p className="text-text-light mt-1">Your dashboard is coming soon.</p>
      </div>
    </div>
  );
}
