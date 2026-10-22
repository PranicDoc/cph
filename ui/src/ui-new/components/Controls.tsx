import type { ReactNode } from "react";
import { ChevronDown } from "lucide-react";

/** Mockup switch (40 x 24). Pair with a visible label or give it an aria-label. */
export function HlSwitch({
  checked,
  onChange,
  label,
  disabled,
}: {
  checked: boolean;
  onChange: (next: boolean) => void;
  label: string;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      className={`switch${checked ? " on" : ""}`}
      style={{ border: 0, padding: 0, cursor: disabled ? "not-allowed" : "pointer", opacity: disabled ? 0.55 : 1 }}
      onClick={() => onChange(!checked)}
    />
  );
}

/** Native select drawn like the mockup's select (chevron on the right). */
export function HlSelect({
  value,
  onChange,
  options,
  id,
  label,
}: {
  value: string;
  onChange: (v: string) => void;
  options: { value: string; label: string }[];
  id?: string;
  label?: string;
}) {
  return (
    <div style={{ position: "relative" }}>
      <select
        id={id}
        aria-label={label}
        className="input"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        style={{ appearance: "none", WebkitAppearance: "none", paddingRight: 34, cursor: "pointer" }}
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      <ChevronDown className="ico" aria-hidden="true" style={{ position: "absolute", right: 10, top: 11, pointerEvents: "none", color: "var(--muted)" }} />
    </div>
  );
}

/** Label above, helper below. */
export function HlField({ label, htmlFor, help, error, children }: { label: string; htmlFor?: string; help?: ReactNode; error?: ReactNode; children: ReactNode }) {
  return (
    <div>
      <label className="lbl" htmlFor={htmlFor}>
        {label}
      </label>
      {children}
      {help && <div className="help">{help}</div>}
      {error && <div className="err">{error}</div>}
    </div>
  );
}
