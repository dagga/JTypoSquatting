import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";

export default function NotFound() {
  const t = useTranslations("NotFound");
  return (
    <div className="space-y-4 py-16 text-center">
      <h1 className="text-2xl font-semibold">{t("title")}</h1>
      <Link href="/" className="underline">
        {t("back")}
      </Link>
    </div>
  );
}
