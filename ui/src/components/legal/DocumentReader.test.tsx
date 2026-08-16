import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { DocumentReader, type DocumentReaderProps } from "./DocumentReader";
import type { PolicyVersionResponse } from "../../hooks/admin/useLegalDocuments";
import {
  triggerIntersection,
  resetIntersectionObserverMock,
} from "../../test/intersectionObserverMock";

vi.mock("../../lib/axios", () => ({
  default: { post: vi.fn(), patch: vi.fn() },
}));

import api from "../../lib/axios";

const mockedPost = api.post as unknown as ReturnType<typeof vi.fn>;
const mockedPatch = api.patch as unknown as ReturnType<typeof vi.fn>;

let originalSendBeacon: Navigator["sendBeacon"] | undefined;

const sampleDocument: PolicyVersionResponse = {
  id: "doc-1",
  consentType: "PRIVACY_POLICY",
  policyVersion: 3,
  mandatory: true,
  effectiveDate: "2026-01-01T00:00:00Z",
  content:
    "# Privacy Policy\n\nThis document describes how we handle your data.\n\n" +
    "| Column A | Column B |\n| --- | --- |\n| one | two |\n\n" +
    "Some terms are ~~no longer valid~~ superseded by later terms.\n\n" +
    "Please read this document carefully before agreeing.",
  publisherId: "pub-1",
  publisherName: "Admin User",
};

// Renders inside `act` and flushes the mount-time reading-session POST's
// microtask queue, so the hook's post-resolution `setSessionId` state
// update is captured within `act` instead of leaking a warning into
// whichever assertion or test happens to run next.
async function renderReader(props: DocumentReaderProps) {
  const utils = render(<DocumentReader {...props} />);
  await act(async () => {
    await Promise.resolve();
  });
  return utils;
}

describe("DocumentReader", () => {
  beforeEach(() => {
    resetIntersectionObserverMock();
    vi.clearAllMocks();
    mockedPost.mockResolvedValue({ data: { id: "sess-1" } });
    mockedPatch.mockResolvedValue({ data: {} });
    originalSendBeacon = navigator.sendBeacon;
    navigator.sendBeacon = vi.fn();
  });

  afterEach(() => {
    navigator.sendBeacon = originalSendBeacon as Navigator["sendBeacon"];
  });

  it("displays_the_version_number_from_first_render", async () => {
    await renderReader({ document: sampleDocument, label: "Privacy Policy", onAgree: vi.fn() });

    expect(screen.getByTestId("document-version")).toHaveTextContent(/Version 3/);
  });

  it("disables_the_i_agree_button_before_the_reader_reaches_the_end", async () => {
    await renderReader({ document: sampleDocument, label: "Privacy Policy", onAgree: vi.fn() });

    expect(screen.getByRole("button", { name: /i agree/i })).toBeDisabled();
  });

  it("enables_the_i_agree_button_after_the_sentinel_intersects", async () => {
    await renderReader({ document: sampleDocument, label: "Privacy Policy", onAgree: vi.fn() });

    expect(
      screen.getByText(/scroll to the end of the document to continue/i)
    ).toBeInTheDocument();

    act(() => {
      triggerIntersection(true);
    });

    expect(screen.getByRole("button", { name: /i agree/i })).toBeEnabled();
    expect(
      screen.queryByText(/scroll to the end of the document to continue/i)
    ).not.toBeInTheDocument();
  });

  it("calls_onAgree_with_document_id_and_version_on_click", async () => {
    const user = userEvent.setup();
    const onAgree = vi.fn();
    await renderReader({ document: sampleDocument, label: "Privacy Policy", onAgree });

    act(() => {
      triggerIntersection(true);
    });

    const button = screen.getByRole("button", { name: /i agree/i });
    await user.click(button);

    expect(onAgree).toHaveBeenCalledTimes(1);
    expect(onAgree).toHaveBeenCalledWith("doc-1", 3);
  });

  it("does_not_call_onAgree_while_the_gate_is_closed", async () => {
    const user = userEvent.setup();
    const onAgree = vi.fn();
    await renderReader({ document: sampleDocument, label: "Privacy Policy", onAgree });

    const button = screen.getByRole("button", { name: /i agree/i });
    await user.click(button);

    expect(onAgree).not.toHaveBeenCalled();
  });

  it("renders_embedded_html_as_text_rather_than_executing_it", async () => {
    const maliciousDocument: PolicyVersionResponse = {
      ...sampleDocument,
      content: "<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>",
    };

    const { container } = await renderReader({
      document: maliciousDocument,
      label: "Privacy Policy",
      onAgree: vi.fn(),
    });

    expect(container.querySelector("script")).toBeNull();
    expect(container.querySelector("img")).toBeNull();
    expect(container.textContent).toContain("<script>alert(1)</script>");
  });

  it("posts_an_open_reading_session_on_mount", async () => {
    await renderReader({ document: sampleDocument, label: "Privacy Policy", onAgree: vi.fn() });

    expect(mockedPost).toHaveBeenCalledTimes(1);
    expect(mockedPost).toHaveBeenCalledWith(
      "/api/v1/consents/reading-sessions",
      expect.objectContaining({ consentType: "PRIVACY_POLICY", policyVersion: 3 })
    );
  });
});
