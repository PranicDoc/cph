import type { ColorSelection } from "../../components/closure/ColorPropertySelector";

interface ColorChipPickerProps {
  label: string;
  value: ColorSelection[];
  colors: string[];
  getProperties: (color: string) => string[] | Promise<string[]>;
  getColorHex?: (color: string) => string | undefined;
  loading?: boolean;
  onChange: (value: ColorSelection[]) => void;
}

function Swatch({ hex }: { hex?: string }) {
  if (!hex) return null;
  return <span className="hl-swatch" style={{ backgroundColor: hex }} aria-hidden="true" />;
}

/**
 * New-view version of ColorPropertySelector: the same data (colours, each with its selected
 * properties) as chips. Pick colours in the first row; each picked colour gets a group of
 * property toggles underneath.
 */
export function ColorChipPicker({ label, value, colors, getProperties, getColorHex, loading, onChange }: ColorChipPickerProps) {
  // A picked colour stays listed even when it is outside this panel's category (e.g. pre-filled
  // from a protocol), so it can still be seen and un-picked.
  const options = [...colors, ...value.map((v) => v.color).filter((c) => !colors.includes(c))];
  const toggleColor = (color: string) => {
    const has = value.some((v) => v.color === color);
    onChange(has ? value.filter((v) => v.color !== color) : [...value, { color, properties: [] }]);
  };
  const toggleProperty = (color: string, property: string) => {
    onChange(
      value.map((v) =>
        v.color !== color
          ? v
          : {
              ...v,
              properties: v.properties.includes(property)
                ? v.properties.filter((p) => p !== property)
                : [...v.properties, property],
            }
      )
    );
  };

  return (
    <div className="hl-cpanel" role="group" aria-label={label}>
      <div className="hl-cpanel-head">
        <span>{label}</span>
        <span className="hl-cpanel-count">{value.length} selected</span>
      </div>
      {loading ? (
        <p className="hl-cpanel-empty">Loading colors…</p>
      ) : options.length === 0 ? (
        <p className="hl-cpanel-empty">No colors found.</p>
      ) : (
        <div className="hl-chips hl-chips-scroll">
          {options.map((c) => {
            const on = value.some((v) => v.color === c);
            return (
              <button
                key={c}
                type="button"
                aria-pressed={on}
                className="hl-chip hl-chip-toggle"
                data-on={on}
                onClick={() => toggleColor(c)}
              >
                <Swatch hex={getColorHex?.(c)} />
                {c}
              </button>
            );
          })}
        </div>
      )}
      {value.map((sel) => {
        const props = (getProperties(sel.color) as string[]) ?? [];
        return (
          <div key={sel.color} className="hl-cgroup">
            <div className="hl-cgroup-head">
              <Swatch hex={getColorHex?.(sel.color)} />
              <strong>{sel.color}</strong>
              <span className="hl-cpanel-count">
                {sel.properties.length} of {props.length} properties
              </span>
            </div>
            {props.length === 0 ? (
              <p className="hl-cpanel-empty">No properties for this color.</p>
            ) : (
              <div className="hl-chips">
                {props.map((p) => {
                  const on = sel.properties.includes(p);
                  return (
                    <button
                      key={p}
                      type="button"
                      aria-pressed={on}
                      className="hl-chip hl-chip-toggle"
                      data-on={on}
                      onClick={() => toggleProperty(sel.color, p)}
                    >
                      {p}
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
