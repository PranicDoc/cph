import { useState } from "react";

/** How many filled-in chakras are collected before the protocol understanding is sent. */
export const HPU_BATCH_KEY = "hl.hpu.batchSize";
const DEFAULT_CHOICE = "5";

export const HPU_BATCH_CHOICES = [
  { value: "5", label: "every 5 chakras" },
  { value: "10", label: "every 10 chakras" },
  { value: "all", label: "all chakras" },
];

const isUsable = (v: string | null): v is string => v === "all" || /^[1-9]\d*$/.test(v ?? "");

function read(): string {
  try {
    const stored = localStorage.getItem(HPU_BATCH_KEY);
    return isUsable(stored) ? stored : DEFAULT_CHOICE;
  } catch {
    return DEFAULT_CHOICE; // storage unavailable: use the default
  }
}

/**
 * The healer's batch size, remembered in this browser. Only this setting is stored there: what the healer
 * writes about a patient is never put in browser storage.
 */
export function useHpuBatchSize() {
  const [choice, setChoice] = useState(read);
  return {
    choice,
    /** "all" waits for the last chakra. */
    size: choice === "all" ? Infinity : Number(choice),
    setChoice: (next: string) => {
      setChoice(next);
      try {
        localStorage.setItem(HPU_BATCH_KEY, next);
      } catch {
        // storage unavailable: the choice lasts until the page is left
      }
    },
  };
}
