import { useMyPermissions } from "./useMyPermissions";

/**
 * Whether the case-review workflow is switched on (the server's global flag, carried on the
 * permissions payload). False while the permissions load or when they fail, so review surfaces
 * never flash up and then disappear. The server still 404s the review endpoints while it is off.
 */
export function useCaseReviewEnabled(): boolean {
  const { data } = useMyPermissions();
  return data?.caseReviewEnabled === true;
}
