import { useCallback, useEffect, useMemo, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { HPUTable } from "@/interfaces";
import api from "@/lib/axios";
import { extractDetail } from "@/lib/utils";
import { useReferenceData } from "@/hooks/useReferenceData";
import { findColorInCategory, hasColorCategory, type ColorCategory } from "@/lib/colorCategory";

/**
 * The healer's protocol understanding for a case: one row per scanned chakra, with what was saved
 * before. An empty `healingCaseId` loads nothing.
 */
export function useProtocolUnderstanding(healingCaseId: string) {
  const [rows, setRows] = useState<HPUTable[]>([]);
  // Saving must not run before the saved selections (and scanned chakras) have loaded: saving the
  // still-empty table would overwrite what the healer filled in earlier.
  const [loading, setLoading] = useState(true);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!healingCaseId) return;
    /**
     * Reopening an already-filled ACTIVE case's closure popup should show
     * what was previously saved, editable in place -- the backend already
     * upserts by healingCaseId (saveFromHealerSelection), so fetching this
     * alongside the scanned chakras and merging by chakra name is the only
     * piece needed for "edit, not duplicate". A chakra with no prior
     * selection (or a first-ever fill) starts empty for the healer to fill in.
     */
    const fetchScannedChakrasAndExisting = async () => {
      try {
        const [chakrasRes, existingRes /*, prefillRes */] = await Promise.all([
          api.get(`/api/v1/sessions/${healingCaseId}/chakras`),
          api.get(`/api/v1/cases/${healingCaseId}/protocol-understanding`),
          // NOTE: Design not yet completed, so commenting this out.
          // Pre-fill is a convenience: if it fails the form still opens for manual entry.
          // api
          //   .get(`/api/v1/cases/${healingCaseId}/protocol-understanding/prefill`)
          //   .catch(() => null),
        ]);
        const data = chakrasRes?.data?.data;
        const existingSelections: HPUTable[] =
          existingRes?.data?.chakraSelections ?? [];
        const existingByChakra = new Map(
          existingSelections.map((selection) => [selection.chakra, selection])
        );
        // NOTE: Design not yet completed, so commenting this out.
        // const prefill: HPUTable[] =
        //   prefillRes?.data?.chakraSelections ?? [];
        // const prefillByChakra = new Map(
        //   prefill.map((row) => [row.chakra, row])
        // );

        const tableData: HPUTable[] = data.map((chakraName: string) => {
          const existing = existingByChakra.get(chakraName);
          // const existing =
          //   existingByChakra.get(chakraName) ?? prefillByChakra.get(chakraName);
          return {
            chakra: chakraName,
            energisingColors: existing?.energisingColors ?? [],
            cleansingColors: existing?.cleansingColors ?? [],
            reason: existing?.reason,
          };
        });
        setRows(tableData);
        setReady(true);
      } catch (err) {
        toast.error(
          extractDetail(err) || "Failed to fetch Scanned Chakras, try again",
          {
            duration: 5000,
          }
        );
      } finally {
        setLoading(false);
      }
    };

    fetchScannedChakrasAndExisting();
  }, [healingCaseId]);

  const save = useMutation({
    mutationFn: () =>
      api.post(`/api/v1/cases/${healingCaseId}/protocol-understanding`, {
        chakraSelections: rows,
      }),
    onError: (err) =>
      toast.error(
        extractDetail(err) ||
          "Failed to save protocol understanding, try again",
        {
          duration: 5000,
        }
      ),
  });

  const updateRow = useCallback(
    <K extends keyof HPUTable>(index: number, field: K, value: HPUTable[K]) => {
      setRows((prev) =>
        prev.map((row, i) => (i === index ? { ...row, [field]: value } : row))
      );
    },
    []
  );

  return { rows, ready, loading, updateRow, save };
}

/** The prana colors each protocol-understanding column offers, with their properties and swatches. */
export function useProtocolColors() {
  const {
    colors,
    loading: colorsLoading,
    error: colorsError,
  } = useReferenceData();

  useEffect(() => {
    if (colorsError) {
      toast.error("Failed to load prana colors, try reopening this dialog", {
        duration: 5000,
      });
    }
  }, [colorsError]);

  // Names are unique per category, not overall: each column lists, and looks properties and
  // swatches up in, its own category's colors.
  const namesIn = useCallback(
    (category: ColorCategory) =>
      Array.from(new Set(colors.filter((c) => hasColorCategory(c, category)).map((c) => c.name))),
    [colors]
  );
  const energisingColors = useMemo(() => namesIn("ENERGISING"), [namesIn]);
  const cleansingColors = useMemo(() => namesIn("CLEANSING"), [namesIn]);

  const getEnergisingProperties = useCallback(
    (color: string) => findColorInCategory(colors, color, "ENERGISING")?.properties ?? [],
    [colors]
  );
  const getCleansingProperties = useCallback(
    (color: string) => findColorInCategory(colors, color, "CLEANSING")?.properties ?? [],
    [colors]
  );
  const getEnergisingHex = useCallback(
    (color: string) => findColorInCategory(colors, color, "ENERGISING")?.colorHex,
    [colors]
  );
  const getCleansingHex = useCallback(
    (color: string) => findColorInCategory(colors, color, "CLEANSING")?.colorHex,
    [colors]
  );

  return {
    energisingColors,
    cleansingColors,
    colorsLoading,
    getEnergisingProperties,
    getCleansingProperties,
    getEnergisingHex,
    getCleansingHex,
  };
}
