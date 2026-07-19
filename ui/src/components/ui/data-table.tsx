import * as React from "react"
import { useState } from "react"
import type { ComponentType } from "react"
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  flexRender,
} from "@tanstack/react-table"
import type { ColumnDef, SortingState, TableMeta, Row } from "@tanstack/react-table"

// Per-column class overrides, read from columnDef.meta:
//   { meta: { headClassName: "...", cellClassName: "..." } }
type ColumnClassMeta = {
  headClassName?: string
  cellClassName?: string
}
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

interface DataTableRowProps<T> {
  row: Row<T>
  // Not read directly by this component -- row.getVisibleCells() reads
  // column defs off the row's own table instance. Included purely so the
  // memo comparator below can detect "columns changed" and re-render,
  // since that doesn't change `row.original` at all.
  columns: ColumnDef<T, unknown>[]
  // Same reasoning as `columns`, but for `meta`: cell renderers read
  // dynamic values (e.g. reference data that loads asynchronously) via
  // `table.options.meta` instead of closing over them directly, precisely
  // so `columns` itself can stay referentially stable (an unstable cell
  // function reference makes TanStack's flexRender -- which calls
  // `React.createElement(cellFn, props)` -- treat it as a brand new
  // component type and fully remount it, destroying any state inside,
  // e.g. an open dropdown). But that means `columns`/`row.original` alone
  // no longer capture "did this row's rendered content actually change" --
  // `meta` must be compared too, or the memo skips re-rendering a row
  // whose meta-driven props are stale.
  meta: TableMeta<T> | undefined
  cellPadding: string
  cellClassName?: string
  rowClassName?: string | ((row: Row<T>) => string)
  onRowClick?: (row: Row<T>) => void
}

function DataTableRowInner<T>({
  row,
  cellPadding,
  cellClassName,
  rowClassName,
  onRowClick,
}: DataTableRowProps<T>) {
  // `columns` isn't read here -- row.getVisibleCells() already resolves
  // cell renderers via the row's own table instance. It only exists on
  // this component's props for the memo comparator (see above/below).
  return (
    <TableRow
      className={cn(
        "hover:bg-muted/50 transition-colors border-0",
        onRowClick && "cursor-pointer",
        typeof rowClassName === "function" ? rowClassName(row) : rowClassName
      )}
      onClick={onRowClick ? () => onRowClick(row) : undefined}
    >
      {row.getVisibleCells().map((cell) => (
        <TableCell
          key={cell.id}
          className={cn(
            "text-text whitespace-normal",
            cellPadding,
            cellClassName,
            (cell.column.columnDef.meta as ColumnClassMeta | undefined)
              ?.cellClassName
          )}
        >
          {flexRender(cell.column.columnDef.cell, cell.getContext())}
        </TableCell>
      ))}
    </TableRow>
  )
}

// TanStack recomputes `getRowModel()` (new row wrapper objects for every
// row) whenever the `data` array reference changes, i.e. on every edit —
// but unchanged rows keep the same `row.original` reference. Skipping
// re-render when that reference (and the other row-affecting props) is
// unchanged stops one row's edit from re-rendering every other row.
function dataTableRowPropsAreEqual<T>(
  prev: DataTableRowProps<T>,
  next: DataTableRowProps<T>
) {
  return (
    prev.row.id === next.row.id &&
    prev.row.original === next.row.original &&
    prev.columns === next.columns &&
    prev.meta === next.meta &&
    prev.cellPadding === next.cellPadding &&
    prev.cellClassName === next.cellClassName &&
    prev.rowClassName === next.rowClassName &&
    prev.onRowClick === next.onRowClick
  )
}

// React.memo doesn't preserve generic type params on its return type; cast
// back to the generic function-component signature so callers still get
// correct per-usage type inference for T.
const DataTableRow = React.memo(
  DataTableRowInner,
  dataTableRowPropsAreEqual
) as typeof DataTableRowInner

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
                      headCellClassName,
                      (header.column.columnDef.meta as ColumnClassMeta | undefined)
                        ?.headClassName
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
                <DataTableRow
                  key={row.id}
                  row={row}
                  columns={columns}
                  meta={meta}
                  cellPadding={cellPadding}
                  cellClassName={cellClassName}
                  rowClassName={rowClassName}
                  onRowClick={onRowClick}
                />
              ))}
        </TableBody>
      </Table>
    </div>
  )
}
