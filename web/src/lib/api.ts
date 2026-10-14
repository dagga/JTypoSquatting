// Client of the backend API. The frontend and the API share the same origin (reverse proxy in
// production, rewrites in development): the session cookie is sent automatically.

import type { Scan, ScanResult, User } from "./types";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    /** Seconds before retrying, for 429 responses */
    readonly retryAfter?: number,
  ) {
    super(message);
  }
}

/** URL that starts the OpenID Connect login (handled by the backend). */
export const LOGIN_URL = "/oauth2/authorization/oidc";

/** Value of the CSRF cookie set by the backend, sent back in the X-XSRF-TOKEN header. */
function csrfToken(): string | undefined {
  return document.cookie
    .split("; ")
    .find((cookie) => cookie.startsWith("XSRF-TOKEN="))
    ?.substring("XSRF-TOKEN=".length);
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.method && init.method !== "GET") {
    const token = csrfToken();
    if (token) headers.set("X-XSRF-TOKEN", decodeURIComponent(token));
  }
  const response = await fetch(path, { ...init, headers, credentials: "same-origin" });
  if (!response.ok) {
    let message = response.statusText;
    try {
      const body = await response.json();
      if (body?.error) message = body.error;
    } catch {
      // No JSON body
    }
    const retryAfter = Number(response.headers.get("Retry-After")) || undefined;
    throw new ApiError(response.status, message, retryAfter);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

/** Current user, or null when not logged in. */
export async function getCurrentUser(): Promise<User | null> {
  try {
    return await request<User>("/api/me");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null;
    throw error;
  }
}

/** Ends the session and the identity provider session. */
export async function logout(): Promise<void> {
  // A GET first, so that the backend issues the CSRF cookie if needed
  if (!csrfToken()) await getCurrentUser();
  const { logoutUrl } = await request<{ logoutUrl: string }>("/logout", { method: "POST" });
  window.location.assign(logoutUrl);
}

export function listScans(): Promise<Scan[]> {
  return request<Scan[]>("/api/scans");
}

export function getScan(id: string): Promise<Scan> {
  return request<Scan>(`/api/scans/${encodeURIComponent(id)}`);
}

export async function createScan(baseDomain: string, forceRefresh: boolean): Promise<Scan> {
  if (!csrfToken()) await getCurrentUser();
  return request<Scan>("/api/scans", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ baseDomain, forceRefresh }),
  });
}

export async function cancelScan(id: string): Promise<void> {
  if (!csrfToken()) await getCurrentUser();
  await request<void>(`/api/scans/${encodeURIComponent(id)}`, { method: "DELETE" });
}

export interface ScanEventHandlers {
  onResult: (result: ScanResult) => void;
  onProgress: (scan: Scan) => void;
  onEnd: (scan: Scan) => void;
  onError: () => void;
}

/**
 * Follows a scan with Server-Sent Events. EventSource reconnects by itself and sends
 * Last-Event-ID, so that only missed results are received again.
 *
 * @returns a function that closes the stream
 */
export function followScan(id: string, handlers: ScanEventHandlers): () => void {
  const source = new EventSource(`/api/scans/${encodeURIComponent(id)}/events`, { withCredentials: true });
  source.addEventListener("result", (event) => handlers.onResult(JSON.parse((event as MessageEvent).data)));
  source.addEventListener("progress", (event) => handlers.onProgress(JSON.parse((event as MessageEvent).data)));
  source.addEventListener("end", (event) => {
    handlers.onEnd(JSON.parse((event as MessageEvent).data));
    source.close();
  });
  source.onerror = () => {
    // CLOSED: the server refused the stream (401, 403...); otherwise the browser retries by itself
    if (source.readyState === EventSource.CLOSED) handlers.onError();
  };
  return () => source.close();
}
