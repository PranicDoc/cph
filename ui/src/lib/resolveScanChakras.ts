export interface ChakraLike {
  id: string;
}

/**
 * Which chakras a healing session's scan step should offer, given the full chakra catalog
 * and the chakras linked to the session's selected protocol(s).
 *
 * No protocol selected yet: nothing to filter by, show the full catalog.
 * One or more protocols selected: show exactly their linked chakras, even if that's none —
 * never silently fall back to the full catalog, since that would make a protocol with no
 * chakra data look like every chakra applies to it.
 */
export function resolveScanChakras<T extends ChakraLike>(
  chakras: T[],
  protocolChakras: ChakraLike[],
  protocolIds: string[]
): T[] {
  if (protocolIds.length === 0) return chakras;
  const linkedIds = new Set(protocolChakras.map((c) => c.id));
  return chakras.filter((c) => linkedIds.has(c.id));
}
