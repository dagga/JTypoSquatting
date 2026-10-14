"use client";

import { useVirtualizer } from "@tanstack/react-virtual";
import { useTranslations } from "next-intl";
import { useRef } from "react";
import type { ScanResult } from "@/lib/types";
import { DomainStatusBadge, TlsStatusLabel } from "./StatusBadge";

const ROW_HEIGHT = 40;
const GRID = "grid grid-cols-[minmax(12rem,2fr)_7rem_4rem_7rem_minmax(10rem,3fr)_6rem] items-center gap-3 px-3";

/**
 * Results of a scan. Only the visible rows are rendered, so that thousands of results stay fluid.
 */
export function ResultsTable({ results, onSelect }: { results: ScanResult[]; onSelect: (result: ScanResult) => void }) {
  const t = useTranslations("Scan");
  const parent = useRef<HTMLDivElement>(null);
  // The React Compiler is not enabled: the memoisation warning of this rule does not apply
  // eslint-disable-next-line react-hooks/incompatible-library
  const virtualizer = useVirtualizer({
    count: results.length,
    getScrollElement: () => parent.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 15,
  });

  if (results.length === 0) {
    return <p className="py-8 text-center text-sm text-zinc-500">{t("noResults")}</p>;
  }

  return (
    <div role="table" aria-rowcount={results.length + 1} className="rounded-lg border border-zinc-200 text-sm dark:border-zinc-800">
      <div role="row" className={`${GRID} h-10 border-b border-zinc-200 font-medium text-zinc-600 dark:border-zinc-800 dark:text-zinc-400`}>
        <span role="columnheader">{t("columns.domain")}</span>
        <span role="columnheader">{t("columns.status")}</span>
        <span role="columnheader">{t("columns.http")}</span>
        <span role="columnheader">{t("columns.tls")}</span>
        <span role="columnheader">{t("columns.title")}</span>
        <span role="columnheader">{t("columns.language")}</span>
      </div>
      <div ref={parent} className="h-[60vh] overflow-y-auto" data-testid="results-scroll">
        <div style={{ height: virtualizer.getTotalSize(), position: "relative" }}>
          {virtualizer.getVirtualItems().map((item) => {
            const result = results[item.index];
            return (
              <div
                key={result.id}
                role="row"
                aria-rowindex={item.index + 2}
                tabIndex={0}
                onClick={() => onSelect(result)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    onSelect(result);
                  }
                }}
                className={`${GRID} absolute left-0 w-full cursor-pointer border-b border-zinc-100 hover:bg-zinc-50 focus:bg-zinc-100 focus:outline-none dark:border-zinc-900 dark:hover:bg-zinc-900 dark:focus:bg-zinc-800`}
                style={{ height: item.size, transform: `translateY(${item.start}px)` }}
              >
                <span role="cell" className="truncate font-mono" title={result.domainAscii}>
                  {result.domain}
                </span>
                <span role="cell">
                  <DomainStatusBadge status={result.status} />
                </span>
                <span role="cell" className="tabular-nums">{result.httpCode || "—"}</span>
                <span role="cell">
                  <TlsStatusLabel status={result.tlsStatus} />
                </span>
                <span role="cell" className="truncate" title={result.title ?? undefined}>
                  {result.title}
                </span>
                <span role="cell" className="truncate text-zinc-500">{result.language}</span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
