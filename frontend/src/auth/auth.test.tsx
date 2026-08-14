import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { apiRequest } from "../api/client";
import { AuthProvider, useAuth } from "../auth/AuthContext";
import { ProtectedRoute } from "../auth/ProtectedRoute";
import { LoginPage } from "../pages/LoginPage";

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify(body),
  } as Response;
}

function Probe() {
  const { user, ready } = useAuth();
  if (!ready) return <div>booting</div>;
  return <div>{user ? `workspace:${user.email}:${user.tenantId}` : "public"}</div>;
}

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

test("unauthenticated /api/auth/me leads to public state", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (String(url).includes("/csrf")) return jsonResponse({ token: "t", headerName: "X-XSRF-TOKEN", parameterName: "_csrf" });
      return jsonResponse({ timestamp: "", status: 401, error: "Unauthorized", message: "authentication required" }, 401);
    }),
  );
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  );
  expect(await screen.findByText("public")).toBeInTheDocument();
});

test("authenticated /api/auth/me enters workspace identity", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (String(url).includes("/csrf")) return jsonResponse({ token: "t", headerName: "X-XSRF-TOKEN", parameterName: "_csrf" });
      return jsonResponse({ authenticated: true, tenantId: "acme", email: "ops@example.com" });
    }),
  );
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  );
  expect(await screen.findByText("workspace:ops@example.com:acme")).toBeInTheDocument();
});

test("login obtains CSRF and submits credentials", async () => {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (String(url).includes("/csrf")) return jsonResponse({ token: "csrf-login", headerName: "X-XSRF-TOKEN", parameterName: "_csrf" });
    if (String(url).includes("/me")) {
      return jsonResponse({ timestamp: "", status: 401, error: "Unauthorized", message: "authentication required" }, 401);
    }
    if (String(url).includes("/login")) {
      expect(init?.credentials).toBe("include");
      expect((init?.headers as Record<string, string>)["X-XSRF-TOKEN"]).toBe("csrf-login");
      expect(JSON.parse(String(init?.body))).toEqual({ email: "ops@example.com", password: "a-long-password" });
      return jsonResponse({ authenticated: true, tenantId: "acme", email: "ops@example.com" });
    }
    return jsonResponse({}, 404);
  });
  vi.stubGlobal("fetch", fetchMock);
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={["/login"]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/workspace" element={<div>workspace-ok</div>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
  await screen.findByLabelText("Email");
  await userEvent.type(screen.getByLabelText("Email"), "ops@example.com");
  await userEvent.type(screen.getByLabelText("Password"), "a-long-password");
  await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
  expect(await screen.findByText("workspace-ok")).toBeInTheDocument();
});

test("login failure shows generic error", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (String(url).includes("/csrf")) return jsonResponse({ token: "t", headerName: "X-XSRF-TOKEN", parameterName: "_csrf" });
      if (String(url).includes("/login")) {
        return jsonResponse({ timestamp: "", status: 401, error: "Unauthorized", message: "authentication required" }, 401);
      }
      return jsonResponse({ timestamp: "", status: 401, error: "Unauthorized", message: "authentication required" }, 401);
    }),
  );
  render(
    <AuthProvider>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </AuthProvider>,
  );
  await screen.findByLabelText("Email");
  await userEvent.type(screen.getByLabelText("Email"), "ops@example.com");
  await userEvent.type(screen.getByLabelText("Password"), "wrong-password-1");
  await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
  expect(await screen.findByText("Invalid email or password.")).toBeInTheDocument();
  expect(screen.queryByText(/unknown email/i)).not.toBeInTheDocument();
});

test("logout clears frontend auth state", async () => {
  let authed = true;
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (String(url).includes("/csrf")) return jsonResponse({ token: "t", headerName: "X-XSRF-TOKEN", parameterName: "_csrf" });
      if (String(url).includes("/logout")) {
        authed = false;
        return jsonResponse({ authenticated: false, tenantId: null, email: null });
      }
      if (authed) return jsonResponse({ authenticated: true, tenantId: "acme", email: "ops@example.com" });
      return jsonResponse({ timestamp: "", status: 401, error: "Unauthorized", message: "authentication required" }, 401);
    }),
  );
  function LogoutProbe() {
    const { user, logout, ready } = useAuth();
    if (!ready) return <div>booting</div>;
    return (
      <div>
        <div>{user ? "in" : "out"}</div>
        <button type="button" onClick={() => void logout()}>
          logout
        </button>
      </div>
    );
  }
  render(
    <AuthProvider>
      <LogoutProbe />
    </AuthProvider>,
  );
  expect(await screen.findByText("in")).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "logout" }));
  await waitFor(() => expect(screen.getByText("out")).toBeInTheDocument());
});

test("protected route redirects anonymous user", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (String(url).includes("/csrf")) return jsonResponse({ token: "t", headerName: "X-XSRF-TOKEN", parameterName: "_csrf" });
      return jsonResponse({ timestamp: "", status: 401, error: "Unauthorized", message: "authentication required" }, 401);
    }),
  );
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={["/workspace"]}>
        <Routes>
          <Route
            path="/workspace"
            element={
              <ProtectedRoute>
                <div>secret</div>
              </ProtectedRoute>
            }
          />
          <Route path="/login" element={<div>login-page</div>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
  expect(await screen.findByText("login-page")).toBeInTheDocument();
  expect(screen.queryByText("secret")).not.toBeInTheDocument();
});

test("session-expired 401 returns user to login", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (String(url).includes("/csrf")) return jsonResponse({ token: "t", headerName: "X-XSRF-TOKEN", parameterName: "_csrf" });
      if (String(url).includes("/me")) return jsonResponse({ authenticated: true, tenantId: "acme", email: "ops@example.com" });
      if (String(url).includes("/tenants/acme")) {
        return jsonResponse({ timestamp: "", status: 401, error: "Unauthorized", message: "authentication required" }, 401);
      }
      return jsonResponse({}, 404);
    }),
  );
  function ExpireProbe() {
    return (
      <button type="button" onClick={() => void apiRequest("/api/tenants/acme", { authRequired: true }).catch(() => undefined)}>
        expire
      </button>
    );
  }
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={["/workspace"]}>
        <Routes>
          <Route
            path="/workspace"
            element={
              <ProtectedRoute>
                <ExpireProbe />
              </ProtectedRoute>
            }
          />
          <Route path="/login" element={<LoginPage />} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
  await userEvent.click(await screen.findByRole("button", { name: "expire" }));
  expect(await screen.findByText("Your session expired. Sign in again.")).toBeInTheDocument();
});
