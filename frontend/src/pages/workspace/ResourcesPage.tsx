import { useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import { useTenant } from "../../auth/TenantContext";
import { BulkWizard } from "../../features/bulk/BulkWizard";
import { ResourceExplorer } from "../../features/resources/ResourceExplorer";

export function ResourcesPage() {
  const { user } = useAuth();
  const { tenant, loading, error, reload } = useTenant();
  const [bulk, setBulk] = useState(false);
  if (loading) return <div className="loading">Loading resources…</div>;
  if (error) return <div className="callout errorbox">{error}</div>;
  if (!tenant || !user) return null;
  return (
    <div className="workpage">
      <div className="workhead">
        <div>
          <div className="eyebrow">Resources</div>
          <h2>Resource catalog</h2>
          <p>Open a resource, then expand an entitlement to inspect its grants and live users.</p>
        </div>
        <button className="btn primary" type="button" onClick={() => setBulk(true)}>
          Bulk resource change
        </button>
      </div>
      <ResourceExplorer tenant={tenant} onChanged={reload} />
      {bulk ? <BulkWizard tenantId={user.tenantId} initialDomain="resources" onClose={() => setBulk(false)} onApplied={reload} /> : null}
    </div>
  );
}
