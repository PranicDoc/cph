import type { ReactNode } from "react";
import { useUiVersion } from "../ui-version";
import { BrandMark } from "../ui-new/components/BrandMark";
import { AuthLogo } from "./AuthLogo";

/** The centred card the public account pages (sign up, forgot / reset password) sit in, in both views. */
export function AuthShell({ title, children }: { title: string; children: ReactNode }) {
  const { isNewView } = useUiVersion();
  return (
    <div className="min-h-screen bg-background flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      {!isNewView && (
        <div className="sm:mx-auto sm:w-full sm:max-w-md">
          <div className="flex justify-center">
            <AuthLogo />
          </div>
          <h2 className="mt-6 text-center text-2xl font-semibold text-text">{title}</h2>
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
              <h2 className="text-[22px] font-semibold">{title}</h2>
            </div>
          )}
          {children}
        </div>
      </div>
    </div>
  );
}
