import { useEffect, useState } from "react";

/**
 * Search box state for a paged list: `search` is what the input shows, `q` is the trimmed text
 * once typing has paused, and the page returns to the first one whenever `q` changes.
 */
export function useDebouncedSearch(delayMs = 300) {
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);

  useEffect(() => {
    const timer = setTimeout(() => {
      setQ(search.trim());
      setPage(0);
    }, delayMs);
    return () => clearTimeout(timer);
  }, [search, delayMs]);

  return { search, setSearch, q, page, setPage };
}
