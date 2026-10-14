import { getTranslations, setRequestLocale } from "next-intl/server";
import { Link } from "@/i18n/navigation";

export default async function HomePage({ params }: PageProps<"/[locale]">) {
  const { locale } = await params;
  setRequestLocale(locale);
  const t = await getTranslations("Home");
  const steps = [1, 2, 3] as const;

  return (
    <div className="space-y-12">
      <section className="space-y-5 py-8">
        <h1 className="max-w-3xl text-4xl font-bold tracking-tight">{t("title")}</h1>
        <p className="max-w-2xl text-lg text-zinc-600 dark:text-zinc-400">{t("lead")}</p>
        <Link href="/app" className="inline-block rounded bg-zinc-900 px-5 py-3 font-medium text-white hover:bg-zinc-700 dark:bg-zinc-100 dark:text-zinc-900">
          {t("cta")}
        </Link>
      </section>
      <section aria-labelledby="how">
        <h2 id="how" className="mb-6 text-2xl font-semibold">
          {t("how")}
        </h2>
        <ol className="grid gap-6 sm:grid-cols-3">
          {steps.map((step) => (
            <li key={step} className="rounded-lg border border-zinc-200 p-5 dark:border-zinc-800">
              <h3 className="mb-2 font-semibold">
                {step}. {t(`step${step}Title`)}
              </h3>
              <p className="text-sm text-zinc-600 dark:text-zinc-400">{t(`step${step}`)}</p>
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
}
