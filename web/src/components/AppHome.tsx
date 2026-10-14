"use client";

import { AuthGate } from "./AuthGate";

/** Scans of the logged-in user. */
export function AppHome() {
  return <AuthGate>{() => null}</AuthGate>;
}
