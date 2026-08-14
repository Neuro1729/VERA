import { Link } from "react-router-dom";
import { TopBar } from "../components/layout/TopBar";

export function LandingPage() {
  return (
    <div className="app">
      <TopBar />
      <section>
        <div className="wrap">
          <div className="hero">
            <div>
              <div className="eyebrow">General-purpose resource entitlement control plane</div>
              <h1>
                Define <span>who can use what</span> — across any organization and any resource.
              </h1>
              <p>
                VERA is a highly generalizable entitlement engine for companies, universities, research labs, GPU
                clusters, hospitals, internal platforms and more. You describe your own hierarchy, resources and access
                rules — VERA does not hard-code departments, employees, GPUs, APIs or any other organization type.
              </p>
              <div className="heroactions">
                <Link className="btn primary" to="/signup">
                  I'm an organization · Get started
                </Link>
                <Link className="btn" to="/login">
                  Returning admin · Sign in
                </Link>
              </div>
              <div style={{ marginTop: 28 }}>
                <div style={{ fontSize: 11, fontWeight: 900, marginBottom: 10 }}>What can VERA control?</div>
                <div className="minigrid">
                  <div className="minicard">
                    <b>Simple access</b>
                    <div>Allow or deny access with boolean rules.</div>
                  </div>
                  <div className="minicard">
                    <b>Limits & quotas</b>
                    <div>Monthly GPU hours, request counts, storage limits and numeric allowances.</div>
                  </div>
                  <div className="minicard">
                    <b>Rate limits</b>
                    <div>Control how quickly APIs or shared systems may be used.</div>
                  </div>
                  <div className="minicard">
                    <b>Ranges</b>
                    <div>Allow values only inside a permitted minimum and maximum.</div>
                  </div>
                  <div className="minicard">
                    <b>Allowed sets</b>
                    <div>Choose permitted GPU models, datasets, regions, API models or tools.</div>
                  </div>
                  <div className="minicard">
                    <b>Time & custom values</b>
                    <div>Access windows, tiers, labels and other text-based controls.</div>
                  </div>
                </div>
              </div>
            </div>
            <div className="visual">
              <h3>One engine, many environments</h3>
              <p>VERA models your world instead of forcing your world into a fixed schema.</p>
              <div className="minigrid">
                <div className="minicard">
                  <b>Company</b>
                  <div>Company → department → team → employee.</div>
                </div>
                <div className="minicard">
                  <b>University</b>
                  <div>University → school → lab → student.</div>
                </div>
                <div className="minicard">
                  <b>Research / GPU lab</b>
                  <div>Lab → project → researcher → compute resources.</div>
                </div>
              </div>
              <div style={{ height: 1, background: "#eef0f3", margin: "20px 0" }} />
              <h3>VERA stays in the control plane</h3>
              <p>Your application still authenticates users and performs the actual resource operation.</p>
              <div className="flow">
                <div className="node">User / Service</div>
                <div className="arrow">→</div>
                <div className="node">Your app</div>
                <div className="arrow">→</div>
                <div className="node brandnode">VERA</div>
                <div className="arrow">→</div>
                <div className="node">ALLOW / DENY</div>
              </div>
              <div className="minigrid">
                <div className="minicard">
                  <b>Organization</b>
                  <div>Your own recursive hierarchy.</div>
                </div>
                <div className="minicard">
                  <b>Resources</b>
                  <div>GPUs, APIs, storage, data, licenses, equipment.</div>
                </div>
                <div className="minicard">
                  <b>Grants</b>
                  <div>Rules inherited from scopes or assigned directly.</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
