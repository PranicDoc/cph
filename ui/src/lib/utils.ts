import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";
import type { CreateSessionPayload, ProtocolEntryRequest } from "../interfaces";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function setCookie(name: string, value: string, days = 365) {
  const expires = new Date();
  expires.setTime(expires.getTime() + days * 24 * 60 * 60 * 1000);
  document.cookie = `${name}=${encodeURIComponent(
    value
  )};expires=${expires.toUTCString()};path=/`;
}

export function getCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp("(^| )" + name + "=([^;]+)"));
  return match ? decodeURIComponent(match[2]) : null;
}

// utils/helpers.ts
export const sanitizeInput = (text: string): string =>
  text.trim().replace(/[<>]/g, "");

export const getCurrentDateAndTime = () => {
  const now = new Date();
  const pad = (n: number) => n.toString().padStart(2, "0");

  const currentDate = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(
    now.getDate()
  )}`;
  const startTime = `${pad(now.getHours())}:${pad(now.getMinutes())}`;
  const endNow = new Date(now.getTime() + 60 * 60 * 1000);
  const endTime = `${pad(endNow.getHours())}:${pad(endNow.getMinutes())}`;

  return { currentDate, startTime, endTime };
};

export const getChakraColor = (chakraName: string): string => {
  const colorMap: Record<string, string> = {
    "Crown Chakra": "bg-purple-500",
    "Forehead Chakra": "bg-indigo-500",
    "Ajna Chakra": "bg-indigo-500",
    "Throat Chakra": "bg-blue-500",
    "Front Heart Chakra": "bg-green-500",
    "Back Heart Chakra": "bg-green-500",
    "Front Solar Plexus Chakra": "bg-yellow-500",
    "Back Solar Plexus Chakra": "bg-yellow-500",
    "Navel Chakra": "bg-orange-500",
    "Meng Mein Chakra": "bg-orange-500",
    "Sex Chakra": "bg-red-500",
    "Basic Chakra": "bg-red-500",
    "Front Spleen Chakra": "bg-pink-500",
    "Back Spleen Chakra": "bg-pink-500",
  };
  const fallbackColors = [
    "bg-gray-500",
    "bg-cyan-500",
    "bg-teal-500",
    "bg-amber-500",
  ];
  for (const [key, color] of Object.entries(colorMap)) {
    if (chakraName.includes(key) || key.includes(chakraName)) return color;
  }
  const hash = chakraName
    .split("")
    .reduce((acc, ch) => acc + ch.charCodeAt(0), 0);
  return fallbackColors[hash % fallbackColors.length];
};

// Constructs payload expected by backend CreateSessionRequest DTO
export function constructHealingSessionPayload({
  caseId,
  sessionDate,
  startTime,
  endTime,
  preScanData,
  postScanData,
  symptomFeedbacks,
  protocolEntries,
  feedbackType,
  feedbackText,
  patientRemarks,
}: {
  caseId: string;
  sessionDate: string;
  startTime: string;
  endTime: string;
  preScanData: Array<{
    chakraId: string;
    chakraName: string;
    energyLevel?: string;
    activationLevel?: string;
    notes?: string;
  }>;
  postScanData: Array<{
    chakraId: string;
    chakraName: string;
    energyLevel?: string;
    activationLevel?: string;
    notes?: string;
  }>;
  symptomFeedbacks: Array<{
    caseSymptomId: string;
    rating: number;
    notes?: string;
  }>;
  protocolEntries: ProtocolEntryRequest[];
  feedbackType: "INTERIM" | "FINAL";
  feedbackText: string;
  patientRemarks: string;
}): CreateSessionPayload {
  return {
    caseId,
    sessionDate,
    startTime,
    endTime,
    scans: [
      {
        scanType: "PRE",
        notes: "",
        readings: preScanData.map((reading) => ({
          chakraId: reading.chakraId,
          chakraName: reading.chakraName,
          energyLevel: reading.energyLevel || "N",
          activationLevel: reading.activationLevel || "N",
          notes: reading.notes || "",
        })),
      },
      {
        scanType: "POST",
        notes: "",
        readings: postScanData.map((reading) => ({
          chakraId: reading.chakraId,
          chakraName: reading.chakraName,
          energyLevel: reading.energyLevel || "N",
          activationLevel: reading.activationLevel || "N",
          notes: reading.notes || "",
        })),
      },
    ],
    symptomFeedbacks: symptomFeedbacks.map((fb) => ({
      caseSymptomId: fb.caseSymptomId,
      rating: fb.rating,
      notes: fb.notes || "",
    })),
    qualitativeFeedbacks: [
      {
        feedbackType,
        feedbackText,
        patientRemarks,
      },
    ],
    protocolEntries: protocolEntries.map((entry) => ({
      chakraId: entry.chakraId || null,
      targetArea: entry.targetArea,
      technique: entry.technique,
      notes: entry.notes || "",
      colors: entry.colors.map((color) => ({
        colorId: color.colorId,
        usageType: color.usageType,
      })),
    })),
  };
}
