"use client";

import { useTranslations } from "next-intl";
import type { DomainStatus, ScanStatus, TlsStatus } from "@/lib/types";

const DOMAIN_STATUS_STYLE: Record<DomainStatus, string> = {
  Suspicious: "bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-200",
  Safe: "bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200",
  Active: "bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200",
  Unreachable: "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300",
  Dead: "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300",
  "Testing...": "bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-200",
  Unknown: "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300",
};

const SCAN_STATUS_STYLE: Record<ScanStatus, string> = {
  PENDING: "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-200",
  RUNNING: "bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-200",
  COMPLETED: "bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-200",
  CANCELLED: "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300",
  FAILED: "bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-200",
};

const BADGE = "inline-block rounded px-2 py-0.5 text-xs font-medium whitespace-nowrap";

export function DomainStatusBadge({ status }: { status: DomainStatus }) {
  const t = useTranslations("Scan.domainStatus");
  return <span className={`${BADGE} ${DOMAIN_STATUS_STYLE[status]}`}>{t(status)}</span>;
}

export function ScanStatusBadge({ status }: { status: ScanStatus }) {
  const t = useTranslations("Scan.status");
  return <span className={`${BADGE} ${SCAN_STATUS_STYLE[status]}`}>{t(status)}</span>;
}

export function TlsStatusLabel({ status }: { status: TlsStatus }) {
  const t = useTranslations("Scan.tlsStatus");
  const warn = status !== "VALID" && status !== "NONE";
  return <span className={warn ? "font-medium text-red-700 dark:text-red-300" : undefined}>{t(status)}</span>;
}
