import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import { RefreshCacheSheet } from "./RefreshCacheSheet";
import api from "../../lib/axios";

vi.mock("../../lib/axios", () => ({ default: { get: vi.fn(), post: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }));

const onClose = vi.fn();

function renderSheet() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RefreshCacheSheet onClose={onClose} />
    </QueryClientProvider>
  );
}

const answer = (redisCleared: boolean) =>
  vi.mocked(api.post).mockResolvedValue({ data: { caches: ["healerName"], key: null, redisCleared } });

describe("RefreshCacheSheet", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.get).mockResolvedValue({ data: ["healerEmail", "healerName"] });
  });

  it("refreshes every cache by default, then closes", async () => {
    answer(true);
    renderSheet();

    await userEvent.click(screen.getByRole("button", { name: "Refresh all caches" }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(api.post).toHaveBeenCalledWith("/api/v1/admin/cache/refresh", null, { params: {} });
    expect(toast.success).toHaveBeenCalledWith("Cache refreshed");
  });

  it("sends the chosen cache and the trimmed key", async () => {
    answer(true);
    renderSheet();

    await userEvent.click(screen.getByRole("combobox", { name: "Cache" }));
    await userEvent.click(await screen.findByRole("option", { name: /healerName/ }));
    await userEvent.type(screen.getByLabelText("Key (optional)"), "  abc  ");
    await userEvent.click(screen.getByRole("button", { name: "Refresh" }));

    await waitFor(() =>
      expect(api.post).toHaveBeenCalledWith("/api/v1/admin/cache/refresh", null, {
        params: { cacheName: "healerName", key: "abc" },
      })
    );
  });

  it("leaves a key of only spaces out", async () => {
    answer(true);
    renderSheet();

    await userEvent.type(screen.getByLabelText("Key (optional)"), "   ");
    await userEvent.click(screen.getByRole("button", { name: "Refresh all caches" }));

    await waitFor(() =>
      expect(api.post).toHaveBeenCalledWith("/api/v1/admin/cache/refresh", null, { params: {} })
    );
  });

  it("warns when Redis could not be reached and stays open to try again", async () => {
    answer(false);
    renderSheet();

    await userEvent.click(screen.getByRole("button", { name: "Refresh all caches" }));

    await waitFor(() =>
      expect(toast.warning).toHaveBeenCalledWith(
        "Cleared in memory, but Redis could not be reached. Try again."
      )
    );
    expect(toast.success).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("shows an error and stays open when the request fails", async () => {
    vi.mocked(api.post).mockRejectedValue(new Error("boom"));
    renderSheet();

    await userEvent.click(screen.getByRole("button", { name: "Refresh all caches" }));

    await waitFor(() => expect(toast.error).toHaveBeenCalled());
    expect(onClose).not.toHaveBeenCalled();
  });
});
