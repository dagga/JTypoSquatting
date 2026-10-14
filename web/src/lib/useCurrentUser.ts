"use client";

import { useEffect, useState } from "react";
import { getCurrentUser } from "./api";
import type { User } from "./types";

export type AuthState = { status: "loading" } | { status: "anonymous" } | { status: "authenticated"; user: User };

/** Logged-in user, read from the backend session (GET /api/me). */
export function useCurrentUser(): AuthState {
  const [state, setState] = useState<AuthState>({ status: "loading" });
  useEffect(() => {
    let active = true;
    getCurrentUser()
      .then((user) => active && setState(user ? { status: "authenticated", user } : { status: "anonymous" }))
      .catch(() => active && setState({ status: "anonymous" }));
    return () => {
      active = false;
    };
  }, []);
  return state;
}
