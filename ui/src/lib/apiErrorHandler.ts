import axios from "axios";

/**
 * Builds a React Query error handler that sends the user to the shared
 * `/forbidden` page whenever any query or mutation comes back 403.
 *
 * `AdminRoute` already blocks the admin section with a client-side role
 * pre-check; this covers every other protected page, whose Phase-42
 * capability gate is only known once the API answers.
 *
 * `navigate` is injected so this stays a plain, unit-testable function with
 * no dependency on the router module.
 */
export function createApiErrorHandler(navigate: (path: string) => void) {
  return (error: unknown): void => {
    if (axios.isAxiosError(error) && error.response?.status === 403) {
      navigate("/forbidden");
    }
  };
}
