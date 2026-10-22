import { createColumnHelper } from "@tanstack/react-table";

import type { ChakraDocEntry } from "../ChakraScan/types/spatial-chakra";
import { DataTable } from "@/components/ui/data-table";
import { useUiVersion } from "../../ui-version";

const columnHelper = createColumnHelper<ChakraDocEntry>();

interface ScanPreviewTableProps {
  data: ChakraDocEntry[];
}

const columns = [
  columnHelper.accessor("name", {
    header: "Chakra",
    cell: (info) => (
      <span className="font-medium">{info.getValue() ?? "-"}</span>
    ),
  }),

  columnHelper.accessor("energyLevel", {
    header: "Energy Level",
    cell: (info) => info.getValue() ?? "-",
  }),

  columnHelper.accessor("activationLevel", {
    header: "Activation Level",
    cell: (info) => info.getValue() ?? "-",
  }),
];

export function ScanPreviewTable({ data }: ScanPreviewTableProps) {
  const { isNewView } = useUiVersion();

  if (isNewView) {
    if (data.length === 0) return <p className="muted">No chakra data available.</p>;
    return (
      <>
        <div className="t-wrap">
          <table className="t">
            <thead>
              <tr><th>Chakra</th><th>Energy level</th><th>Activation level</th></tr>
            </thead>
            <tbody>
              {data.map((row, i) => (
                <tr key={`${row.name}-${i}`}>
                  <td data-label="Chakra"><span className="name">{row.name ?? "-"}</span></td>
                  <td data-label="Energy level">{row.energyLevel ?? "-"}</td>
                  <td data-label="Activation level">{row.activationLevel ?? "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="small muted">D depleted, N normal, C congested. U under, N normal, O over.</div>
      </>
    );
  }

  return (
    <DataTable<ChakraDocEntry>
      columns={columns}
      data={data}
      enableSorting={false}
      emptyMessage="No chakra data available."
      headerRowClassName="bg-primary hover:bg-primary"
      headCellClassName="px-6 py-3 font-semibold text-white text-sm normal-case tracking-normal"
      cellClassName="px-6 py-4"
      rowClassName="hover:bg-primary/5"
    />
  );
}
