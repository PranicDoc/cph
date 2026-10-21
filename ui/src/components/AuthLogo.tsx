import { Activity } from "lucide-react";
import { useUiVersion } from "../ui-version";
import { BrandMark } from "../ui-new/components/BrandMark";

/** The logo above the sign-in, sign-up and invite forms. */
export function AuthLogo() {
  const { isNewView } = useUiVersion();
  if (isNewView) return <BrandMark size={52} className="rounded-[14px]" />;
  return (
    <div className="w-12 h-12 bg-primary rounded-xl flex items-center justify-center shadow-lg shadow-primary/20">
      <Activity className="w-8 h-8 text-white" />
    </div>
  );
}
