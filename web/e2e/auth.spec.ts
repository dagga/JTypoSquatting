import { expect, test } from "@playwright/test";
import { mockBackend } from "./mock-backend";

test("an anonymous visitor is asked to log in", async ({ page }) => {
  await mockBackend(page, { loggedIn: false });
  await page.goto("/fr/app");

  await expect(page.getByText("Connectez-vous pour lancer et suivre vos analyses.")).toBeVisible();
  const loginLinks = page.getByRole("link", { name: "Se connecter" });
  await expect(loginLinks.first()).toHaveAttribute("href", "/oauth2/authorization/oidc");

  await loginLinks.first().click();
  await expect(page.getByRole("heading", { name: "Identity provider" })).toBeVisible();
});

test("a logged-in user sees their name and can log out (with the CSRF token)", async ({ page }) => {
  const requests = await mockBackend(page);
  await page.goto("/en/app");

  await expect(page.getByTestId("current-user")).toHaveText("Alice Dev");
  await page.getByRole("button", { name: "Log out" }).click();

  await expect(page).toHaveURL(/\/fr$|\/en$/);
  const logout = requests.find((r) => r.url === "/logout");
  expect(logout?.method).toBe("POST");
  expect(logout?.headers["x-xsrf-token"]).toBe("csrf-123");
});

test("the language switcher keeps the current page", async ({ page }) => {
  await mockBackend(page, { loggedIn: false });
  await page.goto("/fr/faq");
  await page.getByRole("link", { name: "EN", exact: true }).click();

  await expect(page).toHaveURL(/\/en\/faq$/);
  await expect(page.getByRole("heading", { name: "Frequently asked questions" })).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
});

test("a login failure is reported without starting the login again", async ({ page }) => {
  await mockBackend(page, { loggedIn: false });
  await page.goto("/fr/app?loginError=1");

  await expect(page.getByRole("main").getByRole("alert")).toHaveText(/La connexion a échoué/);
  await expect(page.getByRole("main").getByRole("link", { name: "Se connecter" })).toBeVisible();
});
