import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";

interface Props {
  page: number;
  totalPages: number;
  last: boolean;
  onPageChange: (page: number) => void;
}

/** Prev/next controls under a dashboard list; renders nothing when everything fits on one page. */
export function DashboardPager({ page, totalPages, last, onPageChange }: Props) {
  if (totalPages <= 1) return null;
  return (
    <div className="mt-3 flex items-center justify-end gap-2 text-xs text-text-light">
      <Button
        variant="ghost"
        size="icon-sm"
        aria-label="Previous page"
        disabled={page === 0}
        onClick={() => onPageChange(page - 1)}
      >
        <ChevronLeft className="w-4 h-4" />
      </Button>
      <span>
        {page + 1} / {totalPages}
      </span>
      <Button
        variant="ghost"
        size="icon-sm"
        aria-label="Next page"
        disabled={last}
        onClick={() => onPageChange(page + 1)}
      >
        <ChevronRight className="w-4 h-4" />
      </Button>
    </div>
  );
}
