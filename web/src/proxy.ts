import createMiddleware from "next-intl/middleware";
import { routing } from "./i18n/routing";

// Locale detection and prefix (/fr, /en). Backend paths are left to the reverse proxy (rewrites in development).
export default createMiddleware(routing);

export const config = {
  matcher: ["/((?!api|oauth2|login|logout|_next|_vercel|.*\\..*).*)"],
};
