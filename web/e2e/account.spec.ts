import { expect, test } from "@playwright/test";
import { readFile } from "node:fs/promises";
import { mockBackend } from "./mock-backend";

test("l'utilisateur exporte ses données en JSON", async ({ page }) => {
  await mockBackend(page);
  await page.goto("/fr/app");

  const [download] = await Promise.all([
    page.waitForEvent("download"),
    page.getByRole("button", { name: "Exporter mes données (JSON)" }).click(),
  ]);
  expect(download.suggestedFilename()).toBe("jtyposquatting-data.json");
  const data = JSON.parse(await readFile((await download.path())!, "utf8"));
  expect(data.user.email).toBe("alice@example.com");
});

test("la suppression du compte demande confirmation, appelle DELETE /api/me puis déconnecte", async ({ page }) => {
  const requests = await mockBackend(page);
  await page.goto("/fr/app");

  await page.getByRole("button", { name: "Supprimer mon compte" }).click();
  const dialog = page.getByRole("dialog", { name: "Supprimer définitivement votre compte ?" });
  await dialog.getByRole("button", { name: "Annuler" }).click();
  expect(requests.some((r) => r.method === "DELETE" && r.url === "/api/me")).toBe(false);

  await page.getByRole("button", { name: "Supprimer mon compte" }).click();
  await dialog.getByRole("button", { name: "Supprimer définitivement" }).click();

  await expect(page).toHaveURL(/\/fr$/);
  const deletion = requests.findIndex((r) => r.method === "DELETE" && r.url === "/api/me");
  expect(requests[deletion].headers["x-xsrf-token"]).toBe("csrf-123");
  // Puis déconnexion complète, pour fermer aussi la session du fournisseur d'identité
  expect(requests.findIndex((r) => r.method === "POST" && r.url === "/logout")).toBeGreaterThan(deletion);
});
