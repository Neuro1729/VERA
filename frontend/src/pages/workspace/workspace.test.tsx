import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { fetchTenant } from "../../api/tenantApi";
import { BulkWizard } from "../../features/bulk/BulkWizard";
import { IntegrationPage } from "./IntegrationPage";
import { AuthProvider } from "../../auth/AuthContext";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ToastProvider } from "../../components/common/Toast";
import { WorkspaceLayout } from "../../components/layout/WorkspaceLayout";

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify(body),
  } as Response;
}

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

test("management API URLs use authenticated tenantId", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => jsonResponse({ id: "acme" })));
  await fetchTenant("acme");
  expect(fetch).toHaveBeenCalledWith("/api/tenants/acme", expect.objectContaining({ credentials: "include" }));
});

test("workspace has no tenant selector and loads auth tenantId", async () => {
  const fetchMock = vi.fn(async (url: string) => {
    if (String(url).includes("/csrf")) return jsonResponse({ token: "t", headerName: "X-XSRF-TOKEN", parameterName: "_csrf" });
    if (String(url).includes("/me")) return jsonResponse({ authenticated: true, tenantId: "acme", email: "ops@example.com" });
    if (String(url) === "/api/tenants/acme") {
      return jsonResponse({
        id: "acme",
        name: "Acme Corporation",
        rootScopeId: "root",
        scopes: { root: { id: "root", kind: "company", name: "Acme", metadata: {}, parentScopeId: null, childScopeIds: [], subjectIds: [] } },
        subjects: {},
        resources: {},
        grants: {},
      });
    }
    return jsonResponse({}, 404);
  });
  vi.stubGlobal("fetch", fetchMock);
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={["/workspace"]}>
        <Routes>
          <Route path="/workspace" element={<WorkspaceLayout />} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
  expect(await screen.findByText("tenant: acme")).toBeInTheDocument();
  expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  expect(screen.queryByLabelText(/tenant id/i)).not.toBeInTheDocument();
  await waitFor(() =>
    expect(fetchMock).toHaveBeenCalledWith("/api/tenants/acme", expect.objectContaining({ credentials: "include" })),
  );
  expect(fetchMock).not.toHaveBeenCalledWith(expect.stringMatching(/\/api\/tenants\/(?!acme\b)/), expect.anything());
});

test("existing API key metadata does not display raw secret", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (String(url).includes("/csrf")) return jsonResponse({ token: "t", headerName: "X-XSRF-TOKEN", parameterName: "_csrf" });
      if (String(url).includes("/me")) return jsonResponse({ authenticated: true, tenantId: "acme", email: "ops@example.com" });
      if (String(url).includes("/api-key")) {
        return jsonResponse({
          publicId: "K3px9Bc2",
          displayPrefix: "vera_live_K3px9Bc2...",
          createdAt: "2026-08-15T00:00:00Z",
          rotatedAt: null,
        });
      }
      return jsonResponse({}, 404);
    }),
  );
  render(
    <AuthProvider>
      <ToastProvider>
        <MemoryRouter>
          <IntegrationPage />
        </MemoryRouter>
      </ToastProvider>
    </AuthProvider>,
  );
  expect(await screen.findByText("K3px9Bc2")).toBeInTheDocument();
  expect(screen.queryByText(/vera_live_K3px9Bc2\.[A-Za-z0-9]/)).not.toBeInTheDocument();
  expect(screen.getByText(/raw existing key cannot be viewed/i)).toBeInTheDocument();
});

test("rotation confirmation is required and new raw key is shown", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).includes("/csrf")) return jsonResponse({ token: "t", headerName: "X-XSRF-TOKEN", parameterName: "_csrf" });
      if (String(url).includes("/me")) return jsonResponse({ authenticated: true, tenantId: "acme", email: "ops@example.com" });
      if (String(url).includes("/rotate")) {
        expect(init?.method).toBe("POST");
        return jsonResponse({ apiKey: "vera_live_new.rawsecret", publicId: "newid", rotatedAt: "2026-08-15T01:00:00Z" });
      }
      if (String(url).includes("/api-key")) {
        return jsonResponse({
          publicId: "K3px9Bc2",
          displayPrefix: "vera_live_K3px9Bc2...",
          createdAt: "2026-08-15T00:00:00Z",
          rotatedAt: null,
        });
      }
      return jsonResponse({}, 404);
    }),
  );
  render(
    <AuthProvider>
      <ToastProvider>
        <MemoryRouter>
          <IntegrationPage />
        </MemoryRouter>
      </ToastProvider>
    </AuthProvider>,
  );
  const rotateButtons = await screen.findAllByRole("button", { name: "Rotate API key" });
  await userEvent.click(rotateButtons[0]);
  expect(screen.getByText(/immediately invalidates the existing runtime key/i)).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "Rotate now" }));
  expect(await screen.findByText("vera_live_new.rawsecret")).toBeInTheDocument();
});

test("invalid projected state blocks Apply and auto-removals are impact", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (String(url).includes("/csrf")) return jsonResponse({ token: "t", headerName: "X-XSRF-TOKEN", parameterName: "_csrf" });
      if (String(url).includes("/preview")) {
        return jsonResponse({
          valid: false,
          domains: ["GRANTS"],
          summary: {
            scopesAdded: 0,
            scopesUpdated: 0,
            scopesMoved: 0,
            scopesRemoved: 0,
            subjectsAdded: 0,
            subjectsUpdated: 0,
            subjectsMoved: 0,
            subjectsRemoved: 0,
            resourcesAdded: 0,
            resourcesUpdated: 0,
            resourcesRemoved: 0,
            grantsCreated: 0,
            grantsUpdated: 0,
            grantsRemoved: 0,
            grantsAutomaticallyRemoved: 7,
            invalidGrantCount: 3,
            warningCount: 0,
            errorCount: 3,
          },
          impactSummary: { grantsAffected: 7, grantsAutomaticallyRemoved: 7 },
          changes: [],
          issues: [],
        });
      }
      return jsonResponse({}, 404);
    }),
  );
  render(
    <ToastProvider>
      <BulkWizard tenantId="acme" initialDomain="grants" onClose={() => undefined} onApplied={async () => undefined} />
    </ToastProvider>,
  );
  await userEvent.click(screen.getByRole("button", { name: "Continue" }));
  await userEvent.click(screen.getByRole("button", { name: "Validate changes" }));
  expect(await screen.findByText(/invalid surviving grants block apply/i)).toBeInTheDocument();
  expect(screen.getByText(/7 grants will be removed/i)).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Apply changes atomically" })).toBeDisabled();
});
