import { useRef, useState } from "react";
import { Upload, File as FileIcon, X, Download } from "lucide-react";
import api from "../lib/axios";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";

interface FileUploadProps {
  value?: (File | string)[];
  onChange?: (files: (File | string)[]) => void;
  onBlur?: () => void;
  readOnly?: boolean;
  /** Ask before removing a file (healer lab findings); the patient form removes immediately. */
  confirmRemove?: boolean;
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function keyToFilename(key: string): string {
  return key.split("/").pop() ?? key;
}

export function FileUpload({
  value = [],
  onChange,
  onBlur,
  readOnly = false,
  confirmRemove = false,
}: FileUploadProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [downloading, setDownloading] = useState<string | null>(null);
  const [pendingRemoveIdx, setPendingRemoveIdx] = useState<number | null>(null);

  const addFiles = (incoming: FileList | null) => {
    if (!incoming || !onChange) return;
    const existingFiles = value.filter((v): v is File => v instanceof File);
    const newFiles = Array.from(incoming).filter(
      (f) => !existingFiles.some((e) => e.name === f.name && e.size === f.size)
    );
    onChange([...value, ...newFiles]);
  };

  const removeFile = (idx: number) => {
    if (!onChange) return;
    onChange(value.filter((v, i) => i !== idx) as (File | string)[]);
  };

  const requestRemove = (idx: number) => {
    if (confirmRemove) {
      setPendingRemoveIdx(idx);
    } else {
      removeFile(idx);
    }
  };

  const pendingItem = pendingRemoveIdx !== null ? value[pendingRemoveIdx] : undefined;
  const pendingName =
    pendingItem instanceof File ? pendingItem.name : pendingItem ? keyToFilename(pendingItem) : "";

  const downloadKey = async (key: string) => {
    setDownloading(key);
    try {
      const { data } = await api.get("/api/v1/cases/download", { params: { fileUrl: key } });
      window.open(data.data.url, "_blank", "noopener,noreferrer");
    } finally {
      setDownloading(null);
    }
  };

  if (readOnly) {
    if (value.length === 0) {
      return <p className="text-sm text-text-light/50 italic">No files uploaded</p>;
    }
    return (
      <ul className="space-y-1.5">
        {value.map((item, idx) => {
          if (typeof item === "string") {
            return (
              <li key={idx} className="flex items-center gap-1.5">
                <Download className="w-3.5 h-3.5 text-primary flex-shrink-0" />
                <Button
                  type="button"
                  variant="link"
                  onClick={() => downloadKey(item)}
                  disabled={downloading === item}
                  className="h-auto p-0 text-sm font-normal underline underline-offset-2 decoration-primary/40 hover:decoration-primary truncate max-w-xs justify-start"
                >
                  {keyToFilename(item)}
                </Button>
                {downloading === item && (
                  <span className="text-xs text-text-light flex-shrink-0">Opening...</span>
                )}
              </li>
            );
          }
          return (
            <li
              key={idx}
              className="flex items-center gap-2 px-3 py-2 border border-border rounded-lg bg-white text-sm"
            >
              <FileIcon className="w-4 h-4 text-primary flex-shrink-0" />
              <span className="truncate text-text">{item.name}</span>
              <span className="text-text-light/60 flex-shrink-0 text-xs">{formatSize(item.size)}</span>
            </li>
          );
        })}
      </ul>
    );
  }

  return (
    <div className="space-y-3">
      <div
        onClick={() => inputRef.current?.click()}
        onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => { e.preventDefault(); setDragOver(false); addFiles(e.dataTransfer.files); }}
        onBlur={onBlur}
        className={`border-2 border-dashed rounded-lg p-6 flex flex-col items-center gap-2 cursor-pointer transition-colors ${
          dragOver
            ? "border-primary bg-primary/5"
            : "border-border hover:border-primary/50 hover:bg-background"
        }`}
      >
        <Upload className="w-8 h-8 text-text-light/50" />
        <p className="text-sm text-text-light text-center">
          Drag & drop files here, or{" "}
          <span className="text-primary font-medium">click to browse</span>
        </p>
        <Input
          ref={inputRef}
          type="file"
          multiple
          className="hidden"
          onChange={(e) => addFiles(e.target.files)}
        />
      </div>

      {value.length > 0 && (
        <ul className="space-y-1.5">
          {value.map((item, idx) => {
            const file = item instanceof File ? item : null;
            return (
              <li
                key={idx}
                className="flex items-center justify-between px-3 py-2 border border-border rounded-lg bg-white text-sm"
              >
                <div className="flex items-center gap-2 min-w-0">
                  <FileIcon className="w-4 h-4 text-primary flex-shrink-0" />
                  <span className="truncate text-text">{file ? file.name : keyToFilename(item as string)}</span>
                  {file && (
                    <span className="text-text-light/60 flex-shrink-0 text-xs">{formatSize(file.size)}</span>
                  )}
                </div>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-xs"
                  onClick={() => requestRemove(idx)}
                  className="h-auto w-auto p-0.5 text-text-light/50 hover:text-danger hover:bg-transparent flex-shrink-0 ml-2"
                  aria-label={`Remove ${file ? file.name : item}`}
                >
                  <X className="w-3.5 h-3.5" />
                </Button>
              </li>
            );
          })}
        </ul>
      )}

      <ConfirmDialog
        open={pendingRemoveIdx !== null}
        onOpenChange={(open) => {
          if (!open) setPendingRemoveIdx(null);
        }}
        title="Delete lab finding?"
        description={
          <>
            <span className="font-medium">{pendingName}</span> will be removed from this case when
            you save.
          </>
        }
        confirmLabel="Delete"
        destructive
        onConfirm={() => {
          if (pendingRemoveIdx !== null) removeFile(pendingRemoveIdx);
          setPendingRemoveIdx(null);
        }}
      />
    </div>
  );
}
