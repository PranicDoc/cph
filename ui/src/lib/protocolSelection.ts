import type { Protocol } from "../hooks/admin/useMasterData";

const LEGACY_PREFIX = "legacy:";

/** A stored protocol name that is not (or not yet) in the catalog, kept so it is not lost on edit. */
export const isLegacyProtocol = (p: Pick<Protocol, "id">): boolean => p.id.startsWith(LEGACY_PREFIX);

/**
 * Rebuilds the protocol selection from the comma-joined `protocolName` stored on the case.
 * Names are matched to the catalog ignoring case and spacing. A name with no catalog entry
 * (older free-text protocols, or the catalog still loading) is kept as a "legacy" entry rather
 * than dropped: dropping it made the protocol look empty on edit and, on save, erased it.
 */
export function reconstructSelectedProtocols(protocolNameCsv: string, catalog: Protocol[]): Protocol[] {
  const seen = new Set<string>();
  const result: Protocol[] = [];
  for (const raw of (protocolNameCsv ?? "").split(",")) {
    const name = raw.trim();
    const key = name.toLowerCase();
    if (!name || seen.has(key)) continue;
    seen.add(key);
    const match = catalog.find((p) => p.name.trim().toLowerCase() === key);
    result.push(
      match ?? {
        id: LEGACY_PREFIX + key,
        name,
        sourceBook: "",
        pageNumber: 0,
        protocolType: "PROTOCOL",
      }
    );
  }
  return result;
}
