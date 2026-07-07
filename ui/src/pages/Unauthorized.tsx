import { useLocation } from "react-router-dom";
import ErrorState from "../components/ErrorState";

export default function UnauthorizedPage() {
  const location = useLocation();
  const error = (location.state as { error?: unknown } | null)?.error;

  return <ErrorState variant="expired" error={error} />;
}
