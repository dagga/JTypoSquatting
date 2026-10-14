"use client";

import { useFormatter, useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import { Link } from "@/i18n/navigation";
import { listScans } from "@/lib/api";
import { isActive, type Scan } from "@/lib/types";
import { ScanStatusBadge } from "./StatusBadge";

const REFRESH_MS = 5000;

export function ScanHistory() {
  const t = useTranslations();
  const format = useFormatter();
  const [scans, setScans] = useState<Scan[] | null>(null);

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    let active = true;
    async function load() {
      try {
        const loaded = await listScans();
        if (!active) return;
        setScans(loaded);
        // Refresh while scans are in progress
        if (loaded.some((scan) => isActive(scan.status))) timer = setTimeout(load, REFRESH_MS);
      } catch {
        if (active) setScans([]);
      }
    }
    load();
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, []);

  return (
    <section aria-labelledby="history" className="space-y-3">
      <h2 id="history" className="text-lg font-semibold">
        {t("App.history")}
      </h2>
      {scans === null ? (
        <p className="text-sm text-zinc-500">{t("App.loading")}</p>
      ) : scans.length === 0 ? (
        <p className="text-sm text-zinc-500">{t("App.empty")}</p>
      ) : (
        <ul className="divide-y divide-zinc-200 rounded-lg border border-zinc-200 dark:divide-zinc-800 dark:border-zinc-800">
          {scans.map((scan) => (
            <li key={scan.id}>
              <Link href={`/app/scans/${scan.id}`} className="flex flex-wrap items-center gap-4 px-4 py-3 hover:bg-zinc-50 dark:hover:bg-zinc-900">
                <span className="font-mono font-medium">{scan.baseDomain}</span>
                <ScanStatusBadge status={scan.status} />
                <span className="text-sm text-zinc-500">
                  {t("Scan.progress", { checked: scan.checkedDomains, total: scan.totalDomains })}
                </span>
                <span className="ml-auto text-sm text-zinc-500">
                  {format.dateTime(new Date(scan.createdAt), { dateStyle: "medium", timeStyle: "short" })}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
