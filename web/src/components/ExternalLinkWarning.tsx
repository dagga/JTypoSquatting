"use client";

import { useTranslations } from "next-intl";
import { useEffect, useRef } from "react";

/**
 * Confirmation before opening an analysed domain in the browser (audit SEC-02): the site may be
 * hostile. The link is opened without referrer nor access to this window.
 */
export function ExternalLinkWarning({ domain, url, onClose }: { domain: string; url: string; onClose: () => void }) {
  const t = useTranslations("Warning");
  const dialog = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    dialog.current?.showModal();
  }, []);

  return (
    <dialog
      ref={dialog}
      onClose={onClose}
      aria-labelledby="warning-title"
      className="m-auto max-w-md rounded-lg p-6 backdrop:bg-black/50 dark:bg-zinc-900 dark:text-zinc-100"
    >
      <h2 id="warning-title" className="mb-3 text-lg font-semibold">
        {t("title")}
      </h2>
      <p className="mb-5 text-sm">{t("body", { domain })}</p>
      <div className="flex justify-end gap-3">
        <button
          type="button"
          autoFocus
          onClick={() => dialog.current?.close()}
          className="rounded border border-zinc-300 px-4 py-2 text-sm dark:border-zinc-700"
        >
          {t("cancel")}
        </button>
        <a
          href={url}
          target="_blank"
          rel="noopener noreferrer"
          referrerPolicy="no-referrer"
          onClick={() => dialog.current?.close()}
          className="rounded bg-red-700 px-4 py-2 text-sm font-medium text-white hover:bg-red-800"
        >
          {t("confirm")}
        </a>
      </div>
    </dialog>
  );
}
