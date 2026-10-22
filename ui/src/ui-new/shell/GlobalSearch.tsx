import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Search, User } from "lucide-react";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { usePatient } from "../../context/PatientContext";
import type { ShellNavItem } from "./NewShell";

type Result = { key: string; label: string; hint: string; icon: ShellNavItem["icon"] | typeof User; go: () => void; kind: "Pages" | "Patients" };

/** The app wraps every route in PatientProvider; isolated renders (tests) do not, and just lose patient results. */
function useOptionalPatient() {
  try {
    return usePatient();
  } catch {
    return null;
  }
}

/**
 * "Search or jump to": opens with the topbar button or Ctrl/Cmd + K. Finds pages of this app and
 * (for healers) patients from the list the app already loads, so it adds no new request.
 */
export function GlobalSearch({ items, variant }: { items: ShellNavItem[]; variant: "bar" | "icon" }) {
  const navigate = useNavigate();
  const ctx = useOptionalPatient();
  const patients = ctx?.patients;
  const selectPatient = ctx?.selectPatient ?? (() => {});
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const listRef = useRef<HTMLUListElement>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen((o) => !o);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  useEffect(() => {
    if (open) {
      setQuery("");
      setActive(0);
    }
  }, [open]);

  const results = useMemo<Result[]>(() => {
    const q = query.trim().toLowerCase();
    const close = () => setOpen(false);
    const pages: Result[] = items
      .filter((i) => !q || i.label.toLowerCase().includes(q))
      .map((i) => ({ key: `p-${i.path}`, label: i.label, hint: "Page", icon: i.icon, kind: "Pages" as const, go: () => { close(); navigate(i.path); } }));
    const people: Result[] = q
      ? (patients ?? [])
          .filter((p) => [p.fullName, p.firstName, p.lastName, p.contact].filter(Boolean).some((f) => String(f).toLowerCase().includes(q)))
          .slice(0, 8)
          .map((p) => ({
            key: `pt-${p.id}`,
            label: p.fullName,
            hint: p.healingCaseId ? `Open case${p.status ? ` · ${p.status}` : ""}` : "No case yet",
            icon: User,
            kind: "Patients" as const,
            go: () => {
              close();
              if (p.healingCaseId) {
                selectPatient(p.id);
                navigate(`/session/${p.healingCaseId}`);
              } else {
                navigate("/patients");
              }
            },
          }))
      : [];
    return [...pages, ...people];
  }, [items, patients, query, navigate, selectPatient]);

  useEffect(() => setActive(0), [query]);
  useEffect(() => {
    listRef.current?.querySelector<HTMLElement>('[aria-selected="true"]')?.scrollIntoView({ block: "nearest" });
  }, [active]);

  const onInputKey = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActive((a) => Math.min(a + 1, results.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActive((a) => Math.max(a - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      results[active]?.go();
    }
  };

  let lastKind = "";
  return (
    <>
      {variant === "bar" ? (
        <button type="button" className="hl-search" onClick={() => setOpen(true)} aria-label="Search or jump to">
          <Search className="size-[16px]" aria-hidden="true" />
          <span>Search or jump to</span>
          <kbd>Ctrl K</kbd>
        </button>
      ) : (
        <button type="button" className="hl-icon-btn" onClick={() => setOpen(true)} aria-label="Search">
          <Search className="size-[18px]" aria-hidden="true" />
        </button>
      )}
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent showCloseButton={false} className="hl-cmdk">
          <DialogTitle className="sr-only">Search</DialogTitle>
          <DialogDescription className="sr-only">Search pages and patients</DialogDescription>
          <div className="hl-cmdk-input">
            <Search className="size-[18px]" aria-hidden="true" />
            <input
              autoFocus
              role="combobox"
              aria-expanded="true"
              aria-controls="hl-cmdk-list"
              placeholder="Search pages and patients"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={onInputKey}
            />
          </div>
          <ul id="hl-cmdk-list" role="listbox" ref={listRef} className="hl-cmdk-list">
            {results.length === 0 && <li className="hl-cmdk-empty">Nothing matches "{query}".</li>}
            {results.map((r, i) => {
              const heading = r.kind !== lastKind ? r.kind : null;
              lastKind = r.kind;
              const Icon = r.icon;
              return (
                <li key={r.key} role="presentation">
                  {heading && <div className="hl-cmdk-head">{heading}</div>}
                  <button type="button" role="option" aria-selected={i === active} className="hl-cmdk-row" onMouseEnter={() => setActive(i)} onClick={r.go}>
                    <Icon className="size-[18px]" aria-hidden="true" />
                    <span className="truncate">{r.label}</span>
                    <span className="hl-cmdk-hint">{r.hint}</span>
                  </button>
                </li>
              );
            })}
          </ul>
        </DialogContent>
      </Dialog>
    </>
  );
}
