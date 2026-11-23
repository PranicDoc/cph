import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

import api from "../lib/axios";

const flag = vi.hoisted(() => ({ on: false }));
vi.mock("../lib/axios", () => ({ default: { get: vi.fn() } }));
vi.mock("./useCaseReviewEnabled", () => ({ useCaseReviewEnabled: () => flag.on }));

import { useCaseReviewStatuses } from "./useCaseReviewStatuses";
import { useMyApprovedCount } from "./useMyApprovedCount";
import { useApprovedEditGuard } from "./useApprovedEditGuard";

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function Editor({ onEdit }: { onEdit: () => void }) {
  const { guard, dialog, isApproved } = useApprovedEditGuard("c1");
  return (
    <>
      {isApproved && <span>approved</span>}
      <button type="button" onClick={() => guard(onEdit)}>
        edit
      </button>
      {dialog}
    </>
  );
}

describe("review hooks gated by the case-review flag", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    flag.on = false;
    vi.mocked(api.get).mockImplementation((url: string) =>
      Promise.resolve({
        data: url.endsWith("case-statuses")
          ? [{ caseId: "c1", status: "APPROVED", eligible: true, latestRevisionNo: 1 }]
          : { count: 2 },
      })
    );
  });

  it("useCaseReviewStatuses makes no request while off", async () => {
    const { result } = renderHook(() => useCaseReviewStatuses(["c1"]), { wrapper });
    await new Promise((r) => setTimeout(r, 30));
    expect(api.get).not.toHaveBeenCalled();
    expect(result.current.data).toBeUndefined();
  });

  it("useCaseReviewStatuses fetches while on", async () => {
    flag.on = true;
    const { result } = renderHook(() => useCaseReviewStatuses(["c1"]), { wrapper });
    await waitFor(() => expect(result.current.data?.c1.status).toBe("APPROVED"));
  });

  it("useMyApprovedCount makes no request while off", async () => {
    const { result } = renderHook(() => useMyApprovedCount(), { wrapper });
    await new Promise((r) => setTimeout(r, 30));
    expect(api.get).not.toHaveBeenCalled();
    expect(result.current.data).toBeUndefined();
  });

  it("useMyApprovedCount fetches while on", async () => {
    flag.on = true;
    const { result } = renderHook(() => useMyApprovedCount(), { wrapper });
    await waitFor(() => expect(result.current.data).toBe(2));
  });

  it("useApprovedEditGuard does not guard while off: no request, the edit runs at once", () => {
    const onEdit = vi.fn();
    render(<Editor onEdit={onEdit} />, { wrapper });
    screen.getByText("edit").click();
    expect(onEdit).toHaveBeenCalledTimes(1);
    expect(api.get).not.toHaveBeenCalled();
    expect(screen.queryByText("approved")).not.toBeInTheDocument();
  });

  it("useApprovedEditGuard guards an approved case while on", async () => {
    flag.on = true;
    const onEdit = vi.fn();
    render(<Editor onEdit={onEdit} />, { wrapper });
    await screen.findByText("approved");
    screen.getByText("edit").click();
    expect(onEdit).not.toHaveBeenCalled();
  });
});
