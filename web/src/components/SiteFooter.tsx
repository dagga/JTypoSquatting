import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";

export function SiteFooter() {
  const t = useTranslations("Footer");
  return (
    <footer className="border-t border-zinc-200 dark:border-zinc-800">
      <div className="mx-auto flex max-w-6xl flex-wrap gap-6 px-4 py-6 text-sm text-zinc-500">
        <span>© JTypoSquatting</span>
        <Link href="/legal" className="hover:underline">
          {t("legal")}
        </Link>
        <Link href="/terms" className="hover:underline">
          {t("terms")}
        </Link>
        <Link href="/privacy" className="hover:underline">
          {t("privacy")}
        </Link>
      </div>
    </footer>
  );
}
