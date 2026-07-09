import { createColumnHelper } from "@tanstack/react-table";

import type { ChakraDocEntry } from "../ChakraScan/types/spatial-chakra";
import { DataTable } from "@/components/ui/data-table";

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
