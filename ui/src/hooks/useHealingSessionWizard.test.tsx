import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, renderHook } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

import { useHealingSessionWizard } from "./useHealingSessionWizard";
import api from "../lib/axios";

vi.mock("../lib/axios", () => ({ default: { post: vi.fn(), put: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("./useReferenceData", () => ({ useReferenceData: () => ({ chakras: [], loading: false }) }));
const mockForm = vi.hoisted(() => {
  const state = {
    fieldMeta: {},
    values: { sessionDate: "2026-10-25", startTime: "10:00", endTime: "10:30" } as Record<string, string>,
  };
  return {
    state,
    validateAllFields: () => Promise.resolve(),
    setFieldValue: (name: string, value: string) => {
      state.values[name] = value;
    },
  };
});
vi.mock("./useHealingSessionForm", () => ({ useHealingSessionForm: () => ({ form: mockForm }) }));

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
  beforeEach(() => {
    vi.clearAllMocks();
    mockForm.state.values.sessionDate = "2026-10-25";
  });

  it("saving a corrected session updates it instead of creating a new one", async () => {
    const onSaved = vi.fn();
    (api.put as any).mockResolvedValue({ data: {} });
    const { result } = renderHook(
      () => useHealingSessionWizard({ caseId: "case-1", symptoms: [], sessionId: "session-1", initialSessionNumber: 4, onSaved }),
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
      sessionDate: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
      sessionNumber: 4,
    });
    expect(onSaved).toHaveBeenCalled();
  });

  it("the session date starts at today, can be changed in the scan step, and is sent with a new session", async () => {
    vi.mocked(api.post).mockResolvedValue({ data: {} });
    const { result } = renderHook(
      () => useHealingSessionWizard({ caseId: "case-1", symptoms: [], suggestedSessionNumber: 3 }),
      { wrapper }
    );
    expect(result.current.sessionNumber).toBe("3"); // pre-filled, editable
    expect(result.current.sessionDate).toBe("2026-10-25"); // the form's default (today in the real form)

    act(() => {
      result.current.setSessionDate("2026-11-01");
      result.current.setPreScanChakraData([crown]);
      result.current.setPostScanChakraData([crown]);
    });
    await act(() => result.current.handleNext());
    await act(() => result.current.handleNext());

    expect(api.post).toHaveBeenCalledWith(
      "/api/v1/sessions/case",
      expect.objectContaining({ sessionDate: "2026-11-01", sessionNumber: 3 }),
      expect.anything()
    );
  });

  it("correcting a saved session starts from its own date", () => {
    const { result } = renderHook(
      () => useHealingSessionWizard({ caseId: "case-1", symptoms: [], sessionId: "s-1", initialSessionDate: "2026-10-20" }),
      { wrapper }
    );
    expect(result.current.sessionDate).toBe("2026-10-20");
  });
});

describe("useHealingSessionWizard session number", () => {
  it("refuses to save without a session number", async () => {
    const { result } = renderHook(
      () => useHealingSessionWizard({ caseId: "case-1", symptoms: [] }),
      { wrapper }
    );
    act(() => {
      result.current.setPreScanChakraData([crown]);
      result.current.setPostScanChakraData([crown]);
    });
    await act(() => result.current.handleNext());
    await act(() => result.current.handleNext());
    expect(api.post).not.toHaveBeenCalled();
  });
});
