import { createColumnHelper } from "@tanstack/react-table";
import { useState, useEffect, useRef, useCallback, useMemo } from "react";
import { ComputedPatient, usePatient } from "../context/PatientContext";
import { DataTable } from "@/components/ui/data-table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { Search, Download } from "lucide-react";
import api from "../lib/axios";

type PDFJob = ComputedPatient & {
  healingCaseId: string;
  fileUrl: string;
  completedAt: string;
};

export function PdfList() {
  const [pdfJobs, setPdfJobs] = useState<
    { healingCaseId: string; fileUrl: string; completedAt: string }[]
  >([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const { patients } = usePatient();
  const [isFetchingMore, setIsFetchingMore] = useState<boolean>(false);

  const [scrollPosition, setScrollPosition] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [searchTerm, setSearchTerm] = useState<string>("");
  const [localFilter, setLocalFilter] = useState<string>("");
  const urlCache = useRef<Record<string, { url: string; expiresAt: number }>>(
    {}
  );

  const searchTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const data = useMemo<PDFJob[]>(() => {
    return pdfJobs.map((job) => {
      console.log(patients);
      const patientData = patients.find(
        (p) => p.healingCaseId === job.healingCaseId
      );
      return {
        ...(patientData as ComputedPatient),
        healingCaseId: job.healingCaseId,
        fileUrl: job.fileUrl,
        completedAt: job.completedAt,
        fullName: patientData?.fullName || "Unknown Patient",
      };
    });
  }, [pdfJobs, patients]);

  const fetchPdfRecords = async (
    cursor: string | null = null,
    searchQuery: string = "",
    isReset: boolean = false
  ) => {
    if (isReset) setIsLoading(true);
    else setIsFetchingMore(true);

    try {
      const params = new URLSearchParams({ limit: "10" });
      if (cursor) params.append("scrollPosition", cursor);
      if (searchQuery) params.append("search", searchQuery);

      const response = await api.get(
        `/api/v1/cases/pdfs?${params.toString()}`
      );
      const result = response.data.data;
      const newItems = result.content || [];
      setPdfJobs((prev) => (isReset ? newItems : [...prev, ...newItems]));
      const nextCursor =
        result.positionAt || result.pageable?.scrollPosition || null;
      setScrollPosition(nextCursor);
      setHasMore(newItems.length > 0 && nextCursor !== null);
    } catch (error) {
      console.error("Failed to fetch PDF records:", error);
    } finally {
      setIsLoading(false);
      setIsFetchingMore(false);
    }
  };

  useEffect(() => {
    fetchPdfRecords(null, "", true);
  }, []);

  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setSearchTerm(value);

    if (searchTimeoutRef.current) clearTimeout(searchTimeoutRef.current);

    if (!hasMore && !scrollPosition) {
      setLocalFilter(value);
    } else {
      setLocalFilter("");
      searchTimeoutRef.current = setTimeout(() => {
        fetchPdfRecords(null, value, true);
      }, 500);
    }
  };

  const observer = useRef<IntersectionObserver | null>(null);
  const sentinelRef = useCallback(
    (node: HTMLDivElement | null) => {
      if (isLoading || isFetchingMore) return;
      if (observer.current) observer.current.disconnect();

      observer.current = new IntersectionObserver((entries) => {
        if (entries[0].isIntersecting && hasMore) {
          fetchPdfRecords(scrollPosition, searchTerm, false);
        }
      });

      if (node) observer.current.observe(node);
    },
    [isLoading, isFetchingMore, hasMore, scrollPosition, searchTerm]
  );

  const handleDownloadPdf = async (record: PDFJob) => {
    try {
      let presignedUrl = "";
      const now = Date.now();
      const SAFETY_BUFFER_MS = 60 * 1000; // 1 minute buffer

      // 1. Check if we already have a valid link in React memory
      const cachedData = urlCache.current[record.fileUrl];

      if (cachedData && cachedData.expiresAt - SAFETY_BUFFER_MS > now) {
        // 🟢 CACHE HIT: The link is still valid. Skip the API call.
        presignedUrl = cachedData.url;
      } else {
        // 🔴 CACHE MISS: It's missing or expired. Hit the backend.
        const response = await api.get("/api/v1/cases/download", {
          params: {
            fileUrl: record.fileUrl,
            // Optional: Send the old data to the backend if your Java logic requires it
            // existingUrl: cachedData?.url,
            // existingExpiryTime: cachedData?.expiresAt
          },
        });

        // Assuming your backend now returns the object we discussed earlier:
        // { url: "https...", expiresAt: "2026-05-24T13:16:49Z" }
        const { url, expiresAt } = response.data.data;
        presignedUrl = url;

        // 2. Save the fresh link and time to our React cache for next time
        urlCache.current[record.fileUrl] = {
          url: url,
          // Convert the backend's ISO time string into a JavaScript timestamp
          expiresAt: new Date(expiresAt).getTime(),
        };
      }

      // 3. Trigger the file download
      const link = document.createElement("a");
      link.href = presignedUrl;
      const fileName = `${record.fullName.replace(/\s+/g, "_")}_Case.pdf`;
      link.setAttribute("download", fileName);
      link.target = "_blank";

      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (error) {
      console.error("Failed to generate or download PDF:", error);
    }
  };

  // --- Table Configuration ---

  const columnHelper = createColumnHelper<PDFJob>();

  const columns = [
    columnHelper.accessor("fullName", {
      header: () => <div className="text-left">Patient Name</div>,
      cell: (info) => (
        <span className="font-medium text-left block">{info.getValue()}</span>
      ),
    }),
    columnHelper.accessor("completedAt", {
      header: () => <div className="text-left">Created</div>,
      cell: (info) => {
        const value = info.getValue();
        return (
          <span className="text-left block">
            {value ? new Date(value).toLocaleDateString() : "—"}
          </span>
        );
      },
    }),
    columnHelper.display({
      id: "actions",
      header: () => <div className="text-right">Download PDF</div>,
      cell: (info) => {
        const record = info.row.original;
        return (
          <div className="flex justify-end">
            <Button onClick={() => handleDownloadPdf(record)}>
              <Download className="w-4 h-4" />
              Download
            </Button>
          </div>
        );
      },
    }),
  ];

  const filteredData = useMemo(
    () =>
      localFilter
        ? data.filter((d) =>
            d.fullName.toLowerCase().includes(localFilter.toLowerCase())
          )
        : data,
    [data, localFilter]
  );

  return (
    <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden">
      {/* Search Header */}
      <div className="p-4 border-b border-border flex items-center gap-3">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-light" />
          <Input
            type="text"
            value={searchTerm}
            onChange={handleSearchChange}
            placeholder="Search patients..."
            className="pl-9 pr-4 py-2 border"
          />
        </div>
      </div>

      {/* Table Content */}
      <div className="overflow-x-auto">
        <DataTable<PDFJob>
          columns={columns}
          data={filteredData}
          isLoading={isLoading}
          loadingRowCount={5}
          enableSorting={false}
          emptyMessage={
            searchTerm
              ? "No matching patients found."
              : "No PDFs generated yet."
          }
          className="rounded-none border-0"
          headerRowClassName="bg-primary hover:bg-primary"
          headCellClassName="px-6 py-4 font-semibold text-white text-sm normal-case tracking-normal"
          cellClassName="px-6 py-4"
          rowClassName="hover:bg-primary/5"
        />

        {/* Infinite-scroll sentinel: observed instead of the last table row */}
        {!isLoading && filteredData.length > 0 && <div ref={sentinelRef} />}

        {/* Infinite Scroll Loading Indicator */}
        {isFetchingMore && (
          <div className="flex justify-center items-center p-4 text-primary">
            <Spinner className="mr-2" />
            <span className="text-sm font-medium">Loading more...</span>
          </div>
        )}
      </div>
    </div>
  );
}
