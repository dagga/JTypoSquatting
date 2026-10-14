"use client";

import { useTranslations } from "next-intl";
import { LOGIN_URL, logout } from "@/lib/api";
import { useCurrentUser } from "@/lib/useCurrentUser";

export function UserMenu() {
  const t = useTranslations("Nav");
  const auth = useCurrentUser();

  if (auth.status === "loading") {
    return null;
  }
  if (auth.status === "anonymous") {
    // Full navigation: the backend starts the OpenID Connect flow
    return (
      <a href={LOGIN_URL} className="rounded bg-zinc-900 px-3 py-1.5 text-sm font-medium text-white dark:bg-zinc-100 dark:text-zinc-900">
        {t("login")}
      </a>
    );
  }
  return (
    <div className="flex items-center gap-3 text-sm">
      <span data-testid="current-user">{auth.user.name ?? auth.user.username}</span>
      <button type="button" onClick={() => logout()} className="text-zinc-500 underline">
        {t("logout")}
      </button>
    </div>
  );
}
