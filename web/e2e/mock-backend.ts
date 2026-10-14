// Simulated backend for end-to-end tests: the frontend is tested without Java, database nor network.

import type { Page, Route } from "@playwright/test";
import type { Scan, ScanResult } from "../src/lib/types";

export const USER = { id: "user-1", username: "alice", name: "Alice Dev", email: "alice@example.com" };

export function scan(overrides: Partial<Scan> = {}): Scan {
  return {
    id: "scan-1",
    baseDomain: "example.com",
    status: "RUNNING",
    totalDomains: 3,
    checkedDomains: 0,
    createdAt: "2026-10-14T10:00:00Z",
    startedAt: "2026-10-14T10:00:01Z",
    finishedAt: null,
    error: null,
    ...overrides,
  };
}

export function result(id: number, domain: string, overrides: Partial<ScanResult> = {}): ScanResult {
  return {
    id,
    domain,
    domainAscii: domain,
    status: "Safe",
    httpCode: 200,
    tlsStatus: "VALID",
    title: `Title of ${domain}`,
    language: "ENGLISH",
    description: null,
    homepageText: null,
    httpHeaders: { Server: "nginx" },
    screenshotUrl: null,
    checkedAt: "2026-10-14T10:00:05Z",
    ...overrides,
  };
}

export function sse(events: { event: string; data: unknown; id?: number }[]): string {
  return events
    .map(({ event, data, id }) => (id !== undefined ? `id:${id}\n` : "") + `event:${event}\ndata:${JSON.stringify(data)}\n\n`)
    .join("");
}

export interface MockOptions {
  loggedIn?: boolean;
  scans?: Scan[];
  createScan?: (route: Route) => Promise<void>;
  events?: Record<string, string>;
}

/** Installs the simulated API on the page; returns the requests received, for assertions. */
export async function mockBackend(page: Page, options: MockOptions = {}) {
  const requests: { method: string; url: string; headers: Record<string, string>; body: string | null }[] = [];
  const loggedIn = options.loggedIn ?? true;

  await page.context().addCookies([{ name: "XSRF-TOKEN", value: "csrf-123", url: "http://localhost:3100" }]);

  await page.route(/\/(api|logout|oauth2)\b/, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    requests.push({ method: request.method(), url: url.pathname, headers: request.headers(), body: request.postData() });

    if (url.pathname.startsWith("/oauth2/")) {
      return route.fulfill({ status: 200, contentType: "text/html", body: "<h1>Identity provider</h1>" });
    }
    if (!loggedIn) {
      return route.fulfill({ status: 401, contentType: "application/json", body: "{}" });
    }
    if (url.pathname === "/api/me") {
      return route.fulfill({ json: USER });
    }
    if (url.pathname === "/logout") {
      return route.fulfill({ json: { logoutUrl: "/" } });
    }
    if (url.pathname === "/api/scans" && request.method() === "GET") {
      return route.fulfill({ json: options.scans ?? [] });
    }
    if (url.pathname === "/api/scans" && request.method() === "POST") {
      if (options.createScan) return options.createScan(route);
      return route.fulfill({ status: 202, json: scan({ status: "PENDING" }) });
    }
    const events = url.pathname.match(/^\/api\/scans\/([^/]+)\/events$/);
    if (events) {
      return route.fulfill({
        status: 200,
        headers: { "Content-Type": "text/event-stream", "Cache-Control": "no-cache" },
        body: options.events?.[events[1]] ?? "",
      });
    }
    const one = url.pathname.match(/^\/api\/scans\/([^/]+)$/);
    if (one && request.method() === "DELETE") {
      return route.fulfill({ status: 204 });
    }
    if (one) {
      const found = (options.scans ?? []).find((s) => s.id === one[1]);
      return found ? route.fulfill({ json: found }) : route.fulfill({ status: 404, json: { error: "not found" } });
    }
    return route.fulfill({ status: 404, json: { error: "not mocked" } });
  });

  return requests;
}
