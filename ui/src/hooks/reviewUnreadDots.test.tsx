import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ClipboardCheck, Send } from "lucide-react";

import Sidebar, { type SidebarNavItem } from "../components/Sidebar";
import MySubmissions from "../pages/reviews/MySubmissions";
import ReviewQueue from "../pages/reviews/ReviewQueue";
import { ReviewStreamProvider } from "../context/ReviewStreamProvider";
import { useReviewStream } from "./useReviewStream";

vi.mock("../lib/axios", () => ({
  default: { get: vi.fn().mockResolvedValue({ data: [] }), defaults: { baseURL: "" } },
  refreshSession: vi.fn(),
}));

class FakeEventSource {
  static CLOSED = 2;
  static instances: FakeEventSource[] = [];
  readyState = 1;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  listeners: ((e: MessageEvent) => void)[] = [];
  constructor() {
    FakeEventSource.instances.push(this);
  }
  addEventListener(_type: string, fn: (e: MessageEvent) => void) {
    this.listeners.push(fn);
  }
  close() {}
  emit(data: unknown) {
    this.listeners.forEach((fn) => fn(new MessageEvent("review", { data: JSON.stringify(data) })));
  }
}

const items: SidebarNavItem[] = [
  { label: "Reviews", path: "/reviews", icon: ClipboardCheck, unreadDot: true },
  { label: "Submissions", path: "/submissions", icon: Send, unreadDot: true },
];

/** A page that also reads the stream, to prove pages share the one connection. */
function Probe() {
  useReviewStream();
  return <p>probe</p>;
}

function renderApp() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/probe"]}>
        <ReviewStreamProvider enabled>
          <Sidebar items={items} isOpen onClose={() => {}} />
          <Routes>
            <Route path="/probe" element={<Probe />} />
            <Route path="/submissions" element={<MySubmissions />} />
            <Route path="/reviews" element={<ReviewQueue />} />
          </Routes>
        </ReviewStreamProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("unread dots on the review nav items", () => {
  beforeEach(() => {
    FakeEventSource.instances = [];
    vi.stubGlobal("EventSource", FakeEventSource);
  });
  afterEach(() => vi.unstubAllGlobals());

  it("unreadDotAppearsOnEventAndClearsOnOpen", async () => {
    const user = userEvent.setup();
    renderApp();

    // One shared stream however many components read it.
    expect(FakeEventSource.instances).toHaveLength(1);
    expect(screen.queryAllByLabelText("New activity")).toHaveLength(0);

    act(() => FakeEventSource.instances[0].emit({ submissionId: "s1", type: "COMMENT_ADDED", revisionNo: 1 }));
    expect((await screen.findAllByLabelText("New activity")).length).toBeGreaterThan(0);

    // Opening My submissions clears it.
    await user.click(screen.getAllByRole("link", { name: /Submissions/ })[0]);
    await waitFor(() => expect(screen.queryAllByLabelText("New activity")).toHaveLength(0));
    expect(FakeEventSource.instances).toHaveLength(1);

    // An event that arrives while the page is open does not leave a dot behind either.
    act(() => FakeEventSource.instances[0].emit({ submissionId: "s1", type: "STATUS_CHANGED", revisionNo: 1 }));
    await waitFor(() => expect(screen.queryAllByLabelText("New activity")).toHaveLength(0));
  });

  it("the instructor queue clears the dot too", async () => {
    const user = userEvent.setup();
    renderApp();
    act(() => FakeEventSource.instances[0].emit({ submissionId: "s1", type: "REVISION_ADDED", revisionNo: 2 }));
    expect((await screen.findAllByLabelText("New activity")).length).toBeGreaterThan(0);

    await user.click(screen.getAllByRole("link", { name: /Reviews/ })[0]);
    await waitFor(() => expect(screen.queryAllByLabelText("New activity")).toHaveLength(0));
  });
});
