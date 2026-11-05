import { describe, it, expect } from "vitest";

import { extractDetail } from "./utils";

const error = (status: number, data: object) => ({ response: { status, data } });

describe("extractDetail", () => {
  it("adds the reference to a server fault, so the healer can quote it", () => {
    expect(extractDetail(error(500, { detail: "An unexpected error occurred", requestId: "abc-123" }))).toBe(
      "An unexpected error occurred (Ref: abc-123)"
    );
  });

  it("leaves a validation message as it is: the healer can fix that alone", () => {
    expect(extractDetail(error(400, { detail: "Pick a session", requestId: "abc-123" }))).toBe("Pick a session");
  });

  it("copes with no reference, no detail and no response", () => {
    expect(extractDetail(error(500, { detail: "Failed" }))).toBe("Failed");
    expect(extractDetail(error(500, { requestId: "abc-123" }))).toBeNull();
    expect(extractDetail(new Error("network"))).toBeNull();
    expect(extractDetail(null)).toBeNull();
  });
});
