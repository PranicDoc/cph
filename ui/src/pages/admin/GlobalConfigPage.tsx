import { useState } from "react";
import { versioned } from "../../ui-version";
import { useAdminGlobalConfig, useUpdateGlobalConfig } from "../../hooks/admin/useAdminGlobalConfig";
import { useCan } from "../../hooks/useMyPermissions";
import type { CreditConfigEntry } from "../../hooks/admin/useAdminGlobalConfig";
import { Pencil, Check, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

function ConfigCard({ entry }: { entry: CreditConfigEntry }) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(entry.config_value);
  const update = useUpdateGlobalConfig();
  const canEdit = useCan()("GLOBAL_CONFIG", "UPDATE");

  const save = async () => {
    await update.mutateAsync({ key: entry.config_key, value: draft });
    setEditing(false);
  };

  return (
    <div className="border border-border rounded-xl p-4">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0 flex-1">
          <p className="font-mono text-sm font-semibold text-text">{entry.config_key}</p>
          {entry.description && (
            <p className="text-xs text-text-light mt-0.5 leading-relaxed">{entry.description}</p>
          )}
        </div>
        {/* Action buttons */}
        <div className="shrink-0">
          {editing ? (
            <div className="flex gap-1">
              <Button variant="ghost" size="icon-sm" onClick={save} disabled={update.isPending}
                className="h-auto w-auto p-1.5 hover:bg-green-100 text-green-600">
                <Check className="w-4 h-4" />
              </Button>
              <Button variant="ghost" size="icon-sm"
                onClick={() => { setDraft(entry.config_value); setEditing(false); }}
                className="h-auto w-auto p-1.5 hover:bg-red-100 text-red-500 hover:text-red-500">
                <X className="w-4 h-4" />
              </Button>
            </div>
          ) : canEdit && (
            <Button variant="ghost" size="icon-sm" onClick={() => setEditing(true)}
              className="h-auto w-auto p-1.5 text-text-light">
              <Pencil className="w-4 h-4" />
            </Button>
          )}
        </div>
      </div>

      <div className="mt-3 flex items-center gap-3">
        {editing ? (
          <Input
            type="text"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            className="border border-primary px-3 py-1.5 font-mono w-28"
            autoFocus
          />
        ) : (
          <span className="font-mono text-sm bg-muted/50 px-3 py-1.5 rounded-lg">{entry.config_value}</span>
        )}
        <span className="text-xs text-text-light">{new Date(entry.updated_at).toLocaleDateString()}</span>
      </div>
    </div>
  );
}

function ClassicGlobalConfigPage() {
  const { data, isLoading, isError } = useAdminGlobalConfig();

  return (
    <div>
      <div className="mb-4 md:mb-6">
        <h1 className="text-xl md:text-2xl font-bold text-text">Global Credit Config</h1>
        <p className="text-sm text-text-light mt-1">Changes apply to all clinics. Cache expires in ~2 minutes.</p>
      </div>

      {isLoading && <p className="text-text-light">Loading…</p>}
      {isError && <p className="text-red-500">Failed to load config.</p>}

      {data && (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
          {data.map((entry) => (
            <ConfigCard key={entry.config_key} entry={entry} />
          ))}
        </div>
      )}
    </div>
  );
}

const GlobalConfigPage = versioned(ClassicGlobalConfigPage, () => import("../../ui-new/admin/NewGlobalConfigPage"));
export default GlobalConfigPage;
