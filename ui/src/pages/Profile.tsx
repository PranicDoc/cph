import { useState } from "react";
import { User, Mail, Building2, Hash, ShieldCheck, CreditCard, X } from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { PlanPicker } from "../components/PlanPicker";

function getInitials(name: string): string {
  return name
    .split(" ")
    .map((p) => p[0])
    .join("")
    .toUpperCase()
    .slice(0, 2);
}

function Field({ label, value, icon: Icon }: { label: string; value?: string | null; icon: React.ElementType }) {
  return (
    <div className="flex items-start gap-3 py-4 border-b border-border last:border-0">
      <div className="w-9 h-9 rounded-lg bg-primary/10 flex items-center justify-center shrink-0">
        <Icon className="w-4 h-4 text-primary" />
      </div>
      <div className="min-w-0">
        <p className="text-xs font-medium text-text-light uppercase tracking-wide">{label}</p>
        <p className="mt-0.5 text-sm font-medium text-text truncate">{value || "—"}</p>
      </div>
    </div>
  );
}

export default function Profile() {
  const { user } = useAuth();
  const [showUpgrade, setShowUpgrade] = useState(false);

  if (!user) return null;

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="bg-primary text-white p-4 rounded-xl">
        <h1 className="text-xl font-bold flex items-center gap-2">
          <User className="w-5 h-5" />
          My Profile
        </h1>
      </div>

      <div className="bg-card border border-border rounded-xl shadow-sm p-6 space-y-2">
        {/* Avatar + name header */}
        <div className="flex items-center gap-4 pb-4 border-b border-border">
          {user.avatarUrl ? (
            <img
              src={user.avatarUrl}
              alt={user.fullName}
              className="w-16 h-16 rounded-full object-cover"
            />
          ) : (
            <span className="w-16 h-16 flex items-center justify-center rounded-full bg-primary text-white text-xl font-bold shrink-0">
              {getInitials(user.fullName)}
            </span>
          )}
          <div>
            <p className="text-lg font-semibold text-primary-dark">{user.fullName}</p>
            <p className="text-sm text-text-light">{user.email}</p>
          </div>
        </div>

        <Field label="Full Name"   value={user.fullName}  icon={User} />
        <Field label="Email"       value={user.email}     icon={Mail} />
        <Field label="Role"        value={user.role}      icon={ShieldCheck} />
        <Field label="Clinic Code" value={user.clinicCode} icon={Hash} />
      </div>

      <div className="bg-card border border-border rounded-xl shadow-sm p-6">
        <h2 className="text-sm font-semibold text-primary-dark mb-3">Subscription</h2>
        <button
          onClick={() => setShowUpgrade(true)}
          className="inline-flex items-center gap-2 px-4 py-2 bg-primary hover:bg-primary-dark text-white text-sm font-medium rounded-lg transition-colors"
        >
          <CreditCard className="w-4 h-4" />
          Upgrade Plan
        </button>
      </div>

      {showUpgrade && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-end sm:items-center justify-center">
          <div className="bg-card w-full sm:max-w-3xl sm:mx-4 rounded-t-2xl sm:rounded-2xl shadow-xl relative max-h-[92vh] flex flex-col">
            <div className="sm:hidden flex justify-center pt-3 pb-1 shrink-0">
              <div className="w-10 h-1 bg-border rounded-full" />
            </div>
            <div className="flex-1 min-h-0 overflow-y-auto p-5 sm:p-8">
              <button
                onClick={() => setShowUpgrade(false)}
                className="absolute top-4 right-4 text-text-light hover:text-text transition-colors"
                aria-label="Close"
              >
                <X className="w-5 h-5" />
              </button>
              <PlanPicker
                ownerId={user.id}
                ownerType="HEALER"
                onComplete={() => setShowUpgrade(false)}
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
