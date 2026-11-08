import { TEXT_LIMITS } from "@/lib/textLimits";
import { CharacterCount } from "@/components/form/CharacterCount";
import { lazy, Suspense, useMemo, useState } from "react";
import { Button } from "../ui/button";
import { Checkbox } from "../ui/checkbox";
import { Input } from "../ui/input";
import { Label } from "../ui/label";
import { Textarea } from "../ui/textarea";
import { DataTable } from "../ui/data-table";
import { ColumnDef, TableMeta } from "@tanstack/react-table";
import { HPUTable } from "@/interfaces";
import { UseMutationResult } from "@tanstack/react-query";
import { AxiosResponse } from "axios";
import { ColorPropertySelector } from "./ColorPropertySelector";
import { MultiSelect } from "../ui/multi-select/multi-select";
import { useProtocols, type Protocol } from "@/hooks/admin/useMasterData";
import { useUiVersion } from "../../ui-version";
import { useProtocolColors, useProtocolUnderstanding } from "@/hooks/useProtocolUnderstanding";

const ProtocolUnderstanding = lazy(() => import("../../ui-new/closure/ProtocolUnderstanding"));

type ClosureFormProps = {
  comments: string;
  setComments: React.Dispatch<React.SetStateAction<string>>;
  isHealingContinued: boolean;
  setIsHealingContinued: React.Dispatch<React.SetStateAction<boolean>>;
  /** Extra sessions held outside the app; added to the recorded session count on closure. */
  additionalSessions?: string;
  setAdditionalSessions?: (value: string) => void;
  selectedProtocols: Protocol[];
  setSelectedProtocols: React.Dispatch<React.SetStateAction<Protocol[]>>;
  closeMutation: UseMutationResult<
    AxiosResponse,
    Error,
    void,
    unknown
  >;
  onClose: () => void;
  healingCaseId: string;
  /** Starts at the closure step: the protocol understanding was already saved on its own page. */
  skipUnderstanding?: boolean;
};
const CLOSURE_FORM_STEPS = 2;

/**
 * TanStack's `flexRender` calls `React.createElement(cellFn, props)` for
 * function cell renderers -- treating the function ITSELF as the component
 * type. If `columns` closes over the color lists/`colorsLoading`/
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
  energisingColors: string[];
  cleansingColors: string[];
  colorsLoading: boolean;
  getEnergisingProperties: (color: string) => string[];
  getCleansingProperties: (color: string) => string[];
  getEnergisingHex: (color: string) => string | undefined;
  getCleansingHex: (color: string) => string | undefined;
}

const ClosureForm = ({
  comments,
  setComments,
  isHealingContinued,
  setIsHealingContinued,
  additionalSessions = "",
  setAdditionalSessions,
  selectedProtocols,
  setSelectedProtocols,
  onClose,
  closeMutation,
  healingCaseId,
  skipUnderstanding,
}: ClosureFormProps) => {
  const { isNewView } = useUiVersion();
  const [currentStep, setCurrentStep] = useState(skipUnderstanding ? 2 : 1);
  const {
    rows: hpuTableData,
    ready: hpuReady,
    loading: hpuLoading,
    updateRow,
    save: saveProtocolUnderstandingMutation,
  } = useProtocolUnderstanding(skipUnderstanding ? "" : healingCaseId);
  const colorProps = useProtocolColors();
  const {
    energisingColors,
    cleansingColors,
    colorsLoading,
    getEnergisingProperties,
    getCleansingProperties,
    getEnergisingHex,
    getCleansingHex,
  } = colorProps;
  const { data: supplementaryProtocols = [], isLoading: protocolsLoading } =
    useProtocols("SUPPLEMENTARY");

  const nextStep = () => {
    if (currentStep < CLOSURE_FORM_STEPS) {
      saveProtocolUnderstandingMutation.mutate(undefined, {
        onSuccess: () => setCurrentStep((s) => s + 1),
      });
    }
  };

  const prevStep = () => {
    if (currentStep > 1) {
      setCurrentStep((s) => s - 1);
    }
  };

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
              colors={meta.cleansingColors}
              getProperties={meta.getCleansingProperties}
              getColorHex={meta.getCleansingHex}
              loading={meta.colorsLoading}
              onChange={(updated) => {
                updateRow(row.index, "cleansingColors", updated);
              }}
            />
          );
        },
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
              colors={meta.energisingColors}
              getProperties={meta.getEnergisingProperties}
              getColorHex={meta.getEnergisingHex}
              loading={meta.colorsLoading}
              onChange={(updated) => {
                updateRow(row.index, "energisingColors", updated);
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
        {currentStep == 1 && isNewView ? (
          <Suspense fallback={null}>
            <ProtocolUnderstanding
              rows={hpuTableData}
              {...colorProps}
              onChange={updateRow}
            />
          </Suspense>
        ) : currentStep == 1 ? (
          <>
            <div className="hidden md:block">
              <DataTable
                columns={columns}
                data={hpuTableData}
                enableSorting={false}
                cellClassName="py-3"
                meta={
                  colorProps as TableMeta<HPUTable>
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
                    colors={energisingColors}
                    getProperties={getEnergisingProperties}
                    getColorHex={getEnergisingHex}
                    loading={colorsLoading}
                    onChange={(updated) =>
                      updateRow(index, "energisingColors", updated)
                    }
                  />

                  <ColorPropertySelector
                    label="Cleansing Colors"
                    value={row.cleansingColors}
                    colors={cleansingColors}
                    getProperties={getCleansingProperties}
                    getColorHex={getCleansingHex}
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
                Recommended Protocols
              </Label>
              <MultiSelect
                options={supplementaryProtocols}
                value={selectedProtocols}
                onChange={setSelectedProtocols}
                getOptionKey={(p: Protocol) => p.id}
                getOptionLabel={(p: Protocol) => p.name}
                placeholder="Select protocols to recommend..."
                loading={protocolsLoading}
              />
            </div>

            <div className="space-y-1.5">
              <Label className="text-sm font-medium text-gray-700">
                Additional Comments
              </Label>
              <Textarea
                value={comments}
                onChange={(e) => setComments(e.target.value)}
                maxLength={TEXT_LIMITS.closureComments}
                className="border px-3 py-2 min-h-[100px] resize-none"
                placeholder="Describe the unique thing identified for this case and healer's understanding of the case"
              />
              <CharacterCount value={comments} max={TEXT_LIMITS.closureComments} />
            </div>

            {setAdditionalSessions && (
              <div className="space-y-1.5">
                <Label
                  htmlFor="additional-sessions"
                  className="text-sm font-medium text-gray-700"
                >
                  Additional sessions (optional)
                </Label>
                <Input
                  id="additional-sessions"
                  type="text"
                  inputMode="numeric"
                  pattern="[0-9]*"
                  value={additionalSessions}
                  onChange={(e) =>
                    setAdditionalSessions(e.target.value.replace(/\D/g, ""))
                  }
                  className="w-32"
                  placeholder="0"
                />
              </div>
            )}

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
          onClick={currentStep === 1 || skipUnderstanding ? onClose : prevStep}
        >
          {currentStep === 1 || skipUnderstanding ? "Cancel" : "Back"}
        </Button>

        {currentStep < CLOSURE_FORM_STEPS ? (
          <Button
            onClick={nextStep}
            disabled={saveProtocolUnderstandingMutation.isPending || !hpuReady}
          >
            {saveProtocolUnderstandingMutation.isPending
              ? "Saving..."
              : hpuLoading
                ? "Loading..."
                : "Next"}
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
