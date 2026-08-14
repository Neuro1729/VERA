import { type FormEvent, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { ApiClientError } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { TopBar } from "../components/layout/TopBar";

export function LoginPage() {
  const { login, sessionMessage } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const expired =
    (location.state as { expired?: boolean } | null)?.expired || Boolean(sessionMessage);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(expired ? "Your session expired. Sign in again." : null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email, password);
      navigate("/workspace", { replace: true });
    } catch (err) {
      if (err instanceof ApiClientError && (err.status === 401 || err.status === 400)) {
        setError("Invalid email or password.");
      } else {
        setError("Invalid email or password.");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="app">
      <TopBar />
      <div className="authShell">
        <div className="authIntro">
          <div className="eyebrow">VERA admin</div>
          <h2>Sign in to your workspace.</h2>
          <p>Use the administrator credentials created during company signup. VERA does not issue a JWT for this browser flow.</p>
          <div className="visual" style={{ marginTop: 24 }}>
            <h3>Session flow</h3>
            <p>The browser only carries an opaque session identifier.</p>
            <div className="flow">
              <div className="node">Email + password</div>
              <div className="arrow">→</div>
              <div className="node brandnode">VERA</div>
              <div className="arrow">→</div>
              <div className="node">JSESSIONID</div>
            </div>
            <div className="securityNote">
              <b>Server side:</b> JSESSIONID maps to your admin identity and one tenant. The session expires after inactivity and is invalidated on logout.
            </div>
          </div>
        </div>
        <form className="authCard" onSubmit={(event) => void onSubmit(event)}>
          <div className="tag">WELCOME BACK</div>
          <h3>Administrator sign in</h3>
          <p>Your session is stored on the server. This browser keeps only the HttpOnly JSESSIONID cookie.</p>
          <div className="field">
            <label htmlFor="login-email">Email</label>
            <input id="login-email" type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </div>
          <div className="field">
            <label htmlFor="login-password">Password</label>
            <input id="login-password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </div>
          {error ? <div className="formError" role="alert">{error}</div> : null}
          <button className="btn primary authSubmit" type="submit" disabled={busy}>
            {busy ? "Signing in…" : "Sign in"}
          </button>
          <div className="authSwitch">
            New organization? <Link to="/signup">Create workspace</Link>
          </div>
          <div className="securityNote">
            <b>CSRF:</b> the frontend first obtains the XSRF token, then sends it with this session-authenticated mutation.
          </div>
        </form>
      </div>
    </div>
  );
}
