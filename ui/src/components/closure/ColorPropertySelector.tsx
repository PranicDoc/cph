import { useEffect, useState } from "react";
import { MultiSelect } from "@/components/ui/multi-select/multi-select";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "../ui/popover";

export interface ColorSelection {
  color: string;
  properties: string[];
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

export function ColorPropertySelector({
  value,
  colors,
  getProperties,
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
   * User changed properties for one color.
   */
  const handlePropertiesChange = (color: string, properties: string[]) => {
    const updated = value.map((item) =>
      item.color === color
        ? {
            ...item,
            properties,
          }
        : item
    );

    onChange(updated);
  };

  return (
    <div className="space-y-4">
      {label && <Label className="font-semibold">{label}</Label>}

      <MultiSelect<string>
        title="Colors"
        options={colors}
        value={selectedColors}
        onChange={handleColorsChange}
        getOptionKey={(c) => c}
        getOptionLabel={(c) => c}
        placeholder="Select colors"
        loading={loading}
        emptyText={loading ? "Loading colors…" : "No colors found."}
      />

      {value.map((selection) => (
        <div key={selection.color} className="space-y-2 rounded-lg border p-3">
          <Label className="font-medium">{selection.color}</Label>

          <MultiSelect<string>
            title={`${selection.color} Properties`}
            options={propertyOptions[selection.color] ?? []}
            value={selection.properties}
            onChange={(properties) =>
              handlePropertiesChange(selection.color, properties)
            }
            getOptionKey={(p) => p}
            getOptionLabel={(p) => p}
            placeholder={`Select ${selection.color} properties`}
          />
        </div>
      ))}
    </div>
  );
}
