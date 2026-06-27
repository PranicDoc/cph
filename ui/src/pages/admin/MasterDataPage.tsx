import { useState } from "react";
import { Plus, Pencil, Trash2, X, Lock } from "lucide-react";
import { useAuth } from "../../context/AuthContext";
import {
  usePranaColors,
  useChakras,
  useProtocolMappings,
  useCreateColor,
  useUpdateColor,
  useDeleteColor,
  useCreateChakra,
  useUpdateChakra,
  useDeleteChakra,
  useCreateMapping,
  useDeleteMapping,
  type PranaColor,
  type Chakra,
  type PranaColorPayload,
  type ChakraPayload,
} from "../../hooks/admin/useMasterData";

// ── Shared helpers ────────────────────────────────────────────────────────────

function SourceBadge({ clinicId }: { clinicId?: string | null }) {
  if (!clinicId) {
    return (
      <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-gray-100 text-gray-500">
        System
      </span>
    );
  }
  return (
    <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-green-100 text-green-700">
      Clinic
    </span>
  );
}

function ColorSwatch({ hex }: { hex?: string }) {
  return (
    <span
      className="inline-block w-5 h-5 rounded-full border border-border flex-shrink-0"
      style={{ backgroundColor: hex ?? "#ccc" }}
    />
  );
}

// ── Shared modal shell ────────────────────────────────────────────────────────

function ModalShell({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: React.ReactNode;
}) {
  return (
    <div className="fixed inset-0 bg-black/40 flex items-end sm:items-center justify-center z-50 p-0 sm:p-4">
      <div className="bg-white w-full sm:max-w-md rounded-t-2xl sm:rounded-2xl shadow-xl p-5 relative max-h-[90vh] overflow-y-auto">
        <button
          type="button"
          onClick={onClose}
          className="absolute top-4 right-4 text-text-light hover:text-text"
        >
          <X className="w-5 h-5" />
        </button>
        <h2 className="text-lg font-bold text-text mb-4">{title}</h2>
        {children}
      </div>
    </div>
  );
}

function inputCls() {
  return "w-full border border-border rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-1 focus:ring-primary";
}

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label className="block text-xs text-text-light mb-1">{label}</label>
      {children}
    </div>
  );
}

// ── Prana Colors Tab ──────────────────────────────────────────────────────────

const EMPTY_COLOR: PranaColorPayload = {
  name: "",
  code: "",
  colorHex: "#ffffff",
  properties: "",
  clinicId: null,
};

function ColorModal({
  initial,
  onClose,
}: {
  initial?: PranaColor;
  onClose: () => void;
}) {
  const [form, setForm] = useState<PranaColorPayload>(
    initial
      ? {
          name: initial.name,
          code: initial.code,
          colorHex: initial.colorHex ?? "#ffffff",
          properties: initial.properties ?? "",
          clinicId: initial.clinicId,
        }
      : EMPTY_COLOR
  );
  const [error, setError] = useState("");
  const create = useCreateColor();
  const update = useUpdateColor();

  const set =
    (key: keyof PranaColorPayload) =>
    (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
      setForm((f) => ({ ...f, [key]: e.target.value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    try {
      if (initial) {
        await update.mutateAsync({ id: initial.id, payload: form });
      } else {
        await create.mutateAsync(form);
      }
      onClose();
    } catch {
      setError("Failed to save. Please try again.");
    }
  };

  const isPending = create.isPending || update.isPending;

  return (
    <ModalShell title={initial ? "Edit Prana Color" : "Add Prana Color"} onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-3">
        <Field label="Name">
          <input type="text" value={form.name} onChange={set("name")} className={inputCls()} required />
        </Field>
        <Field label="Code (e.g. VIOLET)">
          <input
            type="text"
            value={form.code}
            onChange={set("code")}
            onBlur={() => setForm((f) => ({ ...f, code: f.code.toUpperCase() }))}
            className={inputCls()}
            required
            disabled={!!initial}
          />
        </Field>
        <Field label="Color">
          <div className="flex items-center gap-2">
            <input
              type="color"
              value={form.colorHex}
              onChange={set("colorHex")}
              className="w-10 h-10 rounded cursor-pointer border border-border p-0.5"
            />
            <input
              type="text"
              value={form.colorHex}
              onChange={set("colorHex")}
              maxLength={7}
              placeholder="#ffffff"
              className={inputCls()}
            />
          </div>
        </Field>
        <Field label="Healing Properties">
          <textarea
            value={form.properties ?? ""}
            onChange={set("properties")}
            rows={4}
            className={inputCls()}
            placeholder="Describe the healing properties of this color…"
          />
        </Field>
        {error && <p className="text-red-500 text-sm">{error}</p>}
        <div className="flex gap-2 pt-1">
          <button
            type="submit"
            disabled={isPending}
            className="flex-1 py-2.5 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark disabled:opacity-60"
          >
            {isPending ? "Saving…" : initial ? "Save Changes" : "Add Color"}
          </button>
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2.5 border border-border rounded-lg text-sm text-text hover:bg-surface"
          >
            Cancel
          </button>
        </div>
      </form>
    </ModalShell>
  );
}

function PranaColorsTab({ isSuperAdmin }: { isSuperAdmin: boolean }) {
  const { data, isLoading, isError } = usePranaColors();
  const deleteColor = useDeleteColor();
  const [modal, setModal] = useState<PranaColor | null | "new">(null);

  const canModify = (item: PranaColor) => isSuperAdmin || !!item.clinicId;

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <p className="text-sm text-text-light">
          System colors are seeded globally. Clinic colors are custom additions.
        </p>
        <button
          onClick={() => setModal("new")}
          className="flex items-center gap-2 px-3 py-2 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark shrink-0"
        >
          <Plus className="w-4 h-4" />
          <span className="hidden sm:inline">Add Color</span>
        </button>
      </div>

      {isLoading && <p className="text-text-light text-sm">Loading…</p>}
      {isError && <p className="text-red-500 text-sm">Failed to load colors.</p>}

      {data && (
        <>
          {/* Desktop table */}
          <div className="hidden md:block rounded-xl border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-surface text-text-light uppercase text-xs tracking-wide">
                <tr>
                  <th className="px-4 py-3 text-left w-8" />
                  <th className="px-4 py-3 text-left">Name</th>
                  <th className="px-4 py-3 text-left">Code</th>
                  <th className="px-4 py-3 text-left">Properties</th>
                  <th className="px-4 py-3 text-left">Source</th>
                  <th className="px-4 py-3 w-20" />
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.map((item) => (
                  <tr key={item.id} className="hover:bg-surface/50">
                    <td className="px-4 py-3">
                      <ColorSwatch hex={item.colorHex} />
                    </td>
                    <td className="px-4 py-3 font-medium text-text">{item.name}</td>
                    <td className="px-4 py-3 font-mono text-xs text-text-light">{item.code}</td>
                    <td className="px-4 py-3 text-text-light max-w-xs truncate">
                      {item.properties ? item.properties.slice(0, 60) + (item.properties.length > 60 ? "…" : "") : "—"}
                    </td>
                    <td className="px-4 py-3">
                      <SourceBadge clinicId={item.clinicId} />
                    </td>
                    <td className="px-4 py-3">
                      {canModify(item) ? (
                        <div className="flex gap-1 justify-end">
                          <button
                            onClick={() => setModal(item)}
                            className="p-1.5 rounded hover:bg-surface text-text-light"
                          >
                            <Pencil className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => deleteColor.mutate(item.id)}
                            disabled={deleteColor.isPending}
                            className="p-1.5 rounded hover:bg-red-50 text-red-400 disabled:opacity-50"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      ) : (
                        <div className="flex justify-end">
                          <Lock className="w-4 h-4 text-text-light/40" />
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile cards */}
          <div className="md:hidden space-y-3">
            {data.map((item) => (
              <div key={item.id} className="border border-border rounded-xl p-4">
                <div className="flex items-start justify-between mb-2">
                  <div className="flex items-center gap-2 min-w-0">
                    <ColorSwatch hex={item.colorHex} />
                    <span className="font-semibold text-text truncate">{item.name}</span>
                    <SourceBadge clinicId={item.clinicId} />
                  </div>
                  {canModify(item) ? (
                    <div className="flex gap-1 flex-shrink-0">
                      <button onClick={() => setModal(item)} className="p-1.5 rounded hover:bg-surface text-text-light">
                        <Pencil className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => deleteColor.mutate(item.id)}
                        disabled={deleteColor.isPending}
                        className="p-1.5 rounded hover:bg-red-50 text-red-400 disabled:opacity-50"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  ) : (
                    <Lock className="w-4 h-4 text-text-light/40 mt-1" />
                  )}
                </div>
                {item.properties && (
                  <p className="text-xs text-text-light mt-1 line-clamp-2">{item.properties}</p>
                )}
              </div>
            ))}
          </div>
        </>
      )}

      {modal !== null && (
        <ColorModal initial={modal === "new" ? undefined : modal} onClose={() => setModal(null)} />
      )}
    </div>
  );
}

// ── Chakras Tab ───────────────────────────────────────────────────────────────

const EMPTY_CHAKRA: ChakraPayload = {
  name: "",
  code: "",
  category: "major",
  location: "",
  clinicId: null,
};

function ChakraModal({
  initial,
  onClose,
}: {
  initial?: Chakra;
  onClose: () => void;
}) {
  const [form, setForm] = useState<ChakraPayload>(
    initial
      ? {
          name: initial.name,
          code: initial.code,
          category: initial.category ?? "major",
          location: initial.location ?? "",
          clinicId: initial.clinicId,
        }
      : EMPTY_CHAKRA
  );
  const [error, setError] = useState("");
  const create = useCreateChakra();
  const update = useUpdateChakra();

  const set =
    (key: keyof ChakraPayload) =>
    (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
      setForm((f) => ({ ...f, [key]: e.target.value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    try {
      if (initial) {
        await update.mutateAsync({ id: initial.id, payload: form });
      } else {
        await create.mutateAsync(form);
      }
      onClose();
    } catch {
      setError("Failed to save. Please try again.");
    }
  };

  const isPending = create.isPending || update.isPending;

  return (
    <ModalShell title={initial ? "Edit Chakra" : "Add Chakra"} onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-3">
        <Field label="Name">
          <input type="text" value={form.name} onChange={set("name")} className={inputCls()} required />
        </Field>
        <Field label="Code (e.g. CROWN)">
          <input
            type="text"
            value={form.code}
            onChange={set("code")}
            onBlur={() => setForm((f) => ({ ...f, code: f.code.toUpperCase() }))}
            className={inputCls()}
            required
            disabled={!!initial}
          />
        </Field>
        <Field label="Category">
          <select value={form.category ?? "major"} onChange={set("category")} className={inputCls()}>
            <option value="major">Major</option>
            <option value="minor">Minor</option>
          </select>
        </Field>
        <Field label="Location">
          <input type="text" value={form.location ?? ""} onChange={set("location")} className={inputCls()} placeholder="e.g. Top of the head" />
        </Field>
        {error && <p className="text-red-500 text-sm">{error}</p>}
        <div className="flex gap-2 pt-1">
          <button
            type="submit"
            disabled={isPending}
            className="flex-1 py-2.5 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark disabled:opacity-60"
          >
            {isPending ? "Saving…" : initial ? "Save Changes" : "Add Chakra"}
          </button>
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2.5 border border-border rounded-lg text-sm text-text hover:bg-surface"
          >
            Cancel
          </button>
        </div>
      </form>
    </ModalShell>
  );
}

function ChakrasTab({ isSuperAdmin }: { isSuperAdmin: boolean }) {
  const { data, isLoading, isError } = useChakras();
  const deleteChakra = useDeleteChakra();
  const [modal, setModal] = useState<Chakra | null | "new">(null);

  const canModify = (item: Chakra) => isSuperAdmin || !!item.clinicId;

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <p className="text-sm text-text-light">
          System chakras (26 seeded) are global. Add custom chakras for your clinic.
        </p>
        <button
          onClick={() => setModal("new")}
          className="flex items-center gap-2 px-3 py-2 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark shrink-0"
        >
          <Plus className="w-4 h-4" />
          <span className="hidden sm:inline">Add Chakra</span>
        </button>
      </div>

      {isLoading && <p className="text-text-light text-sm">Loading…</p>}
      {isError && <p className="text-red-500 text-sm">Failed to load chakras.</p>}

      {data && (
        <>
          {/* Desktop */}
          <div className="hidden md:block rounded-xl border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-surface text-text-light uppercase text-xs tracking-wide">
                <tr>
                  <th className="px-4 py-3 text-left">Name</th>
                  <th className="px-4 py-3 text-left">Code</th>
                  <th className="px-4 py-3 text-left">Category</th>
                  <th className="px-4 py-3 text-left">Location</th>
                  <th className="px-4 py-3 text-left">Source</th>
                  <th className="px-4 py-3 w-20" />
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.map((item) => (
                  <tr key={item.id} className="hover:bg-surface/50">
                    <td className="px-4 py-3 font-medium text-text">{item.name}</td>
                    <td className="px-4 py-3 font-mono text-xs text-text-light">{item.code}</td>
                    <td className="px-4 py-3">
                      <span
                        className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                          item.category === "major"
                            ? "bg-blue-100 text-blue-700"
                            : "bg-gray-100 text-gray-600"
                        }`}
                      >
                        {item.category ?? "—"}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-text-light text-xs max-w-xs truncate">
                      {item.location ?? "—"}
                    </td>
                    <td className="px-4 py-3">
                      <SourceBadge clinicId={item.clinicId} />
                    </td>
                    <td className="px-4 py-3">
                      {canModify(item) ? (
                        <div className="flex gap-1 justify-end">
                          <button onClick={() => setModal(item)} className="p-1.5 rounded hover:bg-surface text-text-light">
                            <Pencil className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => deleteChakra.mutate(item.id)}
                            disabled={deleteChakra.isPending}
                            className="p-1.5 rounded hover:bg-red-50 text-red-400 disabled:opacity-50"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      ) : (
                        <div className="flex justify-end">
                          <Lock className="w-4 h-4 text-text-light/40" />
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile */}
          <div className="md:hidden space-y-3">
            {data.map((item) => (
              <div key={item.id} className="border border-border rounded-xl p-4">
                <div className="flex items-start justify-between">
                  <div>
                    <span className="font-semibold text-text">{item.name}</span>
                    <div className="flex items-center gap-2 mt-1">
                      <span
                        className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                          item.category === "major" ? "bg-blue-100 text-blue-700" : "bg-gray-100 text-gray-600"
                        }`}
                      >
                        {item.category ?? "—"}
                      </span>
                      <SourceBadge clinicId={item.clinicId} />
                    </div>
                  </div>
                  {canModify(item) ? (
                    <div className="flex gap-1">
                      <button onClick={() => setModal(item)} className="p-1.5 rounded hover:bg-surface text-text-light">
                        <Pencil className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => deleteChakra.mutate(item.id)}
                        disabled={deleteChakra.isPending}
                        className="p-1.5 rounded hover:bg-red-50 text-red-400 disabled:opacity-50"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  ) : (
                    <Lock className="w-4 h-4 text-text-light/40 mt-1" />
                  )}
                </div>
                {item.location && <p className="text-xs text-text-light mt-1">{item.location}</p>}
              </div>
            ))}
          </div>
        </>
      )}

      {modal !== null && (
        <ChakraModal initial={modal === "new" ? undefined : modal} onClose={() => setModal(null)} />
      )}
    </div>
  );
}

// ── Protocol Mappings Tab ─────────────────────────────────────────────────────

function MappingModal({ onClose, isSuperAdmin }: { onClose: () => void; isSuperAdmin: boolean }) {
  const { data: colors } = usePranaColors();
  const [protocolName, setProtocolName] = useState("");
  const [chakraName, setChakraName] = useState("");
  const [usageType, setUsageType] = useState<"CLEANSING" | "ENERGISING">("CLEANSING");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [error, setError] = useState("");
  const create = useCreateMapping();

  const toggle = (id: string) =>
    setSelectedIds((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedIds.size === 0) { setError("Select at least one color."); return; }
    setError("");
    try {
      await create.mutateAsync({
        protocolName,
        colorIds: Array.from(selectedIds),
        usageType,
        clinicId: null,
        chakraName
      });
      onClose();
    } catch {
      setError("Failed to save. Please try again.");
    }
  };

  return (
    <ModalShell title="Add Protocol Mapping" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <Field label="Protocol Name">
          <input
            type="text"
            value={protocolName}
            onChange={(e) => setProtocolName(e.target.value)}
            className={inputCls()}
            placeholder="e.g. STRESS_RELIEF"
            required
          />
        </Field>

        <Field label="Chakra Name">
          <input
            type="text"
            value={chakraName}
            onChange={(e) => setChakraName(e.target.value)}
            className={inputCls()}
            placeholder="e.g. Crown Chakra"
            required
          />
        </Field>

        <Field label="Usage Type">
          <div className="flex gap-2">
            {(["CLEANSING", "ENERGISING"] as const).map((type) => (
              <button
                key={type}
                type="button"
                onClick={() => setUsageType(type)}
                className={`flex-1 py-2 rounded-lg text-sm font-medium border transition-colors ${
                  usageType === type
                    ? type === "CLEANSING"
                      ? "bg-green-100 border-green-400 text-green-700"
                      : "bg-orange-100 border-orange-400 text-orange-700"
                    : "border-border text-text-light hover:bg-surface"
                }`}
              >
                {type}
              </button>
            ))}
          </div>
        </Field>

        <div>
          <label className="block text-xs text-text-light mb-2">
            Prana Colors
            {selectedIds.size > 0 && (
              <span className="ml-1.5 text-primary font-medium">({selectedIds.size} selected)</span>
            )}
          </label>
          <div className="flex flex-wrap gap-2 max-h-52 overflow-y-auto pr-1">
            {colors?.map((c) => {
              const selected = selectedIds.has(c.id);
              return (
                <button
                  key={c.id}
                  type="button"
                  onClick={() => toggle(c.id)}
                  className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-full text-xs font-medium border transition-all ${
                    selected
                      ? "border-primary bg-primary/10 text-primary shadow-sm"
                      : "border-border text-text-light hover:border-primary/50 hover:text-text"
                  }`}
                >
                  <span
                    className="w-3 h-3 rounded-full flex-shrink-0 border border-black/10"
                    style={{ backgroundColor: c.colorHex ?? "#ccc" }}
                  />
                  {c.name}
                  {selected && <span className="text-primary">✓</span>}
                </button>
              );
            })}
          </div>
        </div>

        {isSuperAdmin && (
          <p className="text-xs text-text-light">
            Mappings will be created as system-level (no clinic restriction).
          </p>
        )}
        {error && <p className="text-red-500 text-sm">{error}</p>}
        <div className="flex gap-2 pt-1">
          <button
            type="submit"
            disabled={create.isPending}
            className="flex-1 py-2.5 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark disabled:opacity-60"
          >
            {create.isPending
              ? "Saving…"
              : selectedIds.size > 1
              ? `Add ${selectedIds.size} Mappings`
              : "Add Mapping"}
          </button>
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2.5 border border-border rounded-lg text-sm text-text hover:bg-surface"
          >
            Cancel
          </button>
        </div>
      </form>
    </ModalShell>
  );
}

function ProtocolMappingsTab({ isSuperAdmin }: { isSuperAdmin: boolean }) {
  const { data: mappings, isLoading, isError } = useProtocolMappings();
  const { data: colors } = usePranaColors();
  const deleteMapping = useDeleteMapping();
  const [showModal, setShowModal] = useState(false);

  const colorById = (id: string) => colors?.find((c) => c.id === id);
  const canDelete = (m: { clinicId?: string | null }) => isSuperAdmin || !!m.clinicId;

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <p className="text-sm text-text-light">
          Maps a protocol name to cleansing or energising prana colors.
        </p>
        <button
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 px-3 py-2 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark shrink-0"
        >
          <Plus className="w-4 h-4" />
          <span className="hidden sm:inline">Add Mapping</span>
        </button>
      </div>

      {isLoading && <p className="text-text-light text-sm">Loading…</p>}
      {isError && <p className="text-red-500 text-sm">Failed to load mappings.</p>}

      {mappings && (
        <>
          {/* Desktop */}
          <div className="hidden md:block rounded-xl border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-surface text-text-light uppercase text-xs tracking-wide">
                <tr>
                  <th className="px-4 py-3 text-left">Protocol</th>
                  <th className="px-4 py-3 text-left">Color</th>
                  <th className="px-4 py-3 text-left">Usage</th>
                  <th className="px-4 py-3 text-left">Source</th>
                  <th className="px-4 py-3 w-12" />
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {mappings.map((m) => {
                  const color = colorById(m.colorId);
                  return (
                    <tr key={m.id} className="hover:bg-surface/50">
                      <td className="px-4 py-3 font-medium text-text">{m.protocolName}</td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <ColorSwatch hex={color?.colorHex} />
                          <span className="text-text-light">{color?.name ?? m.colorId}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                            m.usageType === "CLEANSING"
                              ? "bg-green-100 text-green-700"
                              : "bg-orange-100 text-orange-700"
                          }`}
                        >
                          {m.usageType}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <SourceBadge clinicId={m.clinicId} />
                      </td>
                      <td className="px-4 py-3">
                        {canDelete(m) ? (
                          <button
                            onClick={() => deleteMapping.mutate(m.id)}
                            disabled={deleteMapping.isPending}
                            className="p-1.5 rounded hover:bg-red-50 text-red-400 disabled:opacity-50"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        ) : (
                          <Lock className="w-4 h-4 text-text-light/40" />
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Mobile */}
          <div className="md:hidden space-y-3">
            {mappings.map((m) => {
              const color = colorById(m.colorId);
              return (
                <div key={m.id} className="border border-border rounded-xl p-4">
                  <div className="flex items-start justify-between">
                    <div>
                      <p className="font-semibold text-text">{m.protocolName}</p>
                      <div className="flex items-center gap-2 mt-1">
                        <ColorSwatch hex={color?.colorHex} />
                        <span className="text-xs text-text-light">{color?.name ?? "—"}</span>
                        <span
                          className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                            m.usageType === "CLEANSING"
                              ? "bg-green-100 text-green-700"
                              : "bg-orange-100 text-orange-700"
                          }`}
                        >
                          {m.usageType}
                        </span>
                      </div>
                    </div>
                    {canDelete(m) ? (
                      <button
                        onClick={() => deleteMapping.mutate(m.id)}
                        disabled={deleteMapping.isPending}
                        className="p-1.5 rounded hover:bg-red-50 text-red-400 disabled:opacity-50"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    ) : (
                      <Lock className="w-4 h-4 text-text-light/40 mt-1" />
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </>
      )}

      {showModal && (
        <MappingModal onClose={() => setShowModal(false)} isSuperAdmin={isSuperAdmin} />
      )}
    </div>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────

type Tab = "colors" | "chakras" | "mappings";

export default function MasterDataPage() {
  const { user } = useAuth();
  const isSuperAdmin = user?.role === "SUPADM";
  const [activeTab, setActiveTab] = useState<Tab>("colors");

  const tabs: { key: Tab; label: string }[] = [
    { key: "colors", label: "Prana Colors" },
    { key: "chakras", label: "Chakras" },
    { key: "mappings", label: "Protocol Mappings" },
  ];

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-xl md:text-2xl font-bold text-text">Master Data</h1>
        <p className="text-sm text-text-light mt-0.5">
          Manage prana colors, chakras, and protocol color mappings.
        </p>
      </div>

      {/* Tab bar */}
      <div className="flex border-b border-border mb-6 overflow-x-auto">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => setActiveTab(tab.key)}
            className={`px-4 py-2.5 text-sm font-medium whitespace-nowrap border-b-2 transition-colors -mb-px ${
              activeTab === tab.key
                ? "border-primary text-primary"
                : "border-transparent text-text-light hover:text-text"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === "colors" && <PranaColorsTab isSuperAdmin={isSuperAdmin} />}
      {activeTab === "chakras" && <ChakrasTab isSuperAdmin={isSuperAdmin} />}
      {activeTab === "mappings" && <ProtocolMappingsTab isSuperAdmin={isSuperAdmin} />}
    </div>
  );
}
