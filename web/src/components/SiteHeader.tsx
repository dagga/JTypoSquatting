import { useTranslations } from "next-intl";
import { Suspense } from "react";
import { Link } from "@/i18n/navigation";
import { LocaleSwitcher } from "./LocaleSwitcher";

export function SiteHeader() {
  const t = useTranslations("Nav");
  return (
    <header className="border-b border-zinc-200 dark:border-zinc-800">
      <a href="#content" className="sr-only focus:not-sr-only focus:absolute focus:p-2">
        {t("skip")}
      </a>
      <nav className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
        <Link href="/" className="text-lg font-semibold">
          JTypoSquatting
        </Link>
        <Link href="/algorithms" className="text-sm hover:underline">
          {t("algorithms")}
        </Link>
        <Link href="/faq" className="text-sm hover:underline">
          {t("faq")}
        </Link>
        <Link href="/app" className="text-sm hover:underline">
          {t("app")}
        </Link>
        <div className="ml-auto flex items-center gap-4">
          {/* Reads the current URL: rendered at request time */}
          <Suspense fallback={null}>
            <LocaleSwitcher />
          </Suspense>
        </div>
      </nav>
    </header>
  );
}
