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
  const usedIds = new Set<string>();
  const result: Protocol[] = [];
  // Several catalog protocols can share a name (same protocol in different books). The stored CSV
  // only has names, so each repeat of a name takes the next unused catalog entry of that name.
  // A repeat with no catalog entry left (or a repeated legacy name) is a true duplicate and dropped.
  for (const raw of (protocolNameCsv ?? "").split(",")) {
    const name = raw.trim();
    if (!name) continue;
    const key = name.toLowerCase();
    const sameName = catalog.filter((p) => p.name.trim().toLowerCase() === key);
    const match = sameName.find((p) => !usedIds.has(p.id));
    if (!match && sameName.length > 0) continue; // every catalog entry of this name is already selected
    const entry: Protocol = match ?? {
      id: LEGACY_PREFIX + key,
      name,
      sourceBook: "",
      pageNumber: 0,
      protocolType: "PROTOCOL",
    };
    if (usedIds.has(entry.id)) continue;
    usedIds.add(entry.id);
    result.push(entry);
  }
  return result;
}
