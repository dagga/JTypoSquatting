import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { ScanView } from "@/components/ScanView";

export async function generateMetadata({ params }: PageProps<"/[locale]/app/scans/[id]">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "App" });
  return { title: t("title"), robots: { index: false } };
}

export default function ScanPage({ params }: PageProps<"/[locale]/app/scans/[id]">) {
  // The scan id is only known at request time: read it inside Suspense
  return (
    <Suspense fallback={null}>
      <ScanView params={params} />
    </Suspense>
  );
}
