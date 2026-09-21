export interface ChakraLike {
  id: string;
}

/**
 * Which chakras a healing session's scan step should PRE-POPULATE as rows, given the full
 * chakra catalog and the chakras linked (protocol_chakra) to the session's selected
 * protocol(s).
 *
 * This is only a convenience prefill for the healer, never a restriction: the chakra picker
 * always offers the whole catalog so a healer can add, swap or remove any chakra. A protocol
 * with no linked chakra data (or no protocol yet) simply prefills nothing.
 */
export function resolvePrefillChakras<T extends ChakraLike>(
  catalog: T[],
  protocolChakras: ChakraLike[],
  protocolIds: string[]
): T[] {
  if (protocolIds.length === 0) return [];
  const linkedIds = new Set(protocolChakras.map((c) => c.id));
  return catalog.filter((c) => linkedIds.has(c.id));
}
