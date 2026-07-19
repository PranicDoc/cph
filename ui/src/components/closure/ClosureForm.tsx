import { useCallback, useEffect, useMemo, useState } from "react";
import { Button } from "../ui/button";
import { Checkbox } from "../ui/checkbox";
import { Label } from "../ui/label";
import { Textarea } from "../ui/textarea";
import { DataTable } from "../ui/data-table";
import { ColumnDef, TableMeta } from "@tanstack/react-table";
import { HPUTable } from "@/interfaces";
import api from "@/lib/axios";
import { toast } from "sonner";
import { UseMutationResult, useMutation } from "@tanstack/react-query";
import { AxiosResponse } from "axios";
import { extractDetail } from "@/lib/utils";
import { ColorPropertySelector } from "./ColorPropertySelector";
import { useReferenceData } from "@/hooks/useReferenceData";

type ClosureFormProps = {
  comments: string;
  setComments: React.Dispatch<React.SetStateAction<string>>;
  isHealingContinued: boolean;
  setIsHealingContinued: React.Dispatch<React.SetStateAction<boolean>>;
  closeMutation: UseMutationResult<
    AxiosResponse<any, any, {}>,
    Error,
    void,
    unknown
  >;
  onClose: () => void;
  healingCaseId: string;
};
const CLOSURE_FORM_STEPS = 2;

/**
 * TanStack's `flexRender` calls `React.createElement(cellFn, props)` for
 * function cell renderers -- treating the function ITSELF as the component
 * type. If `columns` closes over `allColors`/`colorsLoading`/
 * `fetchPropertiesForColor` directly, every time those change (e.g. the
 * color reference-data fetch resolving after the table has already
 * rendered rows) the cell function reference changes too, so React sees a
 * "new component type" and fully unmounts/remounts the cell -- destroying
 * MultiSelect's internal open/draft state mid-interaction. Keeping
 * `columns` fully stable and passing the changing values through TanStack's
 * `meta` (read fresh on every render via `table.options.meta`) avoids that
 * entirely: the cell function reference never changes, so React updates
 * the existing component instance instead of remounting it.
 */
interface HpuTableMeta {
  allColors: string[];
  colorsLoading: boolean;
  fetchPropertiesForColor: (color: string) => string[];
  fetchColorHex: (color: string) => string | undefined;
}

const ClosureForm = ({
  comments,
  setComments,
  isHealingContinued,
  setIsHealingContinued,
  onClose,
  closeMutation,
  healingCaseId,
}: ClosureFormProps) => {
  const [currentStep, setCurrentStep] = useState(1);
  const [hpuTableData, setHpuTableData] = useState<HPUTable[]>([]);

  useEffect(() => {
    /**
     * Reopening an already-filled ACTIVE case's closure popup should show
     * what was previously saved, editable in place -- the backend already
     * upserts by healingCaseId (saveFromHealerSelection), so fetching this
     * alongside the scanned chakras and merging by chakra name is the only
     * piece needed for "edit, not duplicate". A chakra with no prior
     * selection (or a first-ever fill) just gets []/[], same as before.
     */
    const fetchScannedChakrasAndExisting = async () => {
      try {
        const [chakrasRes, existingRes] = await Promise.all([
          api.get(`/api/v1/sessions/${healingCaseId}/chakras`),
          api.get(`/api/v1/cases/${healingCaseId}/protocol-understanding`),
        ]);
        const { data } = chakrasRes?.data;
        const existingSelections: HPUTable[] =
          existingRes?.data?.chakraSelections ?? [];
        const existingByChakra = new Map(
          existingSelections.map((selection) => [selection.chakra, selection])
        );

        const tableData: HPUTable[] = data.map((chakraName: string) => {
          const existing = existingByChakra.get(chakraName);
          return {
            chakra: chakraName,
            energisingColors: existing?.energisingColors ?? [],
            cleansingColors: existing?.cleansingColors ?? [],
          };
        });
        setHpuTableData(tableData);
      } catch (err) {
        toast.error(
          extractDetail(err) || "Failed to fetch Scanned Chakras, try again",
          {
            duration: 5000,
          }
        );
      }
    };

    fetchScannedChakrasAndExisting();
  }, [healingCaseId]);

  const saveProtocolUnderstandingMutation = useMutation({
    mutationFn: () =>
      api.post(`/api/v1/cases/${healingCaseId}/protocol-understanding`, {
        chakraSelections: hpuTableData,
      }),
    onSuccess: () => {
      setCurrentStep((s) => s + 1);
    },
    onError: (err) =>
      toast.error(
        extractDetail(err) ||
          "Failed to save protocol understanding, try again",
        {
          duration: 5000,
        }
      ),
  });

  const nextStep = () => {
    if (currentStep < CLOSURE_FORM_STEPS) {
      saveProtocolUnderstandingMutation.mutate();
    }
  };

  const prevStep = () => {
    if (currentStep > 1) {
      setCurrentStep((s) => s - 1);
    }
  };

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

  const allColors = useMemo(() => colors.map((c) => c.name), [colors]);

  const fetchPropertiesForColor = useCallback(
    (color: string) => {
      const match = colors.find((c) => c.name === color);
      return match?.properties ?? [];
    },
    [colors]
  );

  const fetchColorHex = useCallback(
    (color: string) => colors.find((c) => c.name === color)?.colorHex,
    [colors]
  );

  const updateRow = useCallback(
    <K extends keyof HPUTable>(index: number, field: K, value: HPUTable[K]) => {
      setHpuTableData((prev) =>
        prev.map((row, i) => (i === index ? { ...row, [field]: value } : row))
      );
    },
    []
  );

  const columns = useMemo<ColumnDef<HPUTable>[]>(
    () => [
      {
        accessorKey: "chakra",
        header: "Chakra",
        meta: {
          headClassName: "w-20",
          cellClassName: "align-top whitespace-normal text-sm",
        },
        cell: ({ row }) => (
          <span className="font-medium">{row.original.chakra}</span>
        ),
      },
      {
        accessorKey: "energisingColors",
        header: "Energising Colors",
        meta: {
          headClassName: "min-w-[200px]",
          cellClassName: "align-top whitespace-normal",
        },
        cell: ({ row, table }) => {
          const meta = table.options.meta as HpuTableMeta;
          return (
            <ColorPropertySelector
              value={row.original.energisingColors}
              colors={meta.allColors}
              getProperties={meta.fetchPropertiesForColor}
              getColorHex={meta.fetchColorHex}
              loading={meta.colorsLoading}
              onChange={(updated) => {
                updateRow(row.index, "energisingColors", updated);
              }}
            />
          );
        },
      },
      {
        accessorKey: "cleansingColors",
        header: "Cleansing Colors",
        meta: {
          headClassName: "min-w-[200px]",
          cellClassName: "align-top whitespace-normal",
        },
        cell: ({ row, table }) => {
          const meta = table.options.meta as HpuTableMeta;
          return (
            <ColorPropertySelector
              value={row.original.cleansingColors}
              colors={meta.allColors}
              getProperties={meta.fetchPropertiesForColor}
              getColorHex={meta.fetchColorHex}
              loading={meta.colorsLoading}
              onChange={(updated) => {
                updateRow(row.index, "cleansingColors", updated);
              }}
            />
          );
        },
      },
    ],
    [updateRow]
  );

  return (
    <>
      <div className="space-y-4 py-2 max-h-[65vh] md:max-h-[80vh] overflow-y-auto">
        {currentStep == 1 ? (
          <>
            <div className="hidden md:block">
              <DataTable
                columns={columns}
                data={hpuTableData}
                enableSorting={false}
                cellClassName="py-3"
                meta={
                  {
                    allColors,
                    colorsLoading,
                    fetchPropertiesForColor,
                    fetchColorHex,
                  } as TableMeta<HPUTable>
                }
              />
            </div>

            <div className="md:hidden space-y-3">
              {hpuTableData.map((row, index) => (
                <div
                  key={row.chakra}
                  className="border border-border rounded-xl p-4 space-y-4"
                >
                  <span className="font-medium">{row.chakra}</span>

                  <ColorPropertySelector
                    label="Energising Colors"
                    value={row.energisingColors}
                    colors={allColors}
                    getProperties={fetchPropertiesForColor}
                    getColorHex={fetchColorHex}
                    loading={colorsLoading}
                    onChange={(updated) =>
                      updateRow(index, "energisingColors", updated)
                    }
                  />

                  <ColorPropertySelector
                    label="Cleansing Colors"
                    value={row.cleansingColors}
                    colors={allColors}
                    getProperties={fetchPropertiesForColor}
                    getColorHex={fetchColorHex}
                    loading={colorsLoading}
                    onChange={(updated) =>
                      updateRow(index, "cleansingColors", updated)
                    }
                  />
                </div>
              ))}
            </div>
          </>
        ) : (
          <>
            <div className="space-y-1.5">
              <Label className="text-sm font-medium text-gray-700">
                Additional Comments
              </Label>
              <Textarea
                value={comments}
                onChange={(e) => setComments(e.target.value)}
                className="border px-3 py-2 min-h-[100px] resize-none"
                placeholder="Add any final notes or observations here..."
              />
            </div>

            <Label className="flex items-center gap-2 cursor-pointer w-fit">
              <Checkbox
                checked={isHealingContinued}
                onCheckedChange={(checked) =>
                  setIsHealingContinued(checked === true)
                }
              />
              <span className="text-sm font-medium text-gray-700">
                Is healing continued?
              </span>
            </Label>
          </>
        )}
      </div>
      <div className="flex justify-end gap-3 pt-2">
        <Button
          variant="outline"
          onClick={currentStep === 1 ? onClose : prevStep}
        >
          {currentStep === 1 ? "Cancel" : "Back"}
        </Button>

        {currentStep < CLOSURE_FORM_STEPS ? (
          <Button
            onClick={nextStep}
            disabled={saveProtocolUnderstandingMutation.isPending}
          >
            {saveProtocolUnderstandingMutation.isPending ? "Saving..." : "Next"}
          </Button>
        ) : (
          <Button
            onClick={() => closeMutation.mutate()}
            disabled={closeMutation.isPending}
          >
            {closeMutation.isPending ? "Closing..." : "Close Case"}
          </Button>
        )}
      </div>
    </>
  );
};

export default ClosureForm;
