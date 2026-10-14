import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { AppHome } from "@/components/AppHome";

export async function generateMetadata({ params }: PageProps<"/[locale]/app">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "App" });
  return { title: t("title"), robots: { index: false } };
}

export default async function AppPage({ params }: PageProps<"/[locale]/app">) {
  const { locale } = await params;
  setRequestLocale(locale);
  const t = await getTranslations("App");

  return (
    <div className="space-y-6">
      <h1 className="text-3xl font-bold">{t("title")}</h1>
      <AppHome />
    </div>
  );
}
