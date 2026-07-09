import { useState } from "react"
import type { ComponentType } from "react"
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  flexRender,
} from "@tanstack/react-table"
import type { ColumnDef, SortingState, TableMeta, Row } from "@tanstack/react-table"
import { ArrowUp, ArrowDown, ChevronsUpDown } from "lucide-react"

import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableHeader,
  TableBody,
  TableHead,
  TableRow,
  TableCell,
} from "@/components/ui/table"

interface DataTableProps<T> {
  columns: ColumnDef<T, unknown>[]
  data: T[]
  isLoading?: boolean
  isError?: boolean
  refetch?: () => void
  emptyIcon?: ComponentType<{ className?: string }>
  emptyMessage?: string
  onRowClick?: (row: Row<T>) => void
  rowClassName?: string | ((row: Row<T>) => string)
  enableSorting?: boolean
  compact?: boolean
  meta?: TableMeta<T>
  className?: string
  headerRowClassName?: string
  headCellClassName?: string
  cellClassName?: string
  loadingRowCount?: number
}

export function DataTable<T>(props: DataTableProps<T>) {
  const {
    columns,
    data,
    isLoading = false,
    isError = false,
    refetch,
    emptyIcon: EmptyIcon,
    emptyMessage = "No data available.",
    onRowClick,
    rowClassName,
    enableSorting = true,
    compact = false,
    meta,
    className,
    headerRowClassName,
    headCellClassName,
    cellClassName,
    loadingRowCount = 3,
  } = props

  const [sorting, setSorting] = useState<SortingState>([])

  const table = useReactTable({
    data,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    enableSorting,
    meta,
  })

  const cellPadding = compact ? "px-2 py-1.5" : "px-4 py-3"

  if (isError) {
    return (
      <div className="bg-danger/10 border border-danger/20 rounded-xl px-4 py-4 flex items-center justify-between gap-4">
        <p className="text-sm text-danger">Unable to load — try refreshing.</p>
        {refetch && (
          <Button size="sm" className="font-semibold" onClick={() => refetch()}>
            Retry
          </Button>
        )}
      </div>
    )
  }

  if (!isLoading && data.length === 0) {
    return (
      <div className="py-12 flex flex-col items-center justify-center gap-3">
        {EmptyIcon && <EmptyIcon className="w-10 h-10 text-text-light" />}
        <p className="text-sm text-text-light">{emptyMessage}</p>
      </div>
    )
  }

  return (
    <div
      className={cn(
        "rounded-xl border border-border overflow-hidden bg-card",
        className
      )}
    >
      <Table>
        <TableHeader>
          {table.getHeaderGroups().map((headerGroup) => (
            <TableRow
              key={headerGroup.id}
              className={cn(
                "bg-muted/50 border-b border-border hover:bg-muted/50",
                headerRowClassName
              )}
            >
              {headerGroup.headers.map((header) => {
                const canSort = header.column.getCanSort()
                return (
                  <TableHead
                    key={header.id}
                    className={cn(
                      "h-auto text-xs font-medium text-text-light uppercase tracking-wide",
                      cellPadding,
                      canSort && "cursor-pointer select-none hover:bg-muted",
                      headCellClassName
                    )}
                    onClick={
                      canSort ? () => header.column.toggleSorting() : undefined
                    }
                  >
                    {flexRender(
                      header.column.columnDef.header,
                      header.getContext()
                    )}
                    {canSort &&
                      (header.column.getIsSorted() === "asc" ? (
                        <ArrowUp className="w-3 h-3 text-text-light inline ml-1" />
                      ) : header.column.getIsSorted() === "desc" ? (
                        <ArrowDown className="w-3 h-3 text-text-light inline ml-1" />
                      ) : (
                        <ChevronsUpDown className="w-3 h-3 text-text-light inline ml-1" />
                      ))}
                  </TableHead>
                )
              })}
            </TableRow>
          ))}
        </TableHeader>
        <TableBody className="divide-y divide-border">
          {isLoading
            ? Array.from({ length: loadingRowCount }).map((_, rowIdx) => (
                <TableRow key={rowIdx}>
                  {Array.from({
                    length: table.getVisibleLeafColumns().length,
                  }).map((_, colIdx) => (
                    <TableCell
                      key={colIdx}
                      className={cn(cellPadding, cellClassName)}
                    >
                      <Skeleton className="h-4 w-full" />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            : table.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  className={cn(
                    "hover:bg-muted/50 transition-colors border-0",
                    onRowClick && "cursor-pointer",
                    typeof rowClassName === "function"
                      ? rowClassName(row)
                      : rowClassName
                  )}
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell
                      key={cell.id}
                      className={cn(
                        "text-text whitespace-normal",
                        cellPadding,
                        cellClassName
                      )}
                    >
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
        </TableBody>
      </Table>
    </div>
  )
}
