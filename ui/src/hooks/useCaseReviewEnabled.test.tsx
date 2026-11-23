import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook } from "@testing-library/react";

const { mockUseMyPermissions } = vi.hoisted(() => ({ mockUseMyPermissions: vi.fn() }));
vi.mock("./useMyPermissions", () => ({ useMyPermissions: () => mockUseMyPermissions() }));

import { useCaseReviewEnabled } from "./useCaseReviewEnabled";

describe("useCaseReviewEnabled", () => {
  beforeEach(() => vi.clearAllMocks());

  it("is false while the permissions load", () => {
    mockUseMyPermissions.mockReturnValue({ data: undefined, isPending: true });
    expect(renderHook(() => useCaseReviewEnabled()).result.current).toBe(false);
  });

  it("is false when the permissions request failed", () => {
    mockUseMyPermissions.mockReturnValue({ data: undefined, isError: true });
    expect(renderHook(() => useCaseReviewEnabled()).result.current).toBe(false);
  });

  it("is false when the payload says off or says nothing", () => {
    mockUseMyPermissions.mockReturnValue({ data: { grants: {}, caseReviewEnabled: false } });
    expect(renderHook(() => useCaseReviewEnabled()).result.current).toBe(false);
    mockUseMyPermissions.mockReturnValue({ data: { grants: {} } });
    expect(renderHook(() => useCaseReviewEnabled()).result.current).toBe(false);
  });

  it("is true when the payload says on", () => {
    mockUseMyPermissions.mockReturnValue({ data: { grants: {}, caseReviewEnabled: true } });
    expect(renderHook(() => useCaseReviewEnabled()).result.current).toBe(true);
  });
});
