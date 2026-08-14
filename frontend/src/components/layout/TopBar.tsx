import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";

export function TopBar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  async function onLogout() {
    await logout();
    navigate("/");
  }

  return (
    <header className="topbar">
      <Link className="brand" to={user ? "/workspace" : "/"}>
        <div className="brandmark">V</div>
        VERA
      </Link>
      {user ? (
        <div className="topactions">
          <div className="sessionChip">
            <span className="sessionDot" aria-hidden="true" />
            <div className="sessionText">
              <b>{user.email}</b>
              Session active
            </div>
          </div>
          <button className="btn" type="button" onClick={() => void onLogout()}>
            Log out
          </button>
        </div>
      ) : (
        <div className="topactions">
          <Link className="btn" to="/demo">
            Demo workspace
          </Link>
          <Link className="btn" to="/login">
            Sign in
          </Link>
          <Link className="btn primary" to="/signup">
            Get started
          </Link>
        </div>
      )}
    </header>
  );
}
