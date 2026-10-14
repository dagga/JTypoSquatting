import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";

const FAMILIES = ["characters", "keyboard", "visual", "separators", "words", "suffixes"] as const;

export async function generateMetadata({ params }: PageProps<"/[locale]/algorithms">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "Algorithms" });
  return { title: t("title"), description: t("lead") };
}

export default async function AlgorithmsPage({ params }: PageProps<"/[locale]/algorithms">) {
  const { locale } = await params;
  setRequestLocale(locale);
  const t = await getTranslations("Algorithms");

  return (
    <article className="space-y-8">
      <header className="space-y-3">
        <h1 className="text-3xl font-bold">{t("title")}</h1>
        <p className="text-lg text-zinc-600 dark:text-zinc-400">{t("lead")}</p>
      </header>
      <dl className="grid gap-5 sm:grid-cols-2">
        {FAMILIES.map((family) => (
          <div key={family} className="rounded-lg border border-zinc-200 p-5 dark:border-zinc-800">
            <dt className="mb-1 font-semibold">{t(`families.${family}`)}</dt>
            <dd className="text-sm text-zinc-600 dark:text-zinc-400">{t(`families.${family}Text`)}</dd>
          </div>
        ))}
      </dl>
      <section className="space-y-2">
        <h2 className="text-xl font-semibold">{t("checksTitle")}</h2>
        <p>{t("checks")}</p>
      </section>
    </article>
  );
}
