// Export of scan results. Page titles and descriptions come from hostile sites: CSV cells that a
// spreadsheet would evaluate as formulas are neutralised.

import type { ScanResult } from "./types";

const COLUMNS: (keyof ScanResult)[] = [
  "domain",
  "domainAscii",
  "status",
  "httpCode",
  "tlsStatus",
  "title",
  "language",
  "description",
  "checkedAt",
];

export function csvCell(value: unknown): string {
  let text = value == null ? "" : String(value);
  if (/^[=+\-@\t\r]/.test(text)) {
    text = "'" + text;
  }
  return /[",\r\n;]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

export function toCsv(results: ScanResult[]): string {
  const lines = [COLUMNS.join(",")];
  for (const result of results) {
    lines.push(COLUMNS.map((column) => csvCell(result[column])).join(","));
  }
  return lines.join("\r\n") + "\r\n";
}

export function toJson(results: ScanResult[]): string {
  return JSON.stringify(results, null, 2);
}

export function download(filename: string, content: string, type: string): void {
  // BOM so that spreadsheets detect UTF-8 (internationalised domains)
  const blob = new Blob([type.startsWith("text/csv") ? "﻿" + content : content], { type });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
