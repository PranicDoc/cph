// Hand-rolled — no dependency pulled in just for a coarse phonetic check on
// a handful of short domain words. Used to rank likely-intended chakra names
// when ASR produces a word that doesn't match the catalog at all (e.g.
// "ground" for "crown"), so a human can pick from a short ranked list
// instead of the raw transcript being silently dropped.

/** Classic American Soundex — a 4-character phonetic code. */
export function soundex(word: string): string {
  const letters = word.toUpperCase().replace(/[^A-Z]/g, "");
  if (!letters) return "";

  const CODES: Record<string, string> = {
    B: "1", F: "1", P: "1", V: "1",
    C: "2", G: "2", J: "2", K: "2", Q: "2", S: "2", X: "2", Z: "2",
    D: "3", T: "3",
    L: "4",
    M: "5", N: "5",
    R: "6",
  };

  let code = letters[0];
  let prevDigit = CODES[letters[0]] ?? "";

  for (let i = 1; i < letters.length && code.length < 4; i++) {
    const digit = CODES[letters[i]] ?? "";
    if (digit && digit !== prevDigit) {
      code += digit;
    }
    prevDigit = digit;
  }

  return (code + "000").slice(0, 4);
}

/** Edit distance — used as a tiebreaker among same-soundex candidates. */
export function levenshtein(a: string, b: string): number {
  const rows = a.length + 1;
  const cols = b.length + 1;
  const dp: number[][] = Array.from({ length: rows }, () => new Array(cols).fill(0));

  for (let i = 0; i < rows; i++) dp[i][0] = i;
  for (let j = 0; j < cols; j++) dp[0][j] = j;

  for (let i = 1; i < rows; i++) {
    for (let j = 1; j < cols; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      dp[i][j] = Math.min(
        dp[i - 1][j] + 1,
        dp[i][j - 1] + 1,
        dp[i - 1][j - 1] + cost
      );
    }
  }

  return dp[rows - 1][cols - 1];
}
