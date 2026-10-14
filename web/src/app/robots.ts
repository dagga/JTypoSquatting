import type { MetadataRoute } from "next";

export default function robots(): MetadataRoute.Robots {
  const site = process.env.SITE_URL ?? "http://localhost:3000";
  return {
    // The application and the API are private
    rules: { userAgent: "*", allow: "/", disallow: ["/api/", "/fr/app", "/en/app", "/oauth2/", "/login/"] },
    sitemap: `${site}/sitemap.xml`,
  };
}
