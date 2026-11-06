import { useUiVersion } from "../ui-version";
import { BrandMark } from "../ui-new/components/BrandMark";

/** The logo above the sign-in, sign-up and invite forms. */
export function AuthLogo() {
  const { isNewView } = useUiVersion();
  if (isNewView) return <BrandMark size={52} className="rounded-[14px]" />;
  return (
    <BrandMark size={48} />
  );
}
