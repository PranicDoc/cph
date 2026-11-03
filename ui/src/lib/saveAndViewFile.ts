import api from "./axios";

/**
 * Saves a stored PDF to the device AND shows it.
 *
 * - Save: the file is fetched through our own API (which sends it as an attachment), then handed to the
 *   browser as a download. A link straight to the storage bucket cannot be saved this way: the
 *   `download` attribute is ignored for another origin, so phones just open the file.
 * - Show: `tab` is pointed at the storage link. The caller opens it first, inside the tap, because
 *   browsers block a tab opened after an await.
 *
 * If saving fails the tab still shows the file, and the error is thrown so the caller can say so.
 */
export async function saveAndViewFile(
  tab: Window | null,
  fileKey: string,
  viewUrl: string,
  fileName: string
): Promise<void> {
  if (tab) tab.location.href = viewUrl; // pop-ups blocked: the download below still happens

  const res = await api.get("/api/v1/cases/download", {
    params: { fileUrl: fileKey, stream: true },
    responseType: "blob",
  });
  const blobUrl = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }));
  const link = document.createElement("a");
  link.href = blobUrl;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  setTimeout(() => URL.revokeObjectURL(blobUrl), 60_000);
}
