import type { Protocol } from "../hooks/admin/useMasterData";

/** Dropdown row for a protocol: its name, with the source book and page underneath. */
export function ProtocolOption({ protocol }: { protocol: Protocol }) {
  return (
    <span className="flex flex-col leading-tight">
      <span>{protocol.name}</span>
      {protocol.sourceBook && (
        <span className="text-xs text-muted-foreground">
          {protocol.sourceBook}, p.{protocol.pageNumber}
        </span>
      )}
    </span>
  );
}
