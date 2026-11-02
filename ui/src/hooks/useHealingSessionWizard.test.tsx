import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, renderHook } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

import { useHealingSessionWizard } from "./useHealingSessionWizard";
import api from "../lib/axios";

vi.mock("../lib/axios", () => ({ default: { post: vi.fn(), put: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("./useReferenceData", () => ({ useReferenceData: () => ({ chakras: [], loading: false }) }));
vi.mock("./useHealingSessionForm", () => ({
  useHealingSessionForm: () => ({
    form: {
      validateAllFields: vi.fn(),
      state: { fieldMeta: {}, values: { sessionDate: "2026-10-25", startTime: "10:00", endTime: "10:30" } },
    },
  }),
}));

const wrapper = ({ children }: { children: ReactNode }) => (
  <QueryClientProvider client={new QueryClient()}>{children}</QueryClientProvider>
);

const crown = {
  id: "chakra-1",
  name: "Crown",
  color: "#fff",
  energyLevel: "C" as const,
  activationLevel: "N" as const,
  treatmentColors: [],
};

describe("useHealingSessionWizard", () => {
  beforeEach(() => vi.clearAllMocks());

  it("saving a corrected session updates it instead of creating a new one", async () => {
    const onSaved = vi.fn();
    (api.put as any).mockResolvedValue({ data: {} });
    const { result } = renderHook(
      () => useHealingSessionWizard({ caseId: "case-1", symptoms: [], sessionId: "session-1", onSaved }),
      { wrapper }
    );

    act(() => {
      result.current.setPreScanChakraData([crown]);
      result.current.setPostScanChakraData([crown]);
    });
    await act(() => result.current.handleNext()); // pre-scan -> post-scan
    await act(() => result.current.handleNext()); // save

    expect(api.post).not.toHaveBeenCalled();
    expect(api.put).toHaveBeenCalledWith("/api/v1/sessions/session-1", {
      scans: [
        expect.objectContaining({ scanType: "PRE", readings: [expect.objectContaining({ chakraId: "chakra-1", energyLevel: "C" })] }),
        expect.objectContaining({ scanType: "POST" }),
      ],
      scanDate: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
    });
    expect(onSaved).toHaveBeenCalled();
  });

  it("the scan date starts at today, can be changed, and is sent with a new session", async () => {
    (api.post as any).mockResolvedValue({ data: {} });
    const { result } = renderHook(
      () => useHealingSessionWizard({ caseId: "case-1", symptoms: [] }),
      { wrapper }
    );
    const today = new Date();
    const pad = (n: number) => String(n).padStart(2, "0");
    expect(result.current.scanDate).toBe(`${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`);

    act(() => {
      result.current.setScanDate("2026-11-01");
      result.current.setPreScanChakraData([crown]);
      result.current.setPostScanChakraData([crown]);
    });
    await act(() => result.current.handleNext());
    await act(() => result.current.handleNext());

    expect(api.post).toHaveBeenCalledWith(
      "/api/v1/sessions/case",
      expect.objectContaining({ scanDate: "2026-11-01" }),
      expect.anything()
    );
  });

  it("correcting a saved session starts from its own scan date", () => {
    const { result } = renderHook(
      () => useHealingSessionWizard({ caseId: "case-1", symptoms: [], sessionId: "s-1", initialScanDate: "2026-10-20" }),
      { wrapper }
    );
    expect(result.current.scanDate).toBe("2026-10-20");
  });
});
