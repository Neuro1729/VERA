import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { TopBar } from "../../components/layout/TopBar";
import { useState } from "react";

interface RevealState {
  apiKey?: string;
  tenantId?: string;
}

export function ApiKeyRevealPage() {
  const { user } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const state = (location.state as RevealState | null) ?? {};
  const apiKey = state.apiKey;
  const tenantId = state.tenantId ?? user?.tenantId ?? "";
  const [copied, setCopied] = useState(false);

  async function copy() {
    if (!apiKey) return;
    await navigator.clipboard.writeText(apiKey);
    setCopied(true);
  }

  const prefix = apiKey ? `${apiKey.split(".")[0]}.••••••••••••••••` : "";

  return (
    <div className="app">
      <TopBar />
      <div className="keyReveal">
        <div className="keyHero">
          <div className="eyebrow">Workspace created</div>
          <h2 style={{ fontSize: 34, letterSpacing: "-.04em", margin: "12px 0 8px" }}>Save your runtime API key</h2>
          <p className="lead" style={{ maxWidth: 610, margin: "0 auto" }}>
            Your admin session is already active. This separate key authenticates your company backend when it calls the VERA runtime gateway.
          </p>
        </div>
        <div className="keyCard">
          {apiKey ? (
            <>
              <div className="keyWarn">
                <b>Shown once.</b> Copy this key into your backend secret manager now. VERA stores only its public id and a BCrypt hash of the secret.
              </div>
              <div className="secretBox">
                <div className="secretValue">{apiKey}</div>
                <button className="btn small" type="button" onClick={() => void copy()}>
                  Copy key
                </button>
              </div>
              <div className="note">
                {copied ? <b style={{ color: "var(--green)" }}>Copied.</b> : "Use as:"} <b>X-VERA-API-KEY</b>
              </div>
              <div className="keyMeta">
                <div className="metaCard">
                  <span>Tenant</span>
                  <b>{tenantId}</b>
                </div>
                <div className="metaCard">
                  <span>Gateway</span>
                  <b>/api/gateway/tenants/{tenantId}</b>
                </div>
                <div className="metaCard">
                  <span>Session</span>
                  <b style={{ color: "var(--green)" }}>Admin signed in</b>
                </div>
              </div>
              <div className="code" style={{ marginTop: 16 }}>
                X-VERA-API-KEY: {prefix}
              </div>
            </>
          ) : (
            <div className="keyWarn">
              <b>The original API key can no longer be displayed.</b> You can rotate it from Integration.
            </div>
          )}
          <div className="cardactions">
            <span className="note" style={{ margin: 0 }}>
              You can rotate the key later, but the existing raw secret will never be displayed again.
            </span>
            <button className="btn primary" type="button" onClick={() => navigate("/workspace", { replace: true })}>
              Continue to workspace
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
