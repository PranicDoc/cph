import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

vi.mock("../lib/axios", () => ({ default: { get: vi.fn(), put: vi.fn() } }));
vi.mock("../context/AuthContext", () => ({
  useAuth: () => ({ user: { id: "h1", fullName: "Asha Rao", email: "asha@example.com" }, setUser: vi.fn() }),
}));
const view = vi.hoisted(() => ({ isNewView: true }));
vi.mock("../ui-version", () => ({ useUiVersion: () => view }));
vi.mock("../hooks/useCreditAccount", () => ({ useCreditAccount: () => ({ data: undefined, isLoading: false }) }));
vi.mock("../components/UpgradePlanDialog", () => ({
  UpgradePlanDialog: ({ open }: { open: boolean }) => (open ? <div>Buy credits dialog</div> : null),
}));
vi.mock("../components/DeleteMyAccount", () => ({ DeleteMyAccount: () => null }));
vi.mock("../components/CreditHistory", () => ({ CreditHistory: () => null }));
vi.mock("../ui-new/components/CreditHistoryCard", () => ({ CreditHistoryCard: () => null }));
vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import api from "../lib/axios";
import { toast } from "sonner";
import Profile from "./Profile";

const mGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mPut = api.put as unknown as ReturnType<typeof vi.fn>;

const profile = { id: "h1", fullName: "Asha Rao", email: "asha@example.com", hasPassword: true, hasGoogleLinked: false };

async function editProfile(nextNameChangeCost: number) {
  mGet.mockResolvedValue({ data: { ...profile, nextNameChangeCost } });
  mPut.mockImplementation((_url: string, body: object) => Promise.resolve({ data: { ...profile, ...body } }));
  const user = userEvent.setup();
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <Profile />
    </QueryClientProvider>
  );
  await user.click(await screen.findByRole("button", { name: "Edit personal information" }));
  return user;
}

describe("Profile name change cost", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    view.isNewView = true;
  });

  it("says the next name change is free", async () => {
    await editProfile(0);
    expect(screen.getByText("Your next name change is free")).toBeInTheDocument();
  });

  it("says what the next name change costs", async () => {
    await editProfile(25);
    expect(screen.getByText("Your next name change costs 25 credits")).toBeInTheDocument();
  });

  it("shows the cost in the classic view too", async () => {
    view.isNewView = false;
    mGet.mockResolvedValue({ data: { ...profile, nextNameChangeCost: 100 } });
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <Profile />
      </QueryClientProvider>
    );
    await user.click((await screen.findAllByTitle("Edit"))[0]);
    expect(screen.getByText("Your next name change costs 100 credits")).toBeInTheDocument();
  });

  it("asks before a free name change and saves on confirm", async () => {
    const user = await editProfile(0);
    await user.clear(screen.getByLabelText("Full name"));
    await user.type(screen.getByLabelText("Full name"), "Asha Devi");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText(/This name change is free/)).toBeInTheDocument();
    expect(mPut).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Change name" }));
    expect(mPut).toHaveBeenCalledWith("/api/v1/auth/profile", expect.objectContaining({ fullName: "Asha Devi" }));
  });

  it("states the credit cost before a paid name change", async () => {
    const user = await editProfile(50);
    await user.clear(screen.getByLabelText("Full name"));
    await user.type(screen.getByLabelText("Full name"), "Asha Devi");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText(/This name change costs 50 credits/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Change name for 50 credits" }));
    expect(mPut).toHaveBeenCalledTimes(1);
  });

  it("saves an email-only change without asking", async () => {
    const user = await editProfile(25);
    await user.clear(screen.getByLabelText("Email"));
    await user.type(screen.getByLabelText("Email"), "new@example.com");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(mPut).toHaveBeenCalledWith("/api/v1/auth/profile", expect.objectContaining({ email: "new@example.com" }));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("shows the server message and the buy-credits dialog when credits run short", async () => {
    const user = await editProfile(25);
    const detail = "You need 25 credits to change your name. Buy credits to continue.";
    mPut.mockRejectedValue({ response: { status: 402, data: { detail } } });
    await user.clear(screen.getByLabelText("Full name"));
    await user.type(screen.getByLabelText("Full name"), "Asha Devi");
    await user.click(screen.getByRole("button", { name: "Save" }));
    await user.click(await screen.findByRole("button", { name: "Change name for 25 credits" }));

    expect(await screen.findByText("Buy credits dialog")).toBeInTheDocument();
    expect(toast.error).toHaveBeenCalledWith(detail, expect.anything());
  });
});
