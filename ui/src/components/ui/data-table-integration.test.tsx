import { useCallback, useEffect, useMemo, useState } from "react";
import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ColumnDef, TableMeta } from "@tanstack/react-table";

import { DataTable } from "./data-table";
import { ColorPropertySelector, ColorSelection } from "@/components/closure/ColorPropertySelector";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";

interface Row {
  chakra: string;
  energisingColors: ColorSelection[];
  cleansingColors: ColorSelection[];
}

const COLOR_CATALOG = [
  { name: "Red", hex: "#ff0000", properties: ["Heating", "Cooling"] },
  { name: "Green", hex: "#00ff00", properties: ["Purifying"] },
  { name: "Blue", hex: "#0000ff", properties: ["Soothing"] },
];

interface RowMeta {
  allColors: string[];
  colorsLoading: boolean;
  fetchPropertiesForColor: (color: string) => string[];
  fetchColorHex: (color: string) => string | undefined;
}

/**
 * Faithfully mirrors ClosureForm.tsx's FIXED pattern: two ColorPropertySelector
 * columns per row (energising + cleansing), a fully STABLE `columns` array
 * (deps only on the stable `updateRow`), with the changing
 * allColors/colorsLoading/fetchPropertiesForColor/fetchColorHex passed
 * through TanStack's `meta` and read fresh via `table.options.meta` inside
 * each cell -- not closed over directly. Colors resolve asynchronously (like
 * the real useReferenceData fetch) after the table has already rendered
 * rows, which is exactly the ordering that used to break this (see git
 * history: an unstable `columns` reference makes flexRender's
 * `React.createElement(cellFn, props)` treat the cell as a brand new
 * component type, fully remounting it and destroying MultiSelect's internal
 * open/draft state).
 */
function Harness() {
  const [hpuTableData, setHpuTableData] = useState<Row[]>([
    { chakra: "Crown", energisingColors: [], cleansingColors: [] },
    { chakra: "Heart", energisingColors: [], cleansingColors: [] },
  ]);

  const [colors, setColors] = useState<typeof COLOR_CATALOG>([]);
  const [colorsLoading, setColorsLoading] = useState(true);

  useEffect(() => {
    // Simulate the real network fetch resolving after the table has
    // already rendered rows -- this ordering is what triggers the bug.
    Promise.resolve().then(() => {
      setColors(COLOR_CATALOG);
      setColorsLoading(false);
    });
  }, []);

  const allColors = useMemo(() => colors.map((c) => c.name), [colors]);

  const fetchPropertiesForColor = useCallback(
    (color: string) => {
      const match = colors.find((c) => c.name === color);
      return match?.properties ?? [];
    },
    [colors]
  );

  const fetchColorHex = useCallback(
    (color: string) => colors.find((c) => c.name === color)?.hex,
    [colors]
  );

  const updateRow = useCallback(
    <K extends keyof Row>(index: number, field: K, value: Row[K]) => {
      setHpuTableData((prev) =>
        prev.map((row, i) => (i === index ? { ...row, [field]: value } : row))
      );
    },
    []
  );

  const columns = useMemo<ColumnDef<Row>[]>(
    () => [
      {
        accessorKey: "chakra",
        header: "Chakra",
        cell: ({ row }) => <span>{row.original.chakra}</span>,
      },
      {
        accessorKey: "energisingColors",
        header: "Energising Colors",
        cell: ({ row, table }) => {
          const meta = table.options.meta as RowMeta;
          return (
            <ColorPropertySelector
              value={row.original.energisingColors}
              colors={meta.allColors}
              getProperties={meta.fetchPropertiesForColor}
              getColorHex={meta.fetchColorHex}
              loading={meta.colorsLoading}
              onChange={(updated) =>
                updateRow(row.index, "energisingColors", updated)
              }
            />
          );
        },
      },
      {
        accessorKey: "cleansingColors",
        header: "Cleansing Colors",
        cell: ({ row, table }) => {
          const meta = table.options.meta as RowMeta;
          return (
            <ColorPropertySelector
              value={row.original.cleansingColors}
              colors={meta.allColors}
              getProperties={meta.fetchPropertiesForColor}
              getColorHex={meta.fetchColorHex}
              loading={meta.colorsLoading}
              onChange={(updated) =>
                updateRow(row.index, "cleansingColors", updated)
              }
            />
          );
        },
      },
    ],
    [updateRow]
  );

  return (
    <DataTable
      columns={columns}
      data={hpuTableData}
      enableSorting={false}
      meta={
        { allColors, colorsLoading, fetchPropertiesForColor, fetchColorHex } as TableMeta<Row>
      }
    />
  );
}

function renderHarness() {
  return render(
    <Dialog open>
      <DialogContent>
        <DialogTitle>Test Dialog</DialogTitle>
        <Harness />
      </DialogContent>
    </Dialog>
  );
}

describe("DataTable + two ColorPropertySelector columns (mirrors ClosureForm exactly)", () => {
  it("committing energising colors for row 0 persists and enables its property picker", async () => {
    const user = userEvent.setup();
    renderHarness();

    // Wait for the simulated async color fetch to resolve: the trigger's
    // "loading" spinner (a distinct SVG) disappears once colors arrive.
    await user.click(screen.getAllByRole("combobox")[0]);
    await screen.findByText("Red");
    await user.keyboard("{Escape}");

    // Each ColorPropertySelector instance renders exactly 2 comboboxes
    // (Colors, flat Properties) regardless of how many colors are selected.
    // 2 rows x 2 columns (energising, cleansing) x 2 = 8 comboboxes.
    let comboboxes = screen.getAllByRole("combobox");
    expect(comboboxes).toHaveLength(8);

    // index 0 = row 0 (Crown) energising Colors; index 1 = its Properties
    // picker, disabled until a color is selected.
    expect(comboboxes[1]).toBeDisabled();

    await user.click(comboboxes[0]);
    await user.click(await screen.findByText("Red"));
    await user.click(screen.getByRole("button", { name: "Done" }));

    // Regression check: the properties picker must now be enabled and offer
    // Red's properties -- proves the selection actually reached state after
    // the async color fetch resolved (not just ticked the checkbox).
    comboboxes = screen.getAllByRole("combobox");
    expect(comboboxes[1]).not.toBeDisabled();
    await user.click(comboboxes[1]);
    await user.click(await screen.findByText("Red — Heating"));
    await user.click(screen.getByRole("button", { name: "Done" }));
    expect(await screen.findByText("Red: Heating")).toBeInTheDocument();

    // Regression check: reopening the SAME energising Colors combobox must
    // show "Red" as checked (state actually persisted after close), not
    // reset by a remount.
    await user.click(screen.getAllByRole("combobox")[0]);
    const redItem = await screen.findAllByText("Red");
    const redInList = redItem
      .map((el) => el.closest('[data-slot="command-item"]'))
      .find((el) => el !== null);
    expect(redInList?.querySelector('[data-slot="checkbox"]')).toHaveAttribute(
      "data-state",
      "checked"
    );
  });

  it("selecting cleansing colors for row 1 does not affect row 0's energising colors", async () => {
    const user = userEvent.setup();
    renderHarness();

    // Wait for the simulated async color fetch to resolve before interacting.
    await user.click(screen.getAllByRole("combobox")[0]);
    await screen.findByText("Red");
    await user.keyboard("{Escape}");

    // Select Red as row 0's energising color first (index 0 = row 0
    // energising Colors picker).
    let comboboxes = screen.getAllByRole("combobox");
    await user.click(comboboxes[0]);
    await user.click(await screen.findByText("Red"));
    await user.click(screen.getByRole("button", { name: "Done" }));

    // Row layout is [energising Colors, energising Properties, cleansing
    // Colors, cleansing Properties] per row; row 1's cleansing Colors picker
    // is index 6 of 8.
    comboboxes = screen.getAllByRole("combobox");
    expect(comboboxes).toHaveLength(8);
    await user.click(comboboxes[6]);
    await user.click(await screen.findByText("Blue"));
    await user.click(screen.getByRole("button", { name: "Done" }));

    // Row 0's energising "Red" selection should still be present (its
    // Properties picker still enabled).
    expect(screen.getAllByRole("combobox")[1]).not.toBeDisabled();
    // Row 1's cleansing "Blue" selection should also be present (its
    // Properties picker now enabled too).
    expect(screen.getAllByRole("combobox")[7]).not.toBeDisabled();
  });
});
