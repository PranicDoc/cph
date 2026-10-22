/** Seconds as "mm:ss" for a recording timer. */
export function formatElapsed(seconds: number): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(Math.floor(seconds / 60))}:${pad(seconds % 60)}`;
}
