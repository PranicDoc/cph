import { useEffect, useMemo, useState } from "react";
import { X } from "lucide-react";
import { MultiSelect } from "@/components/ui/multi-select/multi-select";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";

export interface ColorSelection {
  color: string;
  properties: string[];
}

interface ColorPropertyPair {
  color: string;
  property: string;
}

interface ColorPropertySelectorProps {
  /**
   * Current value
   */
  value: ColorSelection[];

  /**
   * All available colors
   */
  colors: string[];

  /**
   * Returns the available properties for a given color.
   *
   * Initially this can return []
   * Later you can fetch from your backend / Redis.
   */
  getProperties: (color: string) => Promise<string[]> | string[];

  /**
   * Returns the swatch hex for a color, if known. Undefined colors simply
   * render without a swatch dot.
   */
  getColorHex?: (color: string) => string | undefined;

  /**
   * Called only when the value changes.
   */
  onChange: (value: ColorSelection[]) => void;

  /**
   * Optional heading
   */
  label?: string;

  /**
   * True while `colors` is still being fetched — shows a spinner on the
   * trigger instead of a silently empty dropdown.
   */
  loading?: boolean;
}

function ColorSwatch({ hex }: { hex?: string }) {
  if (!hex) return null;
  return (
    <span
      className="inline-block size-2.5 shrink-0 rounded-full border border-black/10"
      style={{ backgroundColor: hex }}
    />
  );
}

// TODO(tag-input-refactor): the Colors picker and the Color — Property picker
// below are both good candidates for a proper tag-input component once one
// exists in the design system. MultiSelect works, but a purpose-built
// tag-input would suit this "growing list of chips" interaction better.
export function ColorPropertySelector({
  value,
  colors,
  getProperties,
  getColorHex,
  onChange,
  label,
  loading,
}: ColorPropertySelectorProps) {
  /**
   * Properties available for every selected color.
   *
   * Example:
   *
   * {
   *   Red:["Heating","Cooling"],
   *   Blue:["Purifying"]
   * }
   */
  const [propertyOptions, setPropertyOptions] = useState<
    Record<string, string[]>
  >({});

  /**
   * Current selected colors
   */
  const selectedColors = value.map((x) => x.color);

  // A selected color stays an option even when it is outside this selector's category
  // (e.g. saved before categories existed), so it can still be seen and removed.
  const colorOptions = [
    ...colors,
    ...selectedColors.filter((c) => !colors.includes(c)),
  ];

  /**
   * Fetch properties whenever colors change.
   *
   * Later your backend can cache this in Redis.
   */
  useEffect(() => {
    const loadProperties = async () => {
      const next: Record<string, string[]> = {};

      for (const color of selectedColors) {
        next[color] = await Promise.resolve(getProperties(color));
      }

      setPropertyOptions(next);
    };

    loadProperties();
  }, [selectedColors.join(","), getProperties]);

  /**
   * User changed selected colors.
   *
   * Preserve already selected properties.
   */
  const handleColorsChange = (colors: string[]) => {
    const updated: ColorSelection[] = colors.map((color) => {
      const existing = value.find((v) => v.color === color);

      return (
        existing ?? {
          color,
          properties: [],
        }
      );
    });

    onChange(updated);
  };

  /**
   * Flat cross-product of every selected color's available properties, e.g.
   * [{color:"Red",property:"Heating"}, {color:"Red",property:"Cooling"}, ...]
   */
  const pairOptions = useMemo<ColorPropertyPair[]>(
    () =>
      selectedColors.flatMap((color) =>
        (propertyOptions[color] ?? []).map((property) => ({ color, property }))
      ),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [selectedColors.join(","), propertyOptions]
  );

  const selectedPairs = useMemo<ColorPropertyPair[]>(
    () =>
      value.flatMap((v) =>
        v.properties.map((property) => ({ color: v.color, property }))
      ),
    [value]
  );

  /**
   * User changed the flat color-property selection. Regroup pairs back into
   * per-color property lists, keeping every currently selected color present
   * (even with an empty properties list) so removing all of a color's
   * properties doesn't also deselect the color.
   */
  const handlePairsChange = (nextPairs: ColorPropertyPair[]) => {
    const grouped = new Map<string, string[]>();
    for (const pair of nextPairs) {
      const list = grouped.get(pair.color) ?? [];
      list.push(pair.property);
      grouped.set(pair.color, list);
    }

    const updated: ColorSelection[] = selectedColors.map((color) => ({
      color,
      properties: grouped.get(color) ?? [],
    }));

    onChange(updated);
  };

  const removePair = (pair: ColorPropertyPair) => {
    handlePairsChange(
      selectedPairs.filter(
        (p) => !(p.color === pair.color && p.property === pair.property)
      )
    );
  };

  const renderColorOption = (color: string) => (
    <span className="flex min-w-0 items-center gap-2">
      <ColorSwatch hex={getColorHex?.(color)} />
      <span className="truncate">{color}</span>
    </span>
  );

  const renderPairOption = (pair: ColorPropertyPair) => (
    <span className="flex min-w-0 items-center gap-2">
      <ColorSwatch hex={getColorHex?.(pair.color)} />
      <span className="truncate">
        {pair.color} — {pair.property}
      </span>
    </span>
  );

  return (
    <div className="space-y-3">
      {label && <Label className="font-semibold">{label}</Label>}

      <MultiSelect<string>
        title="Colors"
        options={colorOptions}
        value={selectedColors}
        onChange={handleColorsChange}
        getOptionKey={(c) => c}
        getOptionLabel={(c) => c}
        renderOption={renderColorOption}
        renderBadge={renderColorOption}
        placeholder="Select colors"
        loading={loading}
        emptyText={loading ? "Loading colors…" : "No colors found."}
      />

      <MultiSelect<ColorPropertyPair>
        title="Properties"
        options={pairOptions}
        value={selectedPairs}
        onChange={handlePairsChange}
        getOptionKey={(p) => `${p.color}::${p.property}`}
        getOptionLabel={(p) => `${p.color} — ${p.property}`}
        renderOption={renderPairOption}
        renderBadge={renderPairOption}
        placeholder="Select properties"
        disabled={selectedColors.length === 0}
        emptyText={
          selectedColors.length === 0
            ? "Select a color first."
            : "No properties found."
        }
      />

      {selectedPairs.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {selectedPairs.map((pair) => (
            <Badge
              key={`${pair.color}::${pair.property}`}
              variant="outline"
              className="gap-1.5 pr-1"
            >
              <ColorSwatch hex={getColorHex?.(pair.color)} />
              <span>
                {pair.color}: {pair.property}
              </span>
              <button
                type="button"
                aria-label={`Remove ${pair.color} ${pair.property}`}
                onClick={() => removePair(pair)}
                className="rounded-full p-0.5 hover:bg-muted"
              >
                <X className="size-3" />
              </button>
            </Badge>
          ))}
        </div>
      )}
    </div>
  );
}
