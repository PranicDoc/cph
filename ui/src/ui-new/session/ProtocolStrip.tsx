import type { Protocol } from "../../hooks/admin/useMasterData";
import { isLegacyProtocol } from "../../lib/protocolSelection";

/** Protocols chosen for this session, shown above the pre and post scan so they are visible on every screen size. */
export default function ProtocolStrip({ protocols }: { protocols: Protocol[] }) {
  if (protocols.length === 0) return null;
  return (
    <section className="surface card" aria-label="Protocols for this session" style={{ padding: "14px 18px" }}>
      <div className="small muted" style={{ fontWeight: 550, marginBottom: 6 }}>Protocols for this session</div>
      {protocols.map((p, i) => (
        <div key={p.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, padding: "8px 0", borderTop: i ? "1px solid var(--hair)" : undefined }}>
          <div style={{ minWidth: 0 }}>
            <div style={{ fontWeight: 600 }}>{p.name}</div>
            {!isLegacyProtocol(p) && p.sourceBook && <div className="small muted" style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{p.sourceBook}</div>}
          </div>
          {!isLegacyProtocol(p) && p.pageNumber ? <span className="badge" style={{ flex: "none" }}>p. {p.pageNumber}</span> : null}
        </div>
      ))}
    </section>
  );
}
