"use client";

import { AccountData } from "./AccountData";
import { AuthGate } from "./AuthGate";
import { ScanForm } from "./ScanForm";
import { ScanHistory } from "./ScanHistory";

/** Scans of the logged-in user. */
export function AppHome() {
  return (
    <AuthGate>
      {() => (
        <div className="space-y-8">
          <ScanForm />
          <ScanHistory />
          <AccountData />
        </div>
      )}
    </AuthGate>
  );
}
