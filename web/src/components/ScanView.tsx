"use client";

import { useTranslations } from "next-intl";
import { use, useEffect, useMemo, useRef, useState } from "react";
import { Link } from "@/i18n/navigation";
import { ApiError, cancelScan, followScan, getScan } from "@/lib/api";
import { download, toCsv, toJson } from "@/lib/export";
import { isActive, type Scan, type ScanResult } from "@/lib/types";
import { AuthGate } from "./AuthGate";
import { Counters } from "./Counters";
import { ResultDetails } from "./ResultDetails";
import { ResultsTable } from "./ResultsTable";
import { ScanStatusBadge } from "./StatusBadge";

// Results received from the stream are applied in batches, so that thousands of events stay fluid
const FLUSH_MS = 250;

export function ScanView({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  // key: a new scan starts from an empty state
  return <AuthGate>{() => <LiveScan key={id} id={id} />}</AuthGate>;
}

function LiveScan({ id }: { id: string }) {
  const t = useTranslations();
  const [scan, setScan] = useState<Scan | null>(null);
  const [results, setResults] = useState<ScanResult[]>([]);
  const [error, setError] = useState<"forbidden" | "notFound" | "generic" | null>(null);
  const [streamLost, setStreamLost] = useState(false);
  const [filter, setFilter] = useState("");
  const [onlyOnline, setOnlyOnline] = useState(false);
  const [selected, setSelected] = useState<ScanResult | null>(null);
  const pending = useRef<ScanResult[]>([]);

  useEffect(() => {
    // The stream is opened once the scan is loaded, possibly after the cleanup has run
    // (unmount, or the double mount of React StrictMode): it must then not be opened at all
    let cancelled = false;
    let closeStream: (() => void) | undefined;
    const seen = new Set<number>();
    const flush = () => {
      if (pending.current.length === 0) return;
      const batch = pending.current;
      pending.current = [];
      setResults((current) => current.concat(batch));
    };
    const timer = setInterval(flush, FLUSH_MS);

    getScan(id)
      .then((loaded) => {
        if (cancelled) return;
        setScan(loaded);
        closeStream = followScan(id, {
          onResult: (result) => {
            // Results may be sent again after a reconnection
            if (!seen.has(result.id)) {
              seen.add(result.id);
              pending.current.push(result);
            }
          },
          onProgress: setScan,
          onEnd: (final) => {
            flush();
            setScan(final);
          },
          onError: () => setStreamLost(true),
        });
      })
      .catch((e) => {
        if (cancelled) return;
        if (e instanceof ApiError && e.status === 403) setError("forbidden");
        else if (e instanceof ApiError && e.status === 404) setError("notFound");
        else setError("generic");
      });

    return () => {
      cancelled = true;
      clearInterval(timer);
      closeStream?.();
      pending.current = [];
    };
  }, [id]);

  const visible = useMemo(() => {
    const needle = filter.trim().toLowerCase();
    return results.filter(
      (result) =>
        (!onlyOnline || result.httpCode > 0) &&
        (!needle ||
          result.domain.toLowerCase().includes(needle) ||
          result.domainAscii.includes(needle) ||
          (result.title ?? "").toLowerCase().includes(needle)),
    );
  }, [results, filter, onlyOnline]);

  if (error) {
    return (
      <p role="alert" className="text-red-700 dark:text-red-300">
        {t(`App.errors.${error}`)}
      </p>
    );
  }
  if (!scan) {
    return <p className="text-zinc-500">{t("App.loading")}</p>;
  }

  const fileName = `jtyposquatting-${scan.baseDomain}`;
  return (
    <div className="space-y-6">
      <Link href="/app" className="text-sm text-zinc-500 hover:underline">
        ← {t("Scan.back")}
      </Link>
      <header className="flex flex-wrap items-center gap-4">
        <h1 className="text-2xl font-bold">{t("Scan.titleFor", { domain: scan.baseDomain })}</h1>
        <ScanStatusBadge status={scan.status} />
        <span className="text-sm text-zinc-500" data-testid="progress">
          {t("Scan.progress", { checked: scan.checkedDomains, total: scan.totalDomains })}
        </span>
        {isActive(scan.status) && (
          <button
            type="button"
            onClick={async () => {
              await cancelScan(scan.id);
              setScan({ ...scan, status: "CANCELLED" });
            }}
            className="ml-auto rounded border border-red-300 px-3 py-1.5 text-sm text-red-700 dark:border-red-800 dark:text-red-300"
          >
            {t("Scan.cancel")}
          </button>
        )}
      </header>
      {streamLost && (
        <p role="alert" className="text-sm text-red-700 dark:text-red-300">
          {t("Scan.streamError")}
        </p>
      )}
      <Counters results={results} />
      <div className="flex flex-wrap items-center gap-4">
        <label className="flex items-center gap-2 text-sm">
          {t("Scan.filter")}
          <input
            type="search"
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
            className="rounded border border-zinc-300 px-2 py-1 dark:border-zinc-700 dark:bg-zinc-900"
          />
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={onlyOnline} onChange={(event) => setOnlyOnline(event.target.checked)} />
          {t("Scan.onlyOnline")}
        </label>
        <div className="ml-auto flex gap-2 text-sm">
          <span className="text-zinc-500">{t("Scan.export")} :</span>
          <button type="button" className="underline" onClick={() => download(`${fileName}.csv`, toCsv(visible), "text/csv;charset=utf-8")}>
            CSV
          </button>
          <button type="button" className="underline" onClick={() => download(`${fileName}.json`, toJson(visible), "application/json")}>
            JSON
          </button>
        </div>
      </div>
      <ResultsTable results={visible} onSelect={setSelected} />
      {selected && <ResultDetails result={selected} onClose={() => setSelected(null)} />}
    </div>
  );
}
