import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";

const QUESTIONS = [1, 2, 3, 4] as const;

export async function generateMetadata({ params }: PageProps<"/[locale]/faq">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "Faq" });
  return { title: t("title") };
}

export default async function FaqPage({ params }: PageProps<"/[locale]/faq">) {
  const { locale } = await params;
  setRequestLocale(locale);
  const t = await getTranslations("Faq");

  return (
    <article className="space-y-6">
      <h1 className="text-3xl font-bold">{t("title")}</h1>
      {QUESTIONS.map((n) => (
        <section key={n} className="space-y-1">
          <h2 className="font-semibold">{t(`q${n}`)}</h2>
          <p className="text-zinc-600 dark:text-zinc-400">{t(`a${n}`)}</p>
        </section>
      ))}
    </article>
  );
}
