import { useEffect, useRef, useState } from "react";

interface Props {
  fileUrl: string;
}

/**
 * Renders every page of a PDF into stacked canvases, scaled to the container width (so it works at
 * phone width). pdf.js and its worker load on first use, keeping them out of the main bundle.
 */
export default function PdfDocumentView({ fileUrl }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    const container = containerRef.current;
    if (!container) return;
    setError(null);
    setLoading(true);
    container.replaceChildren();
    let loadingTask: { destroy: () => Promise<void> } | null = null;

    (async () => {
      const pdfjs = await import("pdfjs-dist");
      const workerUrl = (await import("pdfjs-dist/build/pdf.worker.min.mjs?url")).default;
      pdfjs.GlobalWorkerOptions.workerSrc = workerUrl;

      if (cancelled) return;
      const task = pdfjs.getDocument({ url: fileUrl });
      loadingTask = task;
      const doc = await task.promise;
      if (cancelled) return;
      const width = container.clientWidth || 600;
      const ratio = window.devicePixelRatio || 1;
      for (let n = 1; n <= doc.numPages; n++) {
        const page = await doc.getPage(n);
        if (cancelled) return;
        const base = page.getViewport({ scale: 1 });
        const scale = width / base.width;
        const viewport = page.getViewport({ scale: scale * ratio });
        const canvas = document.createElement("canvas");
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        canvas.style.width = `${width}px`;
        canvas.style.height = `${base.height * scale}px`;
        canvas.className = "mb-3 rounded border bg-white shadow-sm";
        container.appendChild(canvas);
        await page.render({ canvas, viewport }).promise;
        if (cancelled) return;
      }
      if (!cancelled) setLoading(false);
    })().catch(() => {
      if (!cancelled) {
        setError("Could not display this PDF. Use Download to open it instead.");
        setLoading(false);
      }
    });

    return () => {
      cancelled = true;
      // Frees the document and its worker so switching revisions does not leak them.
      void loadingTask?.destroy();
    };
  }, [fileUrl]);

  return (
    <div className="w-full">
      {loading && <p className="text-sm text-muted-foreground">Loading PDF…</p>}
      {error && (
        <p role="alert" className="text-sm text-red-500">
          {error}
        </p>
      )}
      <div ref={containerRef} className="w-full" />
    </div>
  );
}
