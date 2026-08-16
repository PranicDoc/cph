import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, within, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { DocumentTabs, type DocumentTabsProps } from "./DocumentTabs";
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

const documents: PolicyVersionResponse[] = [
  {
    id: "doc-terms",
    consentType: "TERMS_AND_CONDITIONS",
    policyVersion: 1,
    mandatory: true,
    effectiveDate: "2026-01-01T00:00:00Z",
    content: "CONTENT_TERMS — please read the terms carefully.",
    publisherId: "pub-1",
    publisherName: "Admin User",
  },
  {
    id: "doc-privacy",
    consentType: "PRIVACY_POLICY",
    policyVersion: 2,
    mandatory: true,
    effectiveDate: "2026-01-01T00:00:00Z",
    content: "CONTENT_PRIVACY — how we handle your data.",
    publisherId: "pub-1",
    publisherName: "Admin User",
  },
  {
    id: "doc-medical",
    consentType: "MEDICAL_DISCLAIMER",
    policyVersion: 3,
    mandatory: true,
    effectiveDate: "2026-01-01T00:00:00Z",
    content: "CONTENT_MEDICAL — medical disclaimer text.",
    publisherId: "pub-1",
    publisherName: "Admin User",
  },
  {
    id: "doc-practitioner",
    consentType: "PRACTITIONER_AGREEMENT",
    policyVersion: 4,
    mandatory: true,
    effectiveDate: "2026-01-01T00:00:00Z",
    content: "CONTENT_PRACTITIONER — practitioner agreement text.",
    publisherId: "pub-1",
    publisherName: "Admin User",
  },
];

const labels: Record<string, string> = {
  TERMS_AND_CONDITIONS: "Terms and Conditions",
  PRIVACY_POLICY: "Privacy Policy",
  MEDICAL_DISCLAIMER: "Medical Disclaimer",
  PRACTITIONER_AGREEMENT: "Practitioner Agreement",
};

async function renderTabs(props: Partial<DocumentTabsProps> = {}) {
  const utils = render(
    <DocumentTabs documents={documents} labels={labels} onAgree={vi.fn()} {...props} />
  );
  await act(async () => {
    await Promise.resolve();
  });
  return utils;
}

describe("DocumentTabs", () => {
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

  it("renders_one_tab_per_document_with_its_label", async () => {
    await renderTabs();

    const tablist = screen.getByRole("tablist");
    const tabs = within(tablist).getAllByRole("tab");
    expect(tabs).toHaveLength(4);
    Object.values(labels).forEach((label) => {
      expect(within(tablist).getByText(label)).toBeInTheDocument();
    });
  });

  it("selects_the_first_document_by_default", async () => {
    await renderTabs();

    expect(screen.getByText(/CONTENT_TERMS/)).toBeInTheDocument();
    expect(screen.queryByText(/CONTENT_PRIVACY/)).not.toBeInTheDocument();
  });

  it("allows_opening_documents_in_any_order", async () => {
    const user = userEvent.setup();
    await renderTabs();

    // D-01: click the fourth tab directly, skipping tabs 2 and 3 entirely.
    const practitionerTab = screen.getByTestId("tab-PRACTITIONER_AGREEMENT");
    await user.click(practitionerTab);
    await act(async () => {
      await Promise.resolve();
    });

    expect(screen.getByText(/CONTENT_PRACTITIONER/)).toBeInTheDocument();
  });

  it("checks_off_only_the_agreed_document", async () => {
    const user = userEvent.setup();
    const onAgree = vi.fn();
    await renderTabs({ onAgree });

    act(() => {
      triggerIntersection(true);
    });

    const agreeButton = screen.getByRole("button", { name: /i agree/i });
    await user.click(agreeButton);

    const termsTab = screen.getByTestId("tab-TERMS_AND_CONDITIONS");
    const privacyTab = screen.getByTestId("tab-PRIVACY_POLICY");
    const medicalTab = screen.getByTestId("tab-MEDICAL_DISCLAIMER");
    const practitionerTab = screen.getByTestId("tab-PRACTITIONER_AGREEMENT");

    expect(termsTab).toHaveTextContent(/agreed/i);
    expect(privacyTab).not.toHaveTextContent(/agreed/i);
    expect(medicalTab).not.toHaveTextContent(/agreed/i);
    expect(practitionerTab).not.toHaveTextContent(/agreed/i);

    expect(onAgree).toHaveBeenCalledTimes(1);
    expect(onAgree).toHaveBeenCalledWith("doc-terms", 1);
  });

  // Pitfall 3 proof: switching tabs must unmount the previous DocumentReader
  // (closing its reading session) and mount a fresh one for the entering
  // document (opening a new session) — never both mounted simultaneously.
  it("unmounts_the_previous_reader_when_switching_tabs", async () => {
    const user = userEvent.setup();
    await renderTabs();

    expect(screen.getByText(/CONTENT_TERMS/)).toBeInTheDocument();
    expect(mockedPost).toHaveBeenCalledTimes(1);

    const privacyTab = screen.getByTestId("tab-PRIVACY_POLICY");
    await user.click(privacyTab);
    await act(async () => {
      await Promise.resolve();
    });

    expect(screen.queryByText(/CONTENT_TERMS/)).not.toBeInTheDocument();
    expect(screen.getByText(/CONTENT_PRIVACY/)).toBeInTheDocument();

    // Leaving document's close-tracking fired on unmount.
    expect(mockedPatch).toHaveBeenCalledTimes(1);
    // Entering document opened a fresh session.
    expect(mockedPost).toHaveBeenCalledTimes(2);
  });
});
