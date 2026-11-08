import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, fireEvent, waitFor } from "@testing-library/react";
import { useState } from "react";

import ClosureForm from "./ClosureForm";
import { additionalSessionsToNumber } from "@/hooks/useCaseClosure";
import { renderWithDataRouter } from "../../test/renderWithDataRouter";

vi.mock("@/lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));
vi.mock("../../ui-version", () => ({
  useUiVersion: () => ({ isNewView: true }),
}));
vi.mock("@/hooks/useReferenceData", () => ({
  useReferenceData: () => ({ colors: [], loading: false, error: null }),
}));
vi.mock("@/hooks/admin/useMasterData", () => ({
  useProtocols: () => ({ data: [], isLoading: false }),
}));
vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import api from "@/lib/axios";

const mGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mPost = api.post as unknown as ReturnType<typeof vi.fn>;

describe("additionalSessionsToNumber", () => {
  it("returns null for blank, zero or non-numeric input", () => {
    expect(additionalSessionsToNumber("")).toBeNull();
    expect(additionalSessionsToNumber("0")).toBeNull();
    expect(additionalSessionsToNumber("abc")).toBeNull();
  });

  it("parses positive integers", () => {
    expect(additionalSessionsToNumber("4")).toBe(4);
    expect(additionalSessionsToNumber("12")).toBe(12);
  });
});

function Harness({ onValue }: { onValue: (v: string) => void }) {
  const [value, setValue] = useState("");
  const noop = vi.fn();
  return (
    <ClosureForm
      comments=""
      setComments={noop}
      isHealingContinued={false}
      setIsHealingContinued={noop}
      additionalSessions={value}
      setAdditionalSessions={(v) => {
        setValue(v);
        onValue(v);
      }}
      selectedProtocols={[]}
      setSelectedProtocols={noop}
      closeMutation={{ mutate: noop, isPending: false } as never}
      onClose={noop}
      healingCaseId="case-1"
    />
  );
}

describe("ClosureForm additional sessions input", () => {
  beforeEach(() => {
    mGet.mockReset();
    mPost.mockReset();
    mGet.mockImplementation((url: string) => {
      if (url.endsWith("/chakras")) return Promise.resolve({ data: { data: ["Solar Plexus"] } });
      if (url.endsWith("/prefill")) return Promise.resolve({ data: { chakraSelections: [] } });
      return Promise.resolve({ data: {} });
    });
    mPost.mockResolvedValue({ data: {} });
  });

  it("accepts digits only on step 2", async () => {
    const seen: string[] = [];
    renderWithDataRouter([
      { path: "/", element: <Harness onValue={(v) => seen.push(v)} /> },
    ]);

    const next = await screen.findByRole("button", { name: /^next$/i });
    await waitFor(() => expect(next).not.toBeDisabled());
    fireEvent.click(next);

    const input = await screen.findByLabelText(/additional sessions/i);
    fireEvent.change(input, { target: { value: "3a-5" } });

    expect(seen.at(-1)).toBe("35");
    expect((input as HTMLInputElement).value).toBe("35");
  });
});
