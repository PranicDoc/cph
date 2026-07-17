import { useCallback, useEffect, useMemo, useState } from "react";
import { Button } from "../ui/button";
import { Checkbox } from "../ui/checkbox";
import { Label } from "../ui/label";
import { Textarea } from "../ui/textarea";
import { DataTable } from "../ui/data-table";
import { ColumnDef } from "@tanstack/react-table";
import { HPUTable } from "@/interfaces";
import api from "@/lib/axios";
import { toast } from "sonner";
import { UseMutationResult } from "@tanstack/react-query";
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
    const fetchScannedChakras = async () => {
      try {
        const res = await api.get(
          `/api/v1/sessions/${healingCaseId}/chakras`
        );
        const { data } = res?.data;
        const tableData: HPUTable[] = data.map((chakraName: string) => ({
          chakra: chakraName,
          energisingColors: [],
          activationColors: [],
        }));
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

    fetchScannedChakras();
  }, [healingCaseId]);

  const nextStep = () => {
    if (currentStep < CLOSURE_FORM_STEPS) {
      setCurrentStep((s) => s + 1);
    }
  };

  const prevStep = () => {
    if (currentStep > 1) {
      setCurrentStep((s) => s - 1);
    }
  };

  const { colors, loading: colorsLoading, error: colorsError } = useReferenceData();

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

  const updateRow = useCallback(
    <K extends keyof HPUTable>(index: number, field: K, value: HPUTable[K]) => {
      setHpuTableData((prev) =>
        prev.map((row, i) => (i === index ? { ...row, [field]: value } : row))
      );
    },
    []
  );

  const columns: ColumnDef<HPUTable>[] = [
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
      cell: ({ row }) => (
        <ColorPropertySelector
          value={row.original.energisingColors}
          colors={allColors}
          getProperties={fetchPropertiesForColor}
          loading={colorsLoading}
          onChange={(updated) => {
            updateRow(row.index, "energisingColors", updated);
          }}
        />
      ),
    },
    {
      accessorKey: "activationColors",
      header: "Activation Colors",
      meta: {
        headClassName: "min-w-[200px]",
        cellClassName: "align-top whitespace-normal",
      },
      cell: ({ row }) => (
        <ColorPropertySelector
          value={row.original.activationColors}
          colors={allColors}
          getProperties={fetchPropertiesForColor}
          loading={colorsLoading}
          onChange={(updated) => {
            updateRow(row.index, "activationColors", updated);
          }}
        />
      ),
    },
  ];

  return (
    <>
      <div className="space-y-4 py-2 max-h-[65vh] overflow-y-auto">
        {currentStep == 1 ? (
          <DataTable
            columns={columns}
            data={hpuTableData}
            enableSorting={false}
            cellClassName="py-3"
          />
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
          <Button onClick={nextStep}>Next</Button>
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
