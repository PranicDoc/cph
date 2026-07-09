import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table";

import type { ChakraDocEntry } from "../ChakraScan/types/spatial-chakra";

const columnHelper = createColumnHelper<ChakraDocEntry>();

interface ScanPreviewTableProps {
  data: ChakraDocEntry[];
}

export function ScanPreviewTable({ data }: ScanPreviewTableProps) {
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

  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
  });

  return (
    <div className="overflow-x-auto rounded-xl border border-border">
      <table className="min-w-full text-sm">
        <thead className="bg-primary text-white">
          {table.getHeaderGroups().map((headerGroup) => (
            <tr key={headerGroup.id}>
              {headerGroup.headers.map((header) => (
                <th
                  key={header.id}
                  className="px-6 py-3 text-left font-semibold"
                >
                  {flexRender(
                    header.column.columnDef.header,
                    header.getContext()
                  )}
                </th>
              ))}
            </tr>
          ))}
        </thead>

        <tbody>
          {table.getRowModel().rows.length === 0 ? (
            <tr>
              <td colSpan={3} className="px-6 py-8 text-center text-text-light">
                No chakra data available.
              </td>
            </tr>
          ) : (
            table.getRowModel().rows.map((row) => (
              <tr
                key={row.id}
                className="border-b border-border hover:bg-primary/5"
              >
                {row.getVisibleCells().map((cell) => (
                  <td key={cell.id} className="px-6 py-4">
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
