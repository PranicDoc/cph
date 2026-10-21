import { useState } from "react";
import { Check, Pencil, X } from "lucide-react";
import { useAdminGlobalConfig, useUpdateGlobalConfig } from "../../hooks/admin/useAdminGlobalConfig";
import type { CreditConfigEntry } from "../../hooks/admin/useAdminGlobalConfig";
import { shortDate } from "../lib/format";

function ConfigRow({ entry }: { entry: CreditConfigEntry }) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(entry.config_value);
  const update = useUpdateGlobalConfig();
  const save = async () => {
    await update.mutateAsync({ key: entry.config_key, value: draft });
    setEditing(false);
  };
  const cancel = () => {
    setDraft(entry.config_value);
    setEditing(false);
  };

  return (
    <div style={{ display: "flex", alignItems: "center", gap: 16, padding: "14px 0", borderBottom: "1px solid var(--hair)", flexWrap: "wrap" }}>
      <div style={{ flex: "1 1 260px", minWidth: 0 }}>
        <div className="name" style={{ fontFamily: "ui-monospace, monospace", fontSize: 13.5 }}>{entry.config_key}</div>
        {entry.description && <div className="small muted" style={{ marginTop: 2 }}>{entry.description}</div>}
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
        {editing ? (
          <input className="input focus num" style={{ width: 120 }} value={draft} autoFocus aria-label={`Value for ${entry.config_key}`} onChange={(e) => setDraft(e.target.value)} />
        ) : (
          <span className="badge num" style={{ height: 32, padding: "0 14px", fontSize: 14 }}>{entry.config_value}</span>
        )}
        <span className="small muted num" style={{ minWidth: 86 }}>{shortDate(entry.updated_at)}</span>
        {editing ? (
          <div style={{ display: "flex", gap: 2 }}>
            <button className="icon-btn" type="button" aria-label={`Save ${entry.config_key}`} disabled={update.isPending} onClick={save}>
              <Check className="ico" />
            </button>
            <button className="icon-btn danger" type="button" aria-label={`Cancel editing ${entry.config_key}`} onClick={cancel}>
              <X className="ico" />
            </button>
          </div>
        ) : (
          <button className="icon-btn" type="button" aria-label={`Edit ${entry.config_key}`} onClick={() => setEditing(true)}>
            <Pencil className="ico" />
          </button>
        )}
      </div>
    </div>
  );
}

/** New-view Global config: every setting is still edited and saved on its own row, as in the classic page. */
export default function NewGlobalConfigPage() {
  const { data, isLoading, isError } = useAdminGlobalConfig();
  return (
    <div className="hlx">
      <div className="page-head">
        <div>
          <h1>Global credit config</h1>
          <p className="muted" style={{ marginTop: 6 }}>Changes apply to all clinics. Cache expires in about 2 minutes.</p>
        </div>
      </div>
      {isLoading && <p className="muted">Loading…</p>}
      {isError && <p className="err">Failed to load config.</p>}
      {data && (
        <div className="surface card" style={{ paddingTop: 6, paddingBottom: 6 }}>
          {data.map((entry) => (
            <ConfigRow key={entry.config_key} entry={entry} />
          ))}
        </div>
      )}
    </div>
  );
}
