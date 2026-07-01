import { UserPlus } from "lucide-react";

export default function HealerRegistrationPage() {
  return (
    <div className="flex items-start justify-center pt-12">
      <div className="max-w-lg w-full bg-card border border-border rounded-xl shadow-sm p-12 text-center">
        <div className="w-16 h-16 bg-primary/10 rounded-2xl flex items-center justify-center mx-auto">
          <UserPlus className="w-8 h-8 text-primary" />
        </div>
        <h2 className="text-xl font-bold text-primary-dark mt-5">Healer Registration</h2>
        <p className="text-sm text-text-light mt-2 max-w-sm mx-auto">
          Healer onboarding will be available here in an upcoming phase. Admins will be able to
          create healer accounts, assign clinic codes, and configure initial access from this screen.
        </p>
        <span className="mt-6 inline-block px-4 py-1.5 bg-primary/10 text-primary text-xs font-semibold rounded-full">
          Coming Soon
        </span>
      </div>
    </div>
  );
}
