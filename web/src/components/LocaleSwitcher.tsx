"use client";

import { useLocale, useTranslations } from "next-intl";
import { Link, usePathname } from "@/i18n/navigation";
import { routing } from "@/i18n/routing";

export function LocaleSwitcher() {
  const t = useTranslations("Nav");
  const current = useLocale();
  const pathname = usePathname();
  return (
    <ul aria-label={t("language")} className="flex gap-2 text-sm">
      {routing.locales.map((locale) => (
        <li key={locale}>
          <Link
            href={pathname}
            locale={locale}
            aria-current={locale === current ? "true" : undefined}
            className={locale === current ? "font-semibold" : "text-zinc-500 hover:underline"}
          >
            {locale.toUpperCase()}
          </Link>
        </li>
      ))}
    </ul>
  );
}
