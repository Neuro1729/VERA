import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchApiKeyMetadata, rotateApiKey } from "../../api/authApi";
import { ApiClientError } from "../../api/client";
import type { ApiKeyMetadataResponse } from "../../api/types";
import { useAuth } from "../../auth/AuthContext";
import { Modal } from "../../components/common/Modal";
import { useToast } from "../../components/common/Toast";
import { formatDate } from "../../features/entitlement/formatValue";

export function IntegrationPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [meta, setMeta] = useState<ApiKeyMetadataResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirmRotate, setConfirmRotate] = useState(false);
  const [rawKey, setRawKey] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void fetchApiKeyMetadata()
      .then(setMeta)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : "Failed to load API key metadata."));
  }, []);

  if (!user) return null;

  async function rotate() {
    setBusy(true);
    try {
      const result = await rotateApiKey();
      setRawKey(result.apiKey);
      setMeta(await fetchApiKeyMetadata());
      setConfirmRotate(false);
      toast.show("API key rotated.");
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : "Rotation failed.");
    } finally {
      setBusy(false);
    }
  }

  const masked = meta ? `${meta.displayPrefix.replace("...", ".")}••••••••` : "";

  return (
    <div className="workpage">
      <div className="workhead">
        <div>
          <div className="eyebrow">Security & integration</div>
          <h2>Connect {user.tenantId} to VERA</h2>
          <p>Human administration uses a session. Runtime calls use a separate tenant-bound API key.</p>
        </div>
      </div>
      {error ? <div className="callout errorbox">{error}</div> : null}
      <div className="integrationGrid">
        <div className="panel">
          <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "flex-start" }}>
            <div>
              <div className="paneltitle">Runtime API key</div>
              <div className="panelsub">Used only by your backend for /api/gateway/**.</div>
            </div>
            <span className="authStatus">{meta ? "● Active" : "● unknown"}</span>
          </div>
          <div className="keyPreview">{meta ? masked : "Loading metadata…"}</div>
          <div className="tinylist">
            <div className="tinyrow">
              <span>Public id</span>
              <span>{meta?.publicId ?? "—"}</span>
            </div>
            <div className="tinyrow">
              <span>Created</span>
              <span>{meta ? formatDate(meta.createdAt) : "—"}</span>
            </div>
            <div className="tinyrow">
              <span>Last rotated</span>
              <span>{meta?.rotatedAt ? formatDate(meta.rotatedAt) : "Never"}</span>
            </div>
            <div className="tinyrow">
              <span>Header</span>
              <span>X-VERA-API-KEY</span>
            </div>
          </div>
          <div className="securityNote">
            <b>The raw existing key cannot be viewed.</b> VERA keeps only the public id and a hash. Rotating generates a new secret and immediately invalidates the old one.
          </div>
          <div className="dangerZone">
            <button className="btn danger" type="button" onClick={() => setConfirmRotate(true)}>
              Rotate API key
            </button>
          </div>
          {rawKey ? (
            <div style={{ marginTop: 14 }}>
              <div className="keyWarn">
                <b>New key — shown once.</b> Replace the old secret in your backend before leaving this page.
              </div>
              <div className="secretBox">
                <div className="secretValue">{rawKey}</div>
                <button className="btn small" type="button" onClick={() => void navigator.clipboard.writeText(rawKey)}>
                  Copy
                </button>
              </div>
            </div>
          ) : null}
        </div>
        <div>
          <div className="panel">
            <div className="paneltitle">Administrator session</div>
            <div className="panelsub">Used only for this VERA management workspace.</div>
            <div className="tinylist">
              <div className="tinyrow">
                <span>Signed in</span>
                <span>{user.email}</span>
              </div>
              <div className="tinyrow">
                <span>Tenant</span>
                <span>{user.tenantId}</span>
              </div>
              <div className="tinyrow">
                <span>Session cookie</span>
                <span>JSESSIONID · HttpOnly</span>
              </div>
              <div className="tinyrow">
                <span>Idle timeout</span>
                <span>30 minutes</span>
              </div>
              <div className="tinyrow">
                <span>CSRF</span>
                <span>X-XSRF-TOKEN</span>
              </div>
            </div>
            <div className="securityNote">
              Changing a path to another tenant does not change identity. The backend checks that the session tenant matches the requested tenant.
            </div>
            <button
              className="btn"
              style={{ marginTop: 12 }}
              type="button"
              onClick={() => {
                void logout().then(() => navigate("/"));
              }}
            >
              Log out of VERA
            </button>
          </div>
          <div className="panel" style={{ marginTop: 14 }}>
            <div className="paneltitle">Gateway example</div>
            <div className="panelsub">Store the raw key as a backend secret, not in frontend JavaScript.</div>
            <div className="code">{`POST /api/gateway/tenants/${user.tenantId}/evaluate
X-VERA-API-KEY: \${VERA_API_KEY}

{
  "subjectId": "emp-1001",
  "resourceId": "gpu",
  "entitlementKey": "gpu.enabled",
  "requestedValue": true
}`}</div>
          </div>
        </div>
      </div>
      {confirmRotate ? (
        <Modal title="Rotate API key" onClose={() => setConfirmRotate(false)}>
          <p>
            Rotating this key immediately invalidates the existing runtime key. Your company backend must be updated with
            the new key.
          </p>
          <div className="modalactions">
            <button className="btn" type="button" onClick={() => setConfirmRotate(false)}>
              Cancel
            </button>
            <button className="btn danger" type="button" disabled={busy} onClick={() => void rotate()}>
              {busy ? "Rotating…" : "Rotate now"}
            </button>
          </div>
        </Modal>
      ) : null}
    </div>
  );
}
