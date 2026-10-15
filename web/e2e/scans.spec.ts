import { expect, test } from "@playwright/test";
import { readFile } from "node:fs/promises";
import { mockBackend, result, scan, sse } from "./mock-backend";

const PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
  "base64",
);

test("starting a scan sends the domain with the CSRF token and opens the scan page", async ({ page }) => {
  const requests = await mockBackend(page, { scans: [scan()], events: { "scan-1": sse([{ event: "end", data: scan({ status: "COMPLETED" }) }]) } });
  await page.goto("/fr/app");

  await page.getByLabel("Nom de domaine").fill("exämple.com");
  await page.getByLabel("Ignorer le cache").check();
  await page.getByRole("button", { name: "Analyser" }).click();

  await expect(page).toHaveURL(/\/fr\/app\/scans\/scan-1$/);
  const create = requests.find((r) => r.url === "/api/scans" && r.method === "POST");
  expect(JSON.parse(create!.body!)).toEqual({ baseDomain: "exämple.com", forceRefresh: true });
  expect(create!.headers["x-xsrf-token"]).toBe("csrf-123");
});

test("a reached quota is explained to the user", async ({ page }) => {
  await mockBackend(page, {
    createScan: (route) =>
      route.fulfill({ status: 429, headers: { "Retry-After": "42" }, json: { error: "Daily scan quota reached (50 per 24 hours)" } }),
  });
  await page.goto("/fr/app");
  await page.getByLabel("Nom de domaine").fill("example.com");
  await page.getByRole("button", { name: "Analyser" }).click();

  await expect(page.getByRole("main").getByRole("alert")).toContainText("Daily scan quota reached");
  await expect(page.getByRole("main").getByRole("alert")).toContainText("42 s");
});

test("results arrive live, with counters, filter, details and warning", async ({ page }) => {
  const results = [
    result(1, "exmple.com", { status: "Suspicious", screenshotUrl: "/api/screenshots/abc", tlsStatus: "UNTRUSTED" }),
    result(2, "ехаmple.com", { domainAscii: "xn--mple-43d4a.com", status: "Safe", httpCode: 301 }),
    result(3, "exampel.com", { status: "Unreachable", httpCode: 0, tlsStatus: "NONE", title: null }),
  ];
  await mockBackend(page, {
    scans: [scan()],
    events: {
      "scan-1": sse([
        { event: "progress", data: scan({ checkedDomains: 1 }) },
        ...results.map((r) => ({ event: "result", id: r.id, data: r })),
        { event: "end", data: scan({ status: "COMPLETED", checkedDomains: 3 }) },
      ]),
    },
  });
  await page.route("**/api/screenshots/abc", (route) => route.fulfill({ contentType: "image/png", body: PNG }));
  await page.goto("/fr/app/scans/scan-1");

  await expect(page.getByText("Terminée")).toBeVisible();
  await expect(page.getByTestId("progress")).toHaveText("3 / 3 domaines vérifiés");
  await expect(page.getByRole("row")).toHaveCount(4);
  await expect(page.getByTestId("counter-Suspects")).toHaveText("1");
  await expect(page.getByTestId("counter-Injoignables")).toHaveText("1");

  await page.getByLabel("Uniquement les domaines en ligne").check();
  await expect(page.getByRole("row")).toHaveCount(3);
  await page.getByLabel("Filtrer").fill("mple-43d4a");
  await expect(page.getByRole("row")).toHaveCount(2);
  await page.getByLabel("Filtrer").fill("");
  await page.getByLabel("Uniquement les domaines en ligne").uncheck();

  await page.getByRole("row").filter({ hasText: "exmple.com" }).click();
  const details = page.getByRole("dialog", { name: "exmple.com" });
  await expect(details.getByRole("img")).toBeVisible();
  await expect(details.getByText("Non fiable")).toBeVisible();

  await details.getByRole("button", { name: "Ouvrir le site…" }).click();
  const warning = page.getByRole("dialog", { name: "Ouvrir un site potentiellement malveillant ?" });
  const open = warning.getByRole("link", { name: "Ouvrir quand même" });
  await expect(open).toHaveAttribute("rel", "noopener noreferrer");
  await expect(open).toHaveAttribute("href", "http://exmple.com/");
  await warning.getByRole("button", { name: "Annuler" }).click();
  await expect(warning).toBeHidden();
});

test("the CSV export neutralises formulas and keeps Unicode", async ({ page }) => {
  await mockBackend(page, {
    scans: [scan({ status: "COMPLETED" })],
    events: {
      "scan-1": sse([
        { event: "result", id: 1, data: result(1, "ехаmple.com", { title: "=HYPERLINK(\"http://evil\")" }) },
        { event: "end", data: scan({ status: "COMPLETED", checkedDomains: 1 }) },
      ]),
    },
  });
  await page.goto("/fr/app/scans/scan-1");
  await expect(page.getByRole("row")).toHaveCount(2);

  const [download] = await Promise.all([page.waitForEvent("download"), page.getByRole("button", { name: "CSV" }).click()]);
  const csv = await readFile((await download.path())!, "utf8");
  expect(download.suggestedFilename()).toBe("jtyposquatting-example.com.csv");
  expect(csv).toContain("ехаmple.com");
  expect(csv).toContain(`"'=HYPERLINK(""http://evil"")"`);
});

test("cancelling a running scan sends DELETE", async ({ page }) => {
  const requests = await mockBackend(page, { scans: [scan()], events: { "scan-1": "" } });
  await page.goto("/fr/app/scans/scan-1");
  await page.getByRole("button", { name: "Annuler l'analyse" }).click();

  await expect(page.getByText("Annulée")).toBeVisible();
  expect(requests.some((r) => r.method === "DELETE" && r.url === "/api/scans/scan-1")).toBe(true);
});

test("50,000 results (the per-scan limit) stay fluid: only visible rows are rendered", async ({ page }) => {
  test.setTimeout(120_000);
  const many = Array.from({ length: 50_000 }, (_, i) => result(i + 1, `variant-${i + 1}.com`));
  await mockBackend(page, {
    scans: [scan({ totalDomains: 50_000 })],
    events: {
      "scan-1": sse([
        ...many.map((r) => ({ event: "result", id: r.id, data: r })),
        { event: "end", data: scan({ status: "COMPLETED", totalDomains: 50_000, checkedDomains: 50_000 }) },
      ]),
    },
  });
  await page.goto("/fr/app/scans/scan-1");

  await expect(page.getByTestId("counter-Vérifiés")).toHaveText("50000", { timeout: 30_000 });
  expect(await page.getByRole("row").count()).toBeLessThan(80);

  await page.getByTestId("results-scroll").evaluate((el) => el.scrollTo(0, el.scrollHeight));
  await expect(page.getByRole("row").filter({ hasText: "variant-50000.com" })).toBeVisible();

  // The filter stays responsive on 50,000 rows
  const start = Date.now();
  await page.getByLabel("Filtrer").fill("variant-4999");
  // variant-4999 and variant-49990 to 49999, plus the header row
  await expect(page.getByRole("row")).toHaveCount(12);
  expect(Date.now() - start).toBeLessThan(3000);
});

test("a scan of another user shows an error", async ({ page }) => {
  await mockBackend(page, { scans: [] });
  await page.route("**/api/scans/other", (route) => route.fulfill({ status: 403, json: { error: "forbidden" } }));
  await page.goto("/fr/app/scans/other");
  await expect(page.getByRole("main").getByRole("alert")).toHaveText("Cette analyse ne vous appartient pas.");
});

test("each result appears once, even when sent again or when loading is slow", async ({ page }) => {
  const errors: string[] = [];
  page.on("console", (message) => message.type() === "error" && errors.push(message.text()));
  const results = [result(1, "a.com"), result(2, "b.com"), result(3, "c.com")];
  await mockBackend(page, {
    scans: [scan()],
    events: {
      "scan-1": sse([
        ...results.map((r) => ({ event: "result", id: r.id, data: r })),
        // Sent again after a reconnection
        { event: "result", id: 2, data: results[1] },
        { event: "end", data: scan({ status: "COMPLETED", checkedDomains: 3 }) },
      ]),
    },
  });
  // Slow scan loading: the stream opens after a possible unmount (StrictMode double mount)
  await page.route("**/api/scans/scan-1", async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 300));
    await route.fulfill({ json: scan() });
  });
  await page.goto("/fr/app/scans/scan-1");

  await expect(page.getByText("Terminée")).toBeVisible();
  await expect(page.getByRole("row")).toHaveCount(4);
  expect(errors.filter((e) => e.includes("same key"))).toEqual([]);
});
