import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { HealingSessionWizard } from "./HealingSession";

vi.mock("../lib/axios", () => ({ default: { post: vi.fn(), put: vi.fn() } }));
vi.mock("../hooks/useReferenceData", () => ({
  useReferenceData: () => ({
    chakras: [
      { id: "chakra-crown", code: "CROWN", name: "Crown Chakra" },
      { id: "chakra-ajna", code: "AJNA", name: "Ajna Chakra" },
    ],
    colors: [],
    loading: false,
  }),
}));
vi.mock("../hooks/admin/useMasterData", () => ({
  useChakrasForProtocols: () => ({ data: [], isLoading: false }),
}));

const savedSession = {
  id: "session-1",
  scans: [
    { scanType: "PRE" as const, readings: [{ chakraId: "chakra-crown", chakraName: "Crown Chakra", energyLevel: "D", activationLevel: "U" }] },
    { scanType: "POST" as const, readings: [{ chakraId: "chakra-ajna", chakraName: "Ajna Chakra", energyLevel: "C", activationLevel: "O" }] },
  ],
};

describe("HealingSessionWizard: correcting a saved session", () => {
  it("shows the saved pre-scan readings", async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <HealingSessionWizard caseId="case-1" symptoms={[]} onClose={vi.fn()} session={savedSession} />
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect((await screen.findAllByText("Crown Chakra")).length).toBeGreaterThan(0);
    expect(screen.queryByText("Ajna Chakra")).not.toBeInTheDocument();
  });

  it("matches a reading by name when its chakra id is from an older catalog", async () => {
    const oldIds = {
      id: "session-2",
      scans: [
        { scanType: "PRE" as const, readings: [{ chakraId: "old-crown-id", chakraName: "Crown Chakra", energyLevel: "D", activationLevel: "U" }] },
        { scanType: "POST" as const, readings: [] },
      ],
    };
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <HealingSessionWizard caseId="case-1" symptoms={[]} onClose={vi.fn()} session={oldIds} />
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect((await screen.findAllByText("Crown Chakra")).length).toBeGreaterThan(0);
    expect(screen.queryByText(/select chakra/i)).not.toBeInTheDocument();
  });

  it("names a saved chakra that is no longer in the list so the healer can replace it", async () => {
    const removedChakra = {
      id: "session-3",
      scans: [
        { scanType: "PRE" as const, readings: [{ chakraId: "old-spleen-id", chakraName: "Spleen", energyLevel: "D", activationLevel: "U" }] },
        { scanType: "POST" as const, readings: [] },
      ],
    };
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <HealingSessionWizard caseId="case-1" symptoms={[]} onClose={vi.fn()} session={removedChakra} />
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect((await screen.findAllByPlaceholderText("Spleen (not in chakra list)")).length).toBeGreaterThan(0);
  });
});
