import type { ApiErrorBody } from "./types";

export class ApiClientError extends Error {
  readonly status: number;
  readonly body: ApiErrorBody | null;

  constructor(status: number, message: string, body: ApiErrorBody | null = null) {
    super(message);
    this.name = "ApiClientError";
    this.status = status;
    this.body = body;
  }
}

export class SessionExpiredError extends ApiClientError {
  constructor() {
    super(401, "Your session expired. Sign in again.");
    this.name = "SessionExpiredError";
  }
}

type UnauthorizedHandler = () => void;

let csrfToken: string | null = null;
let csrfHeaderName = "X-XSRF-TOKEN";
let unauthorizedHandler: UnauthorizedHandler | null = null;

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler;
}

export function getCsrfToken(): string | null {
  return csrfToken;
}

export function setCsrfToken(token: string | null, headerName = "X-XSRF-TOKEN"): void {
  csrfToken = token;
  csrfHeaderName = headerName;
}

function isUnsafe(method: string): boolean {
  return ["POST", "PUT", "PATCH", "DELETE"].includes(method.toUpperCase());
}

async function parseBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  return (
    typeof value === "object" &&
    value !== null &&
    "status" in value &&
    "message" in value
  );
}

export interface RequestOptions {
  method?: string;
  body?: unknown;
  authRequired?: boolean;
  headers?: Record<string, string>;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = (options.method ?? "GET").toUpperCase();
  const headers: Record<string, string> = { ...(options.headers ?? {}) };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (isUnsafe(method) && csrfToken) {
    headers[csrfHeaderName] = csrfToken;
  }

  const response = await fetch(path, {
    method,
    credentials: "include",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  const parsed = await parseBody(response);
  if (response.ok) {
    return parsed as T;
  }

  const message = isApiErrorBody(parsed) ? parsed.message : `Request failed (${response.status})`;
  const body = isApiErrorBody(parsed) ? parsed : null;

  if (response.status === 401 && options.authRequired) {
    unauthorizedHandler?.();
    throw new SessionExpiredError();
  }

  throw new ApiClientError(response.status, message, body);
}
