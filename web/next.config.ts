import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const isDev = process.env.NODE_ENV === "development";

// Backend (Spring Boot). In production, the reverse proxy routes these paths; in development, Next forwards them.
const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8081";
const BACKEND_PATHS = ["/api/:path*", "/oauth2/:path*", "/login/oauth2/:path*", "/logout"];

// Static policy, compatible with prerendering (a nonce-based policy would make every page dynamic).
// React needs 'unsafe-eval' in development only.
const contentSecurityPolicy = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${isDev ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self'",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join("; ");

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  output: "standalone",
  poweredByHeader: false,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "Content-Security-Policy", value: contentSecurityPolicy },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=(), usb=()" },
        ],
      },
    ];
  },
  async rewrites() {
    if (!isDev) return [];
    return BACKEND_PATHS.map((path) => ({ source: path, destination: `${BACKEND_URL}${path}` }));
  },
};

export default createNextIntlPlugin("./src/i18n/request.ts")(nextConfig);
