import { useContext, useState } from "react";
import { Dialog as DialogPrimitive } from "radix-ui";
import { Search, Trash2, X } from "lucide-react";
import { Dialog, DialogClose, DialogDescription, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import type { ColorSelection } from "@/components/closure/ColorPropertySelector";
import { PickerHost } from "./pickerHost";

export interface ColourCategoryProps {
  /** "cleansing" or "energising", as used in the labels. */
  kind: string;
  chakra: string;
  value: ColorSelection[];
  colors: string[];
  getProperties: (color: string) => string[];
  getHex: (color: string) => string | undefined;
  loading?: boolean;
  onChange: (value: ColorSelection[]) => void;
}

export function Swatch({ hex, size }: { hex?: string; size: number }) {
  return <span className="hl-cswatch" style={{ width: size, height: size, background: hex }} aria-hidden="true" />;
}

/** One property per colour (UI only): picking one leaves just that one; picking the only one clears it. */
function pickProperty(value: ColorSelection[], color: string, property: string) {
  return value.map((v) =>
    v.color !== color
      ? v
      : { ...v, properties: v.properties.length === 1 && v.properties[0] === property ? [] : [property] }
  );
}

/**
 * The colour picker for one category: on desktop a drawer inside the chakra panel (portalled into
 * it), on a phone a bottom sheet over the page. Radix dialog: scrim, focus trap, Esc, focus back to
 * the opening button.
 */
export default function ColourPicker({ kind, chakra, value, colors, getProperties, getHex, loading, onChange, trigger }: ColourCategoryProps & { trigger: React.ReactNode }) {
  const host = useContext(PickerHost);
  const [inPanel, setInPanel] = useState(false);
  const [query, setQuery] = useState("");
  const [expanded, setExpanded] = useState<string | null>(null);
  const title = `${kind[0].toUpperCase()}${kind.slice(1)} colours`;
  // A picked colour outside this category (saved before categories existed) stays listed.
  const options = [...colors, ...value.map((v) => v.color).filter((c) => !colors.includes(c))].filter((c) =>
    c.toLowerCase().includes(query.trim().toLowerCase())
  );
  const rowId = (c: string) => `cpick-${kind}-${c.replace(/\W+/g, "-")}`;

  const tap = (color: string) => {
    if (!value.some((v) => v.color === color)) {
      onChange([...value, { color, properties: [] }]);
      setExpanded(color);
    } else setExpanded(expanded === color ? null : color);
  };
  const remove = (color: string) => {
    onChange(value.filter((v) => v.color !== color));
    if (expanded === color) setExpanded(null);
    document.getElementById(rowId(color))?.focus();
  };

  return (
    <Dialog
      onOpenChange={(open) => {
        if (open) setInPanel(!!host && window.matchMedia("(min-width: 768px)").matches);
        else {
          setQuery("");
          setExpanded(null);
        }
      }}
    >
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogPrimitive.Portal container={inPanel ? host : undefined}>
        <DialogPrimitive.Overlay className="hl-cpick-scrim" data-in-panel={inPanel} />
        <DialogPrimitive.Content className="hl-cpick" data-in-panel={inPanel}>
        <div className="hlx hl-cpick-in">
        <div className="hl-cpick-grab md:hidden" aria-hidden="true" />
        <div className="hl-cpick-h">
          <span className="flex flex-col gap-0.5">
            <DialogTitle className="hl-cpick-title">{title}</DialogTitle>
            <DialogDescription className="muted small">
              {chakra}, {value.length} added
            </DialogDescription>
          </span>
          <DialogClose asChild>
            <button type="button" className="hl-icon44" aria-label="Close">
              <X className="w-5 h-5" aria-hidden="true" />
            </button>
          </DialogClose>
        </div>
        <div className="hl-cpick-search">
          <label htmlFor={`cpick-q-${kind}`} className="sr-only">
            Search {kind} colours
          </label>
          <Search className="hl-cpick-search-ico" aria-hidden="true" />
          <input
            id={`cpick-q-${kind}`}
            className="input"
            placeholder="Search colours"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <div className="hl-cpick-list">
          {!loading && options.map((c) => {
            const sel = value.find((v) => v.color === c);
            const open = expanded === c;
            const props = getProperties(c);
            return (
              <div key={c} className="hl-cpick-row" data-open={open}>
                <div className="flex items-center gap-1 pr-1">
                  <button id={rowId(c)} type="button" className="hl-cpick-tap" aria-expanded={open} onClick={() => tap(c)}>
                    <Swatch hex={getHex(c)} size={22} />
                    <span style={{ fontWeight: sel ? 600 : 500 }}>{c}</span>
                    {sel && <span className="hl-badge hl-badge-ok ml-auto">Added</span>}
                  </button>
                  {sel && (
                    <button type="button" className="hl-icon44 hl-danger" aria-label={`Remove ${c}`} onClick={() => remove(c)}>
                      <Trash2 className="w-[18px] h-[18px]" aria-hidden="true" />
                    </button>
                  )}
                </div>
                {open && sel && (
                  <div role="group" aria-label={`Property for ${c}`} className="hl-cpick-props">
                    <span className="muted small">Property (pick one)</span>
                    {props.length === 0 ? (
                      <span className="muted small">No properties for this colour.</span>
                    ) : (
                      <div className="flex flex-wrap gap-1.5">
                        {props.map((p) => {
                          const on = sel.properties.includes(p);
                          return (
                            <button
                              key={p}
                              type="button"
                              className="hl-ptag"
                              aria-pressed={on}
                              onClick={() => onChange(pickProperty(value, c, p))}
                            >
                              {p}
                            </button>
                          );
                        })}
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
          {loading ? (
            <p className="muted small px-2">Loading colours…</p>
          ) : (
            options.length === 0 && <p className="muted small px-2">No colours found.</p>
          )}
        </div>
        <div className="hl-cpick-f">
          <span className="muted small max-md:hidden">Tap a colour to add it</span>
          <DialogClose asChild>
            <Button type="button" className="hl-btn44 max-md:w-full">
              Done
            </Button>
          </DialogClose>
        </div>
        </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </Dialog>
  );
}
