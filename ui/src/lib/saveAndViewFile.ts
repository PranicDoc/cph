/**
 * Saves a file to the device AND shows it.
 *
 * A plain `<a download>` is ignored for a cross-origin link (the storage URL), and on phones the
 * browser then just opens the file. Fetching it as a blob makes the download attribute work, and the
 * same blob is opened in a tab so the person can read it straight away.
 *
 * The tab is opened first, inside the tap, because browsers block a tab opened after an await.
 * If the file cannot be fetched (e.g. storage CORS) the tab is pointed at the link instead.
 */
export async function saveAndViewFile(url: string, fileName: string): Promise<void> {
  const tab = window.open("", "_blank");
  try {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`Download failed (${res.status})`);
    const blob = new Blob([await res.blob()], { type: "application/pdf" });
    const blobUrl = URL.createObjectURL(blob);

    const link = document.createElement("a");
    link.href = blobUrl;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    if (tab) tab.location.href = blobUrl;
    setTimeout(() => URL.revokeObjectURL(blobUrl), 5 * 60 * 1000);
  } catch (e) {
    if (tab) tab.location.href = url;
    else window.location.href = url;
    console.error("Could not save the file, opened it instead:", e);
  }
}
