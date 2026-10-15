"use client";

import { useFormatter, useTranslations } from "next-intl";
import { useEffect, useRef, useState } from "react";
import type { ScanResult } from "@/lib/types";
import { DomainStatusBadge, TlsStatusLabel } from "./StatusBadge";
import { ExternalLinkWarning } from "./ExternalLinkWarning";

/** Details of one checked domain: screenshot, page information, HTTP headers. */
export function ResultDetails({ result, onClose }: { result: ScanResult; onClose: () => void }) {
  const t = useTranslations("Details");
  const format = useFormatter();
  const dialog = useRef<HTMLDialogElement>(null);
  const [warning, setWarning] = useState(false);

  useEffect(() => {
    dialog.current?.showModal();
  }, []);

  return (
    <dialog
      ref={dialog}
      onClose={onClose}
      aria-labelledby="details-title"
      className="m-auto w-full max-w-3xl rounded-lg p-0 backdrop:bg-black/50 dark:bg-zinc-900 dark:text-zinc-100"
    >
      <div className="flex items-start justify-between gap-4 border-b border-zinc-200 p-5 dark:border-zinc-800">
        <div>
          <h2 id="details-title" className="text-lg font-semibold break-all">
            {result.domain}
          </h2>
          {result.domainAscii !== result.domain && (
            <p className="text-sm text-zinc-500">
              {t("punycode")} : <code>{result.domainAscii}</code>
            </p>
          )}
          <p className="mt-2 flex flex-wrap items-center gap-3 text-sm">
            <DomainStatusBadge status={result.status} />
            <span>HTTP {result.httpCode || "—"}</span>
            <span>
              TLS <TlsStatusLabel status={result.tlsStatus} />
            </span>
          </p>
        </div>
        <button
          type="button"
          onClick={() => dialog.current?.close()}
          className="rounded border border-zinc-300 px-3 py-1 text-sm dark:border-zinc-700"
        >
          {t("close")}
        </button>
      </div>
      <div className="max-h-[70vh] space-y-5 overflow-y-auto p-5">
        <section>
          <h3 className="mb-2 font-medium">{t("screenshot")}</h3>
          {result.screenshotUrl ? (
            // Served by the backend, same origin; not optimised by next/image on purpose
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={result.screenshotUrl}
              alt={t("screenshot") + " — " + result.domain}
              className="w-full rounded border border-zinc-200 dark:border-zinc-800"
            />
          ) : (
            <p className="text-sm text-zinc-500">{t("noScreenshot")}</p>
          )}
        </section>
        {result.title && <p className="font-medium">{result.title}</p>}
        {result.description && (
          <section>
            <h3 className="mb-1 font-medium">{t("description")}</h3>
            <p className="text-sm">{result.description}</p>
          </section>
        )}
        {result.homepageText && (
          <section>
            <h3 className="mb-1 font-medium">{t("homepage")}</h3>
            <p className="text-sm text-zinc-600 dark:text-zinc-400">{result.homepageText}</p>
          </section>
        )}
        {result.httpHeaders && Object.keys(result.httpHeaders).length > 0 && (
          <section>
            <h3 className="mb-1 font-medium">{t("headers")}</h3>
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-xs">
              {Object.entries(result.httpHeaders).map(([name, value]) => (
                <div key={name} className="contents">
                  <dt className="font-mono text-zinc-500">{name}</dt>
                  <dd className="font-mono break-all">{value}</dd>
                </div>
              ))}
            </dl>
          </section>
        )}
        <div className="flex items-center justify-between text-sm text-zinc-500">
          <span>{t("checkedAt", { date: format.dateTime(new Date(result.checkedAt), { dateStyle: "medium", timeStyle: "short" }) })}</span>
          {result.httpCode > 0 && (
            <button type="button" onClick={() => setWarning(true)} className="text-red-700 underline dark:text-red-300">
              {t("visit")}
            </button>
          )}
        </div>
      </div>
      {warning && (
        <ExternalLinkWarning
          domain={result.domain}
          url={`http://${result.domainAscii}/`}
          onClose={() => setWarning(false)}
        />
      )}
    </dialog>
  );
}
