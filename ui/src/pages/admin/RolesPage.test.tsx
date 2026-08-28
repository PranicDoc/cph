import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import RolesPage from "./RolesPage";
import { renderWithDataRouter } from "../../test/renderWithDataRouter";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn() },
}));

import api from "../../lib/axios";
import { toast } from "sonner";

const mockedGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mockedDelete = api.delete as unknown as ReturnType<typeof vi.fn>;
const mockedToastSuccess = toast.success as unknown as ReturnType<typeof vi.fn>;
const mockedToastError = toast.error as unknown as ReturnType<typeof vi.fn>;

type PageStub = {
  content: Array<{
    id: string;
    name: string;
    description: string | null;
    resourceCount: number;
    userCount: number;
  }>;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
};

function pageStub(overrides: Partial<PageStub> = {}): PageStub {
  return {
    content: [
      {
        id: "r1",
        name: "Billing",
        description: "Handles invoices",
        resourceCount: 2,
        userCount: 3,
      },
      {
        id: "r2",
        name: "Support",
        description: null,
        resourceCount: 1,
        userCount: 0,
      },
    ],
    page: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1,
    last: true,
    ...overrides,
  };
}

function renderPage(path = "/admin/roles") {
  return renderWithDataRouter(
    [
      { path: "/admin/roles", element: <RolesPage /> },
      { path: "/admin/roles/new", element: <div>NEW ROUTE</div> },
      { path: "/admin/roles/:id/edit", element: <div>EDIT ROUTE</div> },
      { path: "/admin/roles/:id/users", element: <div>USERS ROUTE</div> },
    ],
    { initialEntries: [path] },
  );
}

function lastGetParams() {
  const calls = mockedGet.mock.calls;
  return calls[calls.length - 1][1].params as Record<string, unknown>;
}

beforeEach(() => {
  mockedGet.mockReset();
  mockedDelete.mockReset();
  mockedToastSuccess.mockReset();
  mockedToastError.mockReset();
});

describe("RolesPage — server-paged searchable roles list", () => {
  it("issues the D-08 default query params on first render", async () => {
    mockedGet.mockResolvedValue({ data: pageStub() });
    renderPage();

    await waitFor(() => expect(mockedGet).toHaveBeenCalled());
    expect(mockedGet).toHaveBeenCalledWith("/api/admin/roles", {
      params: { search: "", page: 0, size: 20, sort: "name,asc" },
    });
  });

  it("does not request on keystroke, then debounces (300ms) into one request with page reset to 0", async () => {
    vi.useFakeTimers();
    try {
      mockedGet.mockResolvedValue({ data: pageStub() });
      renderPage();
      await act(async () => {
        await Promise.resolve();
      });
      expect(mockedGet).toHaveBeenCalledTimes(1);

      fireEvent.change(screen.getByPlaceholderText("Search roles"), {
        target: { value: "bill" },
      });
      // No request fired synchronously on the keystroke.
      expect(mockedGet).toHaveBeenCalledTimes(1);

      await act(async () => {
        vi.advanceTimersByTime(300);
        await Promise.resolve();
      });

      expect(mockedGet).toHaveBeenCalledTimes(2);
      expect(lastGetParams()).toMatchObject({ search: "bill", page: 0 });
    } finally {
      vi.useRealTimers();
    }
  });

  it("resets page to 0 on a search write even when starting on a later page (Pitfall 6 guard)", async () => {
    vi.useFakeTimers();
    try {
      mockedGet.mockResolvedValue({
        data: pageStub({ page: 3, totalPages: 5, totalElements: 90 }),
      });
      renderPage("/admin/roles?page=3");
      await act(async () => {
        await Promise.resolve();
      });
      expect(mockedGet.mock.calls[0][1].params).toMatchObject({ page: 3 });

      fireEvent.change(screen.getByPlaceholderText("Search roles"), {
        target: { value: "x" },
      });
      await act(async () => {
        vi.advanceTimersByTime(300);
        await Promise.resolve();
      });

      expect(lastGetParams()).toMatchObject({ search: "x", page: 0 });
    } finally {
      vi.useRealTimers();
    }
  });

  it("toggles the Name sort via the URL and resets page to 0 (no client re-sort)", async () => {
    const user = userEvent.setup();
    mockedGet.mockResolvedValue({ data: pageStub() });
    renderPage("/admin/roles?page=2");
    await screen.findByText("Billing");

    await user.click(screen.getByRole("button", { name: "Name" }));
    await waitFor(() =>
      expect(lastGetParams()).toMatchObject({ sort: "name,desc", page: 0 }),
    );

    await user.click(screen.getByRole("button", { name: "Name" }));
    await waitFor(() =>
      expect(lastGetParams()).toMatchObject({ sort: "name,asc", page: 0 }),
    );
  });

  it("renders resource/user counts from the payload and an em-dash for a null description", async () => {
    mockedGet.mockResolvedValue({ data: pageStub() });
    renderPage();

    const billingRow = (await screen.findByText("Billing")).closest("tr");
    expect(billingRow).not.toBeNull();
    expect(within(billingRow as HTMLElement).getByText("2")).toBeInTheDocument();
    expect(within(billingRow as HTMLElement).getByText("3")).toBeInTheDocument();

    const supportRow = screen.getByText("Support").closest("tr") as HTMLElement;
    expect(within(supportRow).getByText("—")).toBeInTheDocument();
  });

  it("shows the total count and disables the pager at the bounds", async () => {
    mockedGet.mockResolvedValue({
      data: pageStub({ totalElements: 57, totalPages: 3, page: 0 }),
    });
    renderPage();
    await screen.findByText("Billing");

    expect(screen.getByText("57 roles")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Previous page" })).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "Next page" }),
    ).not.toBeDisabled();
  });

  it("disables Next on the last page", async () => {
    mockedGet.mockResolvedValue({
      data: pageStub({ totalElements: 57, totalPages: 3, page: 2 }),
    });
    renderPage("/admin/roles?page=2");
    await screen.findByText("Billing");

    expect(screen.getByRole("button", { name: "Next page" })).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "Previous page" }),
    ).not.toBeDisabled();
  });

  it("shows the 'No roles yet' empty state for an unsearched empty list", async () => {
    mockedGet.mockResolvedValue({
      data: pageStub({ content: [], totalElements: 0, totalPages: 0 }),
    });
    renderPage();

    expect(await screen.findByText("No roles yet")).toBeInTheDocument();
  });

  it("shows the search-miss empty state and keeps the search input mounted", async () => {
    mockedGet.mockResolvedValue({
      data: pageStub({ content: [], totalElements: 0, totalPages: 0 }),
    });
    renderPage("/admin/roles?search=zzz");

    expect(await screen.findByText(/No roles match/)).toBeInTheDocument();
    expect(screen.getByText(/zzz/)).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Search roles")).toBeInTheDocument();
  });

  it("shows the load-error copy with a Retry control", async () => {
    mockedGet.mockRejectedValue(new Error("network"));
    renderPage();

    expect(
      await screen.findByText(
        "Couldn't load roles. Check your connection and try again.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
  });

  it("on a 409 self-lockout: keeps the dialog open, shows the detail in role=alert, leaves the row, no success toast", async () => {
    const user = userEvent.setup();
    mockedGet.mockResolvedValue({ data: pageStub() });
    mockedDelete.mockRejectedValue({
      response: {
        status: 409,
        data: {
          detail: "This change would leave nobody able to manage roles.",
        },
      },
    });
    renderPage();
    await screen.findByText("Billing");

    await user.click(screen.getByRole("button", { name: "Delete Billing" }));
    expect(await screen.findByText("Delete this role?")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Delete role" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(
      "This change would leave nobody able to manage roles.",
    );
    // Dialog still open, row still present, no success toast.
    expect(screen.getByText("Delete this role?")).toBeInTheDocument();
    expect(screen.getByText("Billing")).toBeInTheDocument();
    expect(mockedToastSuccess).not.toHaveBeenCalled();
  });

  it("on a non-409 delete error: closes the dialog and calls toast.error", async () => {
    const user = userEvent.setup();
    mockedGet.mockResolvedValue({ data: pageStub() });
    mockedDelete.mockRejectedValue({
      response: { status: 500, data: { detail: "boom" } },
    });
    renderPage();
    await screen.findByText("Billing");

    await user.click(screen.getByRole("button", { name: "Delete Billing" }));
    await user.click(
      await screen.findByRole("button", { name: "Delete role" }),
    );

    await waitFor(() => expect(mockedToastError).toHaveBeenCalled());
    expect(screen.queryByText("Delete this role?")).not.toBeInTheDocument();
  });
});
