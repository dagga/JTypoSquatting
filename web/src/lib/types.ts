// Types of the backend API (see doc/API.md, section 3)

export type ScanStatus = "PENDING" | "RUNNING" | "COMPLETED" | "CANCELLED" | "FAILED";

export type DomainStatus =
  | "Active"
  | "Dead"
  | "Unreachable"
  | "Suspicious"
  | "Safe"
  | "Testing..."
  | "Unknown";

export type TlsStatus = "VALID" | "EXPIRED" | "HOSTNAME_MISMATCH" | "UNTRUSTED" | "HANDSHAKE_FAILED" | "NONE";

export interface User {
  id: string;
  username: string | null;
  name: string | null;
  email: string | null;
}

export interface Scan {
  id: string;
  baseDomain: string;
  status: ScanStatus;
  totalDomains: number;
  checkedDomains: number;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  error: string | null;
}

export interface ScanResult {
  id: number;
  /** Unicode form, for display */
  domain: string;
  /** ASCII/punycode form, the actual DNS name */
  domainAscii: string;
  status: DomainStatus;
  httpCode: number;
  tlsStatus: TlsStatus;
  title: string | null;
  language: string | null;
  description: string | null;
  homepageText: string | null;
  httpHeaders: Record<string, string> | null;
  screenshotUrl: string | null;
  checkedAt: string;
}

export function isActive(status: ScanStatus): boolean {
  return status === "PENDING" || status === "RUNNING";
}
