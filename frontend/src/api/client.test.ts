import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { apiRequest, getCsrfToken, SessionExpiredError, setCsrfToken, setUnauthorizedHandler } from "../api/client";

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify(body),
  } as Response;
}

beforeEach(() => {
  setCsrfToken(null);
  setUnauthorizedHandler(null);
  vi.stubGlobal(
    "fetch",
    vi.fn(() => Promise.resolve(jsonResponse({ token: "csrf-1", headerName: "X-XSRF-TOKEN", parameterName: "_csrf" }))),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
});

test("unsafe management call includes X-XSRF-TOKEN", async () => {
  setCsrfToken("csrf-secret", "X-XSRF-TOKEN");
  vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ success: true }));
  await apiRequest("/api/commands", { method: "POST", body: { type: "ADD_SCOPE" }, authRequired: true });
  expect(fetch).toHaveBeenCalledWith(
    "/api/commands",
    expect.objectContaining({
      method: "POST",
      credentials: "include",
      headers: expect.objectContaining({ "X-XSRF-TOKEN": "csrf-secret" }),
    }),
  );
});

test("safe GET does not send CSRF header", async () => {
  setCsrfToken("csrf-secret", "X-XSRF-TOKEN");
  vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ id: "acme" }));
  await apiRequest("/api/tenants/acme", { authRequired: true });
  const init = vi.mocked(fetch).mock.calls[0][1] as RequestInit;
  const headers = init.headers as Record<string, string>;
  expect(headers["X-XSRF-TOKEN"]).toBeUndefined();
  expect(getCsrfToken()).toBe("csrf-secret");
});

test("session-expired 401 invokes handler", async () => {
  const handler = vi.fn();
  setUnauthorizedHandler(handler);
  vi.mocked(fetch).mockResolvedValueOnce(
    jsonResponse({ timestamp: "", status: 401, error: "Unauthorized", message: "authentication required" }, 401),
  );
  await expect(apiRequest("/api/tenants/acme", { authRequired: true })).rejects.toBeInstanceOf(SessionExpiredError);
  expect(handler).toHaveBeenCalled();
});
