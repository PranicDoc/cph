import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const css = readFileSync(resolve(__dirname, "new-view.css"), "utf8");

/** The top-level rules and @media blocks of a stylesheet, comments removed. */
function topLevelBlocks(text: string) {
  const src = text.replace(/\/\*[\s\S]*?\*\//g, "");
  const blocks: string[] = [];
  let depth = 0;
  let start = 0;
  for (let i = 0; i < src.length; i++) {
    if (src[i] === "{") depth++;
    else if (src[i] === "}" && --depth === 0) {
      blocks.push(src.slice(start, i + 1).trim());
      start = i + 1;
    }
  }
  return blocks.filter((b) => b.includes(".hl-cpick"));
}

describe("close-case colour picker CSS", () => {
  const blocks = topLevelBlocks(css);

  it("sets the 460px desktop drawer width only from 768px up, so the phone sheet keeps the screen width", () => {
    const withWidth = blocks.filter((b) => /\.hl-cpick\s*\{[^}]*width:\s*460px/.test(b));
    expect(withWidth.length).toBeGreaterThan(0);
    withWidth.forEach((b) => expect(b.startsWith("@media (min-width: 768px)")).toBe(true));
  });

  it("makes it a bottom sheet 70px from the top with 20px corners below 768px", () => {
    const phone = blocks.find((b) => b.startsWith("@media (max-width: 767px)") && /\.hl-cpick\s*\{[^}]*top:\s*70px/.test(b));
    expect(phone).toBeDefined();
    expect(phone).toMatch(/\.hl-cpick\s*\{[^}]*border-radius:\s*20px 20px 0 0/);
  });
});

describe("close-case page height", () => {
  it("caps the shell at the screen height and scrolls the chakra list and the panel body", () => {
    expect(css).toMatch(/\.hl-main-col:has\(\.hl-ccp\)\s*\{[^}]*height:\s*100dvh/);
    expect(css).toMatch(/\.hl-cc-rows,\s*html\[data-ui="new"\] \.hl-cc-panel-b\s*\{[^}]*overflow-y:\s*auto/);
  });
});
