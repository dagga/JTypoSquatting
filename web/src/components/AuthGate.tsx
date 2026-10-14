"use client";

import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { LOGIN_URL } from "@/lib/api";
import type { User } from "@/lib/types";
import { useCurrentUser } from "@/lib/useCurrentUser";

/** Renders its content for logged-in users only; the API enforces it anyway (401). */
export function AuthGate({ children }: { children: (user: User) => ReactNode }) {
  const t = useTranslations();
  const auth = useCurrentUser();

  if (auth.status === "loading") {
    return <p className="text-zinc-500">{t("App.loading")}</p>;
  }
  if (auth.status === "anonymous") {
    // Only rendered in the browser, after the session check. Set by the backend when the login failed.
    const loginFailed = new URLSearchParams(window.location.search).has("loginError");
    return (
      <div className="space-y-4 rounded-lg border border-zinc-200 p-6 dark:border-zinc-800">
        {loginFailed && (
          <p role="alert" className="text-red-700 dark:text-red-300">
            {t("App.loginFailed")}
          </p>
        )}
        <p>{t("App.loginRequired")}</p>
        <a href={LOGIN_URL} className="inline-block rounded bg-zinc-900 px-4 py-2 font-medium text-white dark:bg-zinc-100 dark:text-zinc-900">
          {t("Nav.login")}
        </a>
      </div>
    );
  }
  return <>{children(auth.user)}</>;
}
