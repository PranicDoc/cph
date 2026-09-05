import { describe, expect, it, vi } from "vitest";
import { AxiosError } from "axios";
import { createApiErrorHandler } from "./apiErrorHandler";

function axiosErrorWithStatus(status: number): AxiosError {
  const err = new AxiosError("request failed");
  // @ts-expect-error minimal shape for the handler under test
  err.response = { status };
  return err;
}

describe("createApiErrorHandler", () => {
  it("navigates to /forbidden on a 403 response", () => {
    const navigate = vi.fn();
    createApiErrorHandler(navigate)(axiosErrorWithStatus(403));
    expect(navigate).toHaveBeenCalledExactlyOnceWith("/forbidden");
  });

  it("does not navigate on a non-403 response (e.g. 500)", () => {
    const navigate = vi.fn();
    createApiErrorHandler(navigate)(axiosErrorWithStatus(500));
    expect(navigate).not.toHaveBeenCalled();
  });

  it("does not navigate on a non-axios error", () => {
    const navigate = vi.fn();
    createApiErrorHandler(navigate)(new Error("boom"));
    expect(navigate).not.toHaveBeenCalled();
  });
});
