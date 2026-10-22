/** "Chief Complaint" -> "Chief complaint"; acronyms (BP, PDF) and bracketed text stay as written. */
export function sentenceCase(label: string): string {
  let inBracket = false;
  return label
    .split(" ")
    .map((word, i) => {
      if (word.startsWith("(")) inBracket = true;
      const keep = i === 0 || inBracket || (word.length > 1 && word === word.toUpperCase());
      if (word.endsWith(")")) inBracket = false;
      return keep ? word : word.toLowerCase();
    })
    .join(" ");
}
