"use client";

import { useTranslations } from "next-intl";
import { useRef, useState } from "react";
import { deleteMyAccount, exportMyData } from "@/lib/api";
import { download } from "@/lib/export";

/** GDPR rights: data export and account deletion. */
export function AccountData() {
  const t = useTranslations("Account");
  const dialog = useRef<HTMLDialogElement>(null);
  const [error, setError] = useState(false);

  return (
    <section aria-labelledby="account-data" className="space-y-3 rounded-lg border border-zinc-200 p-5 dark:border-zinc-800">
      <h2 id="account-data" className="text-lg font-semibold">
        {t("title")}
      </h2>
      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={async () => download("jtyposquatting-data.json", await exportMyData(), "application/json")}
          className="rounded border border-zinc-300 px-3 py-1.5 text-sm dark:border-zinc-700"
        >
          {t("export")}
        </button>
        <span className="text-sm text-zinc-500">{t("exportHelp")}</span>
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={() => dialog.current?.showModal()}
          className="rounded border border-red-300 px-3 py-1.5 text-sm text-red-700 dark:border-red-800 dark:text-red-300"
        >
          {t("delete")}
        </button>
        <span className="text-sm text-zinc-500">{t("deleteHelp")}</span>
      </div>
      {error && (
        <p role="alert" className="text-sm text-red-700 dark:text-red-300">
          {t("error")}
        </p>
      )}
      <dialog
        ref={dialog}
        aria-labelledby="delete-title"
        className="m-auto max-w-md rounded-lg p-6 backdrop:bg-black/50 dark:bg-zinc-900 dark:text-zinc-100"
      >
        <h3 id="delete-title" className="mb-3 text-lg font-semibold">
          {t("confirmTitle")}
        </h3>
        <p className="mb-5 text-sm">{t("confirmBody")}</p>
        <div className="flex justify-end gap-3">
          <button type="button" autoFocus onClick={() => dialog.current?.close()} className="rounded border border-zinc-300 px-4 py-2 text-sm dark:border-zinc-700">
            {t("cancel")}
          </button>
          <button
            type="button"
            onClick={async () => {
              try {
                await deleteMyAccount();
              } catch {
                dialog.current?.close();
                setError(true);
              }
            }}
            className="rounded bg-red-700 px-4 py-2 text-sm font-medium text-white hover:bg-red-800"
          >
            {t("confirm")}
          </button>
        </div>
      </dialog>
    </section>
  );
}
