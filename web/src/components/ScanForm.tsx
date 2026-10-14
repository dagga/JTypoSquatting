"use client";

import { useTranslations } from "next-intl";
import { useState } from "react";
import { useRouter } from "@/i18n/navigation";
import { ApiError, createScan } from "@/lib/api";

export function ScanForm() {
  const t = useTranslations("App");
  const router = useRouter();
  const [domain, setDomain] = useState("");
  const [forceRefresh, setForceRefresh] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const scan = await createScan(domain.trim(), forceRefresh);
      router.push(`/app/scans/${scan.id}`);
    } catch (e) {
      if (e instanceof ApiError && e.status === 400) {
        setError(t("errors.invalidDomain"));
      } else if (e instanceof ApiError && e.status === 429) {
        setError(t("errors.quota", { message: e.message, seconds: e.retryAfter ?? 60 }));
      } else {
        setError(t("errors.generic"));
      }
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-3 rounded-lg border border-zinc-200 p-5 dark:border-zinc-800">
      <h2 className="text-lg font-semibold">{t("newScan")}</h2>
      <div className="flex flex-wrap gap-3">
        <label className="sr-only" htmlFor="domain">
          {t("domainLabel")}
        </label>
        <input
          id="domain"
          name="domain"
          required
          value={domain}
          onChange={(event) => setDomain(event.target.value)}
          placeholder={t("domainPlaceholder")}
          autoComplete="off"
          spellCheck={false}
          className="min-w-64 flex-1 rounded border border-zinc-300 px-3 py-2 font-mono dark:border-zinc-700 dark:bg-zinc-900"
        />
        <button
          type="submit"
          disabled={submitting}
          className="rounded bg-zinc-900 px-5 py-2 font-medium text-white disabled:opacity-50 dark:bg-zinc-100 dark:text-zinc-900"
        >
          {submitting ? t("starting") : t("start")}
        </button>
      </div>
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" checked={forceRefresh} onChange={(event) => setForceRefresh(event.target.checked)} />
        {t("forceRefresh")}
      </label>
      {error && (
        <p role="alert" className="text-sm text-red-700 dark:text-red-300">
          {error}
        </p>
      )}
    </form>
  );
}
