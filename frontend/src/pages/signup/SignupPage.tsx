import { type FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useSignupDraft } from "../../auth/SignupDraftContext";
import { TopBar } from "../../components/layout/TopBar";

export function SignupPage() {
  const { draft, setCredentials } = useSignupDraft();
  const navigate = useNavigate();
  const [email, setEmail] = useState(draft.email);
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (password.length < 12) {
      setError("Password must be at least 12 characters.");
      return;
    }
    setCredentials(email, password);
    navigate("/signup/configuration");
  }

  return (
    <div className="app">
      <TopBar />
      <div className="authShell">
        <div className="authIntro">
          <div className="eyebrow">Create your VERA workspace</div>
          <h2>One administrator. One organization.</h2>
          <p>
            For V1, each tenant has one VERA administrator. Create the admin identity first, then configure organization,
            resources and grants.
          </p>
          <div className="authPoints">
            <div className="authPoint">
              <div className="authIcon">1</div>
              <div>
                <b>Admin session</b>
                <span>After signup, VERA creates a server-side session. The browser keeps only the HttpOnly JSESSIONID cookie.</span>
              </div>
            </div>
            <div className="authPoint">
              <div className="authIcon">2</div>
              <div>
                <b>Tenant isolation</b>
                <span>Your authenticated session is bound to one tenant and cannot manage another tenant by changing a URL.</span>
              </div>
            </div>
            <div className="authPoint">
              <div className="authIcon">3</div>
              <div>
                <b>Runtime key comes later</b>
                <span>After successful registration, VERA reveals one API key for your company backend exactly once.</span>
              </div>
            </div>
          </div>
        </div>
        <form className="authCard" onSubmit={onSubmit}>
          <div className="tag">ADMIN ACCOUNT</div>
          <h3>Create your admin login</h3>
          <p>
            These credentials are collected for the final atomic signup. Nothing is persisted until you validate the three
            configs and create the workspace. The admin login stays separate from the runtime API key.
          </p>
          <div className="field">
            <label htmlFor="signup-email">Work email</label>
            <input id="signup-email" type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required placeholder="admin@company.com" />
          </div>
          <div className="field">
            <label htmlFor="signup-password">Password</label>
            <input id="signup-password" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required placeholder="At least 12 characters" />
          </div>
          {error ? <div className="formError" role="alert">{error}</div> : null}
          <button className="btn primary authSubmit" type="submit">
            Continue to configuration
          </button>
          <div className="authSwitch">
            Already have a workspace? <Link to="/login">Sign in</Link>
          </div>
          <div className="securityNote">
            <b>What happens technically:</b> the final signup request is CSRF-protected, the password is hashed, and the authenticated SecurityContext is stored in the server-side HttpSession.
          </div>
        </form>
      </div>
    </div>
  );
}
