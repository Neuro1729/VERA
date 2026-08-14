import { useEffect, useMemo, useState } from "react";
import { fetchEntitlementHistory } from "../../api/historyApi";
import type { EntitlementHistoryEvent } from "../../api/types";
import { useAuth } from "../../auth/AuthContext";
import { useTenant } from "../../auth/TenantContext";
import { formatDate, formatEntitlementValue } from "../../features/entitlement/formatValue";

export function EntitlementHistoryPage() {
  const { user } = useAuth();
  const { tenant } = useTenant();
  const [changes, setChanges] = useState<EntitlementHistoryEvent[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [type, setType] = useState("ALL");
  const [resource, setResource] = useState("ALL");
  const [key, setKey] = useState("ALL");

  useEffect(() => {
    if (!user) return;
    void fetchEntitlementHistory(user.tenantId)
      .then((result) => setChanges([...result.changes].reverse()))
      .catch((err: unknown) => setError(err instanceof Error ? err.message : "Failed to load history."));
  }, [user]);

  const filtered = useMemo(
    () =>
      changes.filter((change) => {
        if (type !== "ALL" && change.changeType !== type) return false;
        if (resource !== "ALL" && change.resourceId !== resource) return false;
        if (key !== "ALL" && change.entitlementKey !== key) return false;
        return true;
      }),
    [changes, key, resource, type],
  );

  const resources = [...new Set(changes.map((change) => change.resourceId))];
  const keys = [...new Set(changes.map((change) => change.entitlementKey))];

  return (
    <div className="workpage">
      <div className="workhead">
        <div>
          <div className="eyebrow">Audit</div>
          <h2>Entitlement history</h2>
          <p>Append-only history of what changed in access configuration.</p>
        </div>
      </div>
      {error ? <div className="callout errorbox">{error}</div> : null}
      <div className="filterrow">
        <select value={type} onChange={(e) => setType(e.target.value)} aria-label="Change type">
          <option value="ALL">All changes</option>
          <option>CREATED</option>
          <option>UPDATED</option>
          <option>REMOVED</option>
        </select>
        <select value={resource} onChange={(e) => setResource(e.target.value)} aria-label="Resource">
          <option value="ALL">All resources</option>
          {resources.map((id) => (
            <option key={id}>{id}</option>
          ))}
        </select>
        <select value={key} onChange={(e) => setKey(e.target.value)} aria-label="Entitlement key">
          <option value="ALL">All entitlement keys</option>
          {keys.map((id) => (
            <option key={id}>{id}</option>
          ))}
        </select>
      </div>
      {filtered.length === 0 ? (
        <div className="empty">No entitlement history yet.</div>
      ) : (
        <div className="tablewrap">
          <table>
            <thead>
              <tr>
                <th>Time</th>
                <th>Change</th>
                <th>Resource</th>
                <th>Entitlement</th>
                <th>Target</th>
                <th>Grant</th>
                <th>Value</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((change) => (
                <tr key={change.id}>
                  <td>{formatDate(change.changedAt)}</td>
                  <td>
                    <span className={`badge ${change.changeType === "CREATED" ? "badd" : change.changeType === "REMOVED" ? "brem" : "bupd"}`}>
                      {change.changeType}
                    </span>
                  </td>
                  <td>{tenant?.resources[change.resourceId]?.name ?? change.resourceId}</td>
                  <td>{change.entitlementKey}</td>
                  <td>
                    {change.target.type}:{change.target.id}
                  </td>
                  <td>{change.newGrantId ?? change.previousGrantId ?? "—"}</td>
                  <td>{formatEntitlementValue(change.newValue ?? change.oldValue)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
