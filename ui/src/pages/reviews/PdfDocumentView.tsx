import { useEffect, useRef, useState } from "react";

interface Props {
  fileUrl: string;
  /** Called with the page wrapper elements (index 0 = page 1) each time one more page has rendered. */
  onPagesReady?: (pages: HTMLElement[]) => void;
}

/**
 * Renders every page of a PDF into stacked canvases, scaled to the container width (so it works at
 * phone width). pdf.js and its worker load on first use, keeping them out of the main bundle.
 */
export default function PdfDocumentView({ fileUrl, onPagesReady }: Props) {
  const onPagesReadyRef = useRef(onPagesReady);
  onPagesReadyRef.current = onPagesReady;
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
    const pageEls: HTMLElement[] = [];
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
        const wrapper = document.createElement("div");
        wrapper.dataset.pdfPage = String(n);
        wrapper.className = "relative mb-3";
        wrapper.style.width = `${width}px`;
        wrapper.style.height = `${base.height * scale}px`;
        const canvas = document.createElement("canvas");
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        canvas.style.width = "100%";
        canvas.style.height = "100%";
        canvas.className = "rounded border bg-white shadow-sm";
        wrapper.appendChild(canvas);
        container.appendChild(wrapper);
        pageEls.push(wrapper);
        await page.render({ canvas, viewport }).promise;
        onPagesReadyRef.current?.([...pageEls]);
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
