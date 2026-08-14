import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { fetchApiKeyMetadata } from "../../api/authApi";
import type { ApiKeyMetadataResponse } from "../../api/types";
import { useAuth } from "../../auth/AuthContext";
import { useTenant } from "../../auth/TenantContext";
import { BulkWizard } from "../../features/bulk/BulkWizard";

export function OverviewPage() {
  const { user } = useAuth();
  const { tenant, loading, error, reload } = useTenant();
  const [bulk, setBulk] = useState(false);
  const [keyMeta, setKeyMeta] = useState<ApiKeyMetadataResponse | null>(null);

  useEffect(() => {
    void fetchApiKeyMetadata().then(setKeyMeta).catch(() => setKeyMeta(null));
  }, []);

  if (loading) return <div className="loading">Loading workspace…</div>;
  if (error) return <div className="callout errorbox">{error}</div>;
  if (!tenant || !user) return <div className="empty">Tenant not found.</div>;

  const definitionCount = Object.values(tenant.resources).reduce((sum, resource) => sum + resource.entitlementDefinitions.length, 0);

  return (
    <div className="workpage">
      <div className="workhead">
        <div>
          <div className="eyebrow">{tenant.name}</div>
          <h2>Workspace</h2>
          <p>Short operational summary.</p>
        </div>
        <button className="btn primary" type="button" onClick={() => setBulk(true)}>
          Bulk upload / changes
        </button>
      </div>
      <div className="summarygrid">
        <div className="summary">
          <div className="label">Subjects</div>
          <div className="num">{Object.keys(tenant.subjects).length.toLocaleString()}</div>
          <div className="small">{Object.keys(tenant.scopes).length} scopes</div>
        </div>
        <div className="summary">
          <div className="label">Resources</div>
          <div className="num">{Object.keys(tenant.resources).length}</div>
          <div className="small">{definitionCount} definitions</div>
        </div>
        <div className="summary">
          <div className="label">Grants</div>
          <div className="num">{Object.keys(tenant.grants).length}</div>
          <div className="small">live configuration</div>
        </div>
        <div className="summary">
          <div className="label">Gateway</div>
          <div className="num" style={{ fontSize: 20, color: "var(--green)" }}>
            {keyMeta ? "Ready" : "…"}
          </div>
          <div className="small">runtime API</div>
        </div>
      </div>
      <div className="grid2" style={{ marginTop: 14 }}>
        <div className="panel">
          <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "flex-start" }}>
            <div>
              <div className="paneltitle">Company gateway</div>
              <div className="panelsub">Machine-to-machine runtime API.</div>
            </div>
            <span className="authStatus">{keyMeta ? "● API key protected" : "● unknown"}</span>
          </div>
          <div className="code">/api/gateway/tenants/{tenant.id}</div>
          <div className="tinylist">
            <div className="tinyrow">
              <span>Authentication</span>
              <span>X-VERA-API-KEY</span>
            </div>
            <div className="tinyrow">
              <span>Evaluate</span>
              <span>POST /evaluate</span>
            </div>
            <div className="tinyrow">
              <span>Consume / use</span>
              <span>runtime endpoints</span>
            </div>
          </div>
          <Link className="btn small soft" style={{ marginTop: 10, display: "inline-block" }} to="/workspace/integration">
            Manage integration
          </Link>
        </div>
        <div className="panel">
          <div className="paneltitle">Configuration health</div>
          <div className="panelsub">Current registered tenant.</div>
          <div className="tinylist">
            <div className="tinyrow">
              <span>Resources</span>
              <span>{Object.keys(tenant.resources).length}</span>
            </div>
            <div className="tinyrow">
              <span>Grants</span>
              <span>{Object.keys(tenant.grants).length}</span>
            </div>
            <div className="tinyrow">
              <span>Gateway base path</span>
              <span>/api/gateway/tenants/{tenant.id}</span>
            </div>
            <div className="tinyrow">
              <span>Bulk sync</span>
              <span>Available</span>
            </div>
          </div>
        </div>
      </div>
      <div className="note">VERA decides entitlement. Your company still authenticates users and performs the real resource operation.</div>
      {bulk ? <BulkWizard tenantId={user.tenantId} onClose={() => setBulk(false)} onApplied={reload} /> : null}
    </div>
  );
}
