"use client";

import { useTranslations } from "next-intl";
import type { ScanResult } from "@/lib/types";

export function countResults(results: ScanResult[]) {
  let online = 0;
  let suspicious = 0;
  let unreachable = 0;
  for (const result of results) {
    if (result.httpCode > 0) online++;
    if (result.status === "Suspicious") suspicious++;
    if (result.httpCode === 0) unreachable++;
  }
  return { checked: results.length, online, suspicious, unreachable };
}

export function Counters({ results }: { results: ScanResult[] }) {
  const t = useTranslations("Scan.counters");
  const counts = countResults(results);
  const items: [string, number, string][] = [
    [t("checked"), counts.checked, "text-zinc-900 dark:text-zinc-100"],
    [t("online"), counts.online, "text-amber-700 dark:text-amber-300"],
    [t("suspicious"), counts.suspicious, "text-red-700 dark:text-red-300"],
    [t("unreachable"), counts.unreachable, "text-zinc-500"],
  ];
  return (
    <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
      {items.map(([label, value, color]) => (
        <div key={label} className="rounded-lg border border-zinc-200 p-3 dark:border-zinc-800">
          <dt className="text-xs uppercase tracking-wide text-zinc-500">{label}</dt>
          <dd className={`text-2xl font-semibold tabular-nums ${color}`} data-testid={`counter-${label}`}>
            {value}
          </dd>
        </div>
      ))}
    </dl>
  );
}
