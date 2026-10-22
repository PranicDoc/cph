import { useMemo, useState } from "react";
import { Search } from "lucide-react";
import { Checkbox } from "@/components/ui/checkbox";
import type { GrantMap } from "@/components/admin/roles/PermissionToggleGrid";
import type { RbacCatalogResource } from "@/hooks/admin/useRbacCatalog";

/** Turn one permission on or off; a resource with nothing granted is dropped, locked verbs ride along. */
export function toggleVerb(grants: GrantMap, entry: RbacCatalogResource, verb: string, on: boolean): GrantMap {
  const locked = entry.verbs.filter((v) => v.locked).map((v) => v.value);
  const current = new Set(grants[entry.resource] ?? []);
  if (on) {
    current.add(verb);
    locked.forEach((l) => current.add(l));
  } else {
    current.delete(verb);
  }
  const next = { ...grants };
  const unlocked = [...current].filter((v) => !locked.includes(v));
  if (unlocked.length === 0 && !on) delete next[entry.resource];
  else next[entry.resource] = entry.verbs.map((v) => v.value).filter((v) => current.has(v));
  return next;
}

/** Grant every permission of a resource, or remove the resource. */
export function setAllVerbs(grants: GrantMap, entry: RbacCatalogResource, on: boolean): GrantMap {
  const next = { ...grants };
  if (on) next[entry.resource] = entry.verbs.map((v) => v.value);
  else delete next[entry.resource];
  return next;
}

interface Props {
  catalog: RbacCatalogResource[];
  value: GrantMap;
  onChange: (next: GrantMap) => void;
  disabled?: boolean;
}

/**
 * Many-permissions picker from the RoleFormMany mockup: a group list (one group per resource,
 * with n/N counts) and the selected group's permissions in two columns. Same GrantMap data as
 * the classic toggle grid.
 */
export function PermissionsPicker({ catalog, value, onChange, disabled }: Props) {
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(catalog[0]?.resource ?? "");
  const q = query.trim().toLowerCase();

  const groups = useMemo(
    () =>
      catalog.filter(
        (e) => !q || e.label.toLowerCase().includes(q) || e.verbs.some((v) => `${v.label} ${v.description}`.toLowerCase().includes(q)),
      ),
    [catalog, q],
  );
  const total = catalog.reduce((n, e) => n + e.verbs.length, 0);
  const selected = Object.values(value).reduce((n, v) => n + v.length, 0);
  const entry = groups.find((g) => g.resource === active) ?? groups[0];
  const granted = new Set(entry ? value[entry.resource] ?? [] : []);
  const verbs = entry ? entry.verbs.filter((v) => !q || entry.label.toLowerCase().includes(q) || `${v.label} ${v.description}`.toLowerCase().includes(q)) : [];

  const selectAll = () => onChange(Object.fromEntries(catalog.map((e) => [e.resource, e.verbs.map((v) => v.value)])));

  return (
    <div className="stack" style={{ gap: 14 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
        <div style={{ position: "relative", flex: "1 1 260px", maxWidth: 380 }}>
          <Search className="ico" aria-hidden="true" style={{ position: "absolute", left: 12, top: 11, color: "var(--muted)" }} />
          <input className="input" style={{ paddingLeft: 38 }} placeholder="Search permissions" aria-label="Search permissions" value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
        <span className="small muted num">{selected} of {total} selected</span>
        <div style={{ marginLeft: "auto", display: "flex", gap: 4 }}>
          <button type="button" className="btn btn-ghost" style={{ height: 34 }} disabled={disabled} onClick={selectAll}>Select all</button>
          <button type="button" className="btn btn-ghost" style={{ height: 34 }} disabled={disabled} onClick={() => onChange({})}>Clear</button>
        </div>
      </div>

      {groups.length === 0 ? (
        <p className="muted">No permissions match "{query}".</p>
      ) : (
        <div className="perm-grid">
          <nav className="perm-nav" aria-label="Permission groups">
            {groups.map((g) => {
              const n = (value[g.resource] ?? []).length;
              const on = entry?.resource === g.resource;
              return (
                <button
                  key={g.resource}
                  type="button"
                  className={`pnav${on ? " on" : ""}`}
                  aria-current={on ? "true" : undefined}
                  onClick={() => setActive(g.resource)}
                  style={{ border: 0, background: on ? undefined : "transparent", cursor: "pointer", font: "inherit", textAlign: "left" }}
                >
                  <span style={{ overflow: "hidden", textOverflow: "ellipsis" }}>{g.label}</span>
                  <span className={`badge${n ? "" : " off"}`} style={{ height: 22 }}>{n}/{g.verbs.length}</span>
                </button>
              );
            })}
          </nav>

          {entry && (
            <div>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, marginBottom: 4 }}>
                <h3>{entry.label}</h3>
                <button
                  type="button"
                  className="btn btn-ghost"
                  style={{ height: 34 }}
                  disabled={disabled}
                  onClick={() => onChange(setAllVerbs(value, entry, granted.size < entry.verbs.length))}
                >
                  {granted.size < entry.verbs.length ? "Select all in group" : "Clear group"}
                </button>
              </div>
              <div className="perm-items">
                {verbs.map((v) => {
                  const checked = granted.has(v.value);
                  const id = `perm-${entry.resource}-${v.value}`;
                  return (
                    <label key={v.value} htmlFor={id} className="pitem" style={{ cursor: v.locked ? "default" : "pointer" }}>
                      <Checkbox
                        id={id}
                        checked={checked}
                        disabled={disabled || (v.locked && checked)}
                        onCheckedChange={(c) => onChange(toggleVerb(value, entry, v.value, c === true))}
                        style={{ marginTop: 2 }}
                      />
                      <span>
                        <span style={{ display: "block", fontWeight: 600 }}>{v.label}{v.locked && <span className="small muted"> · always on</span>}</span>
                        {v.description && <span className="small muted">{v.description}</span>}
                      </span>
                    </label>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
