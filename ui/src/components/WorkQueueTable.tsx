import { useState } from "react";
import type { ComponentType } from "react";
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  flexRender,
} from "@tanstack/react-table";
import type { ColumnDef, SortingState } from "@tanstack/react-table";
import { ArrowUp, ArrowDown, ChevronsUpDown } from "lucide-react";

interface WorkQueueTableProps<T> {
  columns: ColumnDef<T, unknown>[];
  data: T[];
  isLoading: boolean;
  isError: boolean;
  refetch: () => void;
  emptyIcon: ComponentType<{ className?: string }>;
  emptyMessage: string;
}

export function WorkQueueTable<T>(props: WorkQueueTableProps<T>) {
  const { columns, data, isLoading, isError, refetch, emptyIcon, emptyMessage } =
    props;

  const [sorting, setSorting] = useState<SortingState>([]);

  const table = useReactTable({
    data,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  if (isError) {
    return (
      <div className="bg-danger/10 border border-danger/20 rounded-xl px-4 py-4 flex items-center justify-between gap-4">
        <p className="text-sm text-danger">Unable to load — try refreshing.</p>
        <button
          className="bg-primary text-white rounded-lg px-4 py-2 text-sm font-semibold hover:bg-primary-dark transition-colors"
          onClick={() => refetch()}
        >
          Retry
        </button>
      </div>
    );
  }

  if (!isLoading && !isError && data.length === 0) {
    const Icon = emptyIcon;
    return (
      <div className="py-12 flex flex-col items-center justify-center gap-3">
        <Icon className="w-10 h-10 text-text-light" />
        <p className="text-sm text-text-light">{emptyMessage}</p>
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-border overflow-hidden">
      <table className="w-full text-sm">
        <thead>
          {table.getHeaderGroups().map((headerGroup) => (
            <tr key={headerGroup.id} className="bg-surface border-b border-border">
              {headerGroup.headers.map((header) => (
                <th
                  key={header.id}
                  className="px-4 py-3 text-left text-xs font-medium text-text-light uppercase tracking-wide cursor-pointer select-none hover:bg-surface/80"
                  onClick={() => header.column.toggleSorting()}
                >
                  {flexRender(header.column.columnDef.header, header.getContext())}
                  {header.column.getIsSorted() === "asc" ? (
                    <ArrowUp className="w-3 h-3 text-text-light inline ml-1" />
                  ) : header.column.getIsSorted() === "desc" ? (
                    <ArrowDown className="w-3 h-3 text-text-light inline ml-1" />
                  ) : (
                    <ChevronsUpDown className="w-3 h-3 text-text-light inline ml-1" />
                  )}
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody className="divide-y divide-border">
          {isLoading
            ? Array.from({ length: 3 }).map((_, rowIdx) => (
                <tr key={rowIdx} className="animate-pulse">
                  {Array.from({
                    length: table.getVisibleLeafColumns().length,
                  }).map((_, colIdx) => (
                    <td key={colIdx} className="px-4 py-3">
                      <div className="bg-muted rounded h-4 w-full" />
                    </td>
                  ))}
                </tr>
              ))
            : table.getRowModel().rows.map((row) => (
                <tr key={row.id} className="hover:bg-surface/50 transition-colors">
                  {row.getVisibleCells().map((cell) => (
                    <td key={cell.id} className="px-4 py-3 text-text">
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </td>
                  ))}
                </tr>
              ))}
        </tbody>
      </table>
    </div>
  );
}
