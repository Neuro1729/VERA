import { useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import { useTenant } from "../../auth/TenantContext";
import { BulkWizard } from "../../features/bulk/BulkWizard";
import { ScopeTree } from "../../features/organization/ScopeTree";

export function OrganizationPage() {
  const { user } = useAuth();
  const { tenant, loading, error, reload } = useTenant();
  const [bulk, setBulk] = useState(false);
  if (loading) return <div className="loading">Loading organization…</div>;
  if (error) return <div className="callout errorbox">{error}</div>;
  if (!tenant || !user) return null;
  return (
    <div className="workpage">
      <div className="workhead">
        <div>
          <div className="eyebrow">Organization</div>
          <h2>Structure</h2>
          <p>Expand only the scopes you want to inspect or edit.</p>
        </div>
        <button className="btn primary" type="button" onClick={() => setBulk(true)}>
          Bulk organization change
        </button>
      </div>
      <ScopeTree tenant={tenant} onChanged={reload} />
      {bulk ? (
        <BulkWizard tenantId={user.tenantId} initialDomain="organization" onClose={() => setBulk(false)} onApplied={reload} />
      ) : null}
    </div>
  );
}
