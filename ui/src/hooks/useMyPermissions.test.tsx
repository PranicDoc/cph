import { describe, it, expect, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

import { useMyPermissions } from "./useMyPermissions";

const auth = vi.hoisted(() => ({ user: { id: "u1", role: "HEALER" }, isAuthenticated: true }));
vi.mock("../context/AuthContext", () => ({ useAuth: () => auth }));
vi.mock("../lib/axios", () => ({ default: { get: vi.fn(() => Promise.resolve({ data: { grants: {} } })) } }));

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient()}>{children}</QueryClientProvider>;
}

describe("useMyPermissions", () => {
  it.each([
    ["SUPADM", true],
    ["ADMIN", false],
    ["HEALER", false],
  ])("marks a %s account's super admin flag as %s", async (role, expected) => {
    auth.user = { id: role, role };
    const { result } = renderHook(() => useMyPermissions(), { wrapper });
    await waitFor(() => expect(result.current.data).toBeDefined());
    expect(result.current.data?.superAdmin).toBe(expected);
  });
});
