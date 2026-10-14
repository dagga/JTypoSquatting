import type { MetadataRoute } from "next";
import { routing } from "@/i18n/routing";

const PUBLIC_PAGES = ["", "/algorithms", "/faq", "/legal", "/terms", "/privacy"];

export default function sitemap(): MetadataRoute.Sitemap {
  const site = process.env.SITE_URL ?? "http://localhost:3000";
  return PUBLIC_PAGES.map((page) => ({
    url: `${site}/${routing.defaultLocale}${page}`,
    alternates: {
      languages: Object.fromEntries(routing.locales.map((locale) => [locale, `${site}/${locale}${page}`])),
    },
  }));
}
