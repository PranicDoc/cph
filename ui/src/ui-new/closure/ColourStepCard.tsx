import type { ColorSelection } from "@/components/closure/ColorPropertySelector";
import type { UsualPick } from "@/hooks/useUsualPicks";
import ColourPicker, { Swatch, type ColourCategoryProps } from "./ColourPicker";

const count = (n: number) => (n === 0 ? "No colours yet" : n === 1 ? "1 colour" : `${n} colours`);
const isPick = (v: ColorSelection | undefined, pick: UsualPick) =>
  !!v && (pick.property ? v.properties.length === 1 && v.properties[0] === pick.property : v.properties.length === 0);

/** "1. Cleanse" / "2. Energise": usual picks, the colours added, and the button that opens the picker. */
export default function ColourStepCard({ title, picks, ...cat }: ColourCategoryProps & { title: string; picks: UsualPick[] }) {
  const { kind, value, onChange, getHex } = cat;
  const headingId = `step-${kind}`;
  const togglePick = (pick: UsualPick) => {
    const current = value.find((v) => v.color === pick.color);
    const properties = pick.property ? [pick.property] : [];
    if (isPick(current, pick)) onChange(value.filter((v) => v.color !== pick.color));
    else if (current) onChange(value.map((v) => (v.color === pick.color ? { ...v, properties } : v)));
    else onChange([...value, { color: pick.color, properties }]);
  };

  return (
    <section className="hl-step" aria-labelledby={headingId}>
      <div className="flex items-baseline justify-between gap-2">
        <h3 id={headingId}>{title}</h3>
        <span className="muted small">{count(value.length)}</span>
      </div>
      {picks.length > 0 && (
        <div className="flex flex-col gap-1.5">
          <span id={`${headingId}-usual`} className="hl-step-lbl">
            Your usual picks
          </span>
          <div role="group" aria-labelledby={`${headingId}-usual`} className="hl-usual">
            {picks.map((pick) => {
              const on = isPick(value.find((v) => v.color === pick.color), pick);
              return (
                <button
                  key={`${pick.color}|${pick.property}`}
                  type="button"
                  className="hl-uchip"
                  aria-pressed={on}
                  onClick={() => togglePick(pick)}
                >
                  <Swatch hex={getHex(pick.color)} size={16} checked={on} />
                  {pick.property ? `${pick.color}, ${pick.property}` : pick.color}
                </button>
              );
            })}
          </div>
        </div>
      )}
      {value.length > 0 && (
        <ul className="flex flex-col gap-1.5" aria-label={`${kind[0].toUpperCase()}${kind.slice(1)} colours added`}>
          {value.map((v) => (
            <li key={v.color} className="hl-picked">
              <Swatch hex={getHex(v.color)} size={20} />
              <span className="flex flex-col">
                <span className="hl-picked-name">{v.color}</span>
                <span className={v.properties.length ? "hl-picked-prop" : "muted small"}>
                  {v.properties.length ? v.properties.join(", ") : "No property"}
                </span>
              </span>
            </li>
          ))}
        </ul>
      )}
      <ColourPicker
        {...cat}
        trigger={
          <button type="button" className="hl-dashed">
            {value.length ? `Add or change ${kind} colours` : `Add ${kind} colours`}
          </button>
        }
      />
    </section>
  );
}
