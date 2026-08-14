import { useEffect, useState } from "react";
import { fetchUsageHistory } from "../../api/historyApi";
import type { BucketUsage, EventUsage, ResourceUsageHistory } from "../../api/types";
import { useAuth } from "../../auth/AuthContext";
import { formatDate, formatNumber } from "../../features/entitlement/formatValue";

interface Row {
  id: string;
  time: string;
  type: "BUCKET" | "EVENT";
  subject: string;
  resource: string;
  entitlement: string;
  grant: string;
  usage: string;
}

export function UsageHistoryPage() {
  const { user } = useAuth();
  const [rows, setRows] = useState<Row[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!user) return;
    void fetchUsageHistory(user.tenantId)
      .then((result) => setRows(flatten(result.resources)))
      .catch((err: unknown) => setError(err instanceof Error ? err.message : "Failed to load usage history."));
  }, [user]);

  return (
    <div className="workpage">
      <div className="workhead">
        <div>
          <div className="eyebrow">Audit</div>
          <h2>Usage history</h2>
          <p>Successful committed use, with exact events and 5-minute numeric buckets.</p>
        </div>
      </div>
      {error ? <div className="callout errorbox">{error}</div> : null}
      {rows.length === 0 && !error ? (
        <div className="empty">No usage history yet.</div>
      ) : (
        <div className="tablewrap">
          <table>
            <thead>
              <tr>
                <th>Time</th>
                <th>Type</th>
                <th>Subject</th>
                <th>Resource</th>
                <th>Entitlement</th>
                <th>Grant</th>
                <th>Usage</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td>{row.time}</td>
                  <td>
                    <span className={`badge ${row.type === "BUCKET" ? "bbucket" : "bevent"}`}>{row.type}</span>
                  </td>
                  <td>{row.subject}</td>
                  <td>{row.resource}</td>
                  <td>{row.entitlement}</td>
                  <td>{row.grant}</td>
                  <td>{row.usage}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function flatten(resources: ResourceUsageHistory[]): Row[] {
  const rows: Row[] = [];
  for (const resource of resources) {
    for (const entitlement of resource.entitlements) {
      for (const grant of entitlement.grants) {
        for (const record of grant.usage) {
          if (record.type === "BUCKET") {
            const bucket = record as BucketUsage;
            rows.push({
              id: `${resource.resourceId}-${grant.grantId}-${bucket.subjectId}-${bucket.bucketStart}`,
              time: `${formatDate(bucket.bucketStart)}–${formatDate(bucket.bucketEnd)}`,
              type: "BUCKET",
              subject: bucket.subjectNameAtTime,
              resource: resource.resourceName ?? resource.resourceId,
              entitlement: entitlement.entitlementKey,
              grant: grant.grantTargetNameAtTime,
              usage: `${formatNumber(bucket.totalConsumed)} · ${bucket.operationCount} ops`,
            });
          } else {
            const event = record as EventUsage;
            rows.push({
              id: `${resource.resourceId}-${grant.grantId}-${event.subjectId}-${event.occurredAt}`,
              time: formatDate(event.occurredAt),
              type: "EVENT",
              subject: event.subjectNameAtTime,
              resource: resource.resourceName ?? resource.resourceId,
              entitlement: entitlement.entitlementKey,
              grant: grant.grantTargetNameAtTime,
              usage: JSON.stringify(event.usedValue),
            });
          }
        }
      }
    }
  }
  return rows;
}
