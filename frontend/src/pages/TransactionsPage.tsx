import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { api } from "../api/client";
import { usePortfolio } from "../app/PortfolioContext";
import { EmptyState } from "../components/EmptyState";
import { ErrorBox } from "../components/ErrorBox";
import { PageHeader } from "../components/PageHeader";
import { formatCurrency, formatDateTime, formatQuantity } from "../lib/format";
import { useLanguage } from "../i18n/LanguageContext";

const PAGE_SIZE = 20;

export function TransactionsPage() {
  const { portfolioId, selectedPortfolio } = usePortfolio();
  const { locale, t } = useLanguage();
  const [page, setPage] = useState(1);

  const query = useQuery({
    queryKey: ["transactions", portfolioId, page],
    queryFn: () => api.transactions.list(portfolioId!, page, PAGE_SIZE),
    enabled: Boolean(portfolioId),
  });

  const items = query.data?.items ?? [];
  const total = query.data?.total ?? 0;
  const maxPage = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const sorted = useMemo(
    () => [...items].sort((a, b) => new Date(b.executedAt).getTime() - new Date(a.executedAt).getTime()),
    [items],
  );

  const currency = selectedPortfolio?.baseCurrency ?? "USD";

  return (
    <>
      <PageHeader title={t("transactions.title")} subtitle={t("transactions.subtitle")} />

      {!portfolioId ? (
        <EmptyState icon="🧾" title={t("common.selectPortfolio")} description={t("transactions.noPortfolioDescription")} />
      ) : null}

      {query.isError ? <ErrorBox error={query.error} onRetry={() => query.refetch()} /> : null}

      {portfolioId && !query.isPending && sorted.length === 0 ? (
        <EmptyState
          icon="🧾"
          title={t("transactions.emptyTitle")}
          description={t("transactions.emptyDescription")}
        />
      ) : null}

      {sorted.length > 0 ? (
        <>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>{t("table.time")}</th>
                  <th>{t("table.symbol")}</th>
                  <th>{t("table.side")}</th>
                  <th className="num">{t("table.quantity")}</th>
                  <th className="num">{t("table.unitPrice")}</th>
                  <th className="num">{t("table.fee")}</th>
                  <th>{t("table.note")}</th>
                </tr>
              </thead>
              <tbody>
                {sorted.map((tx) => (
                  <tr key={tx.id}>
                    <td>{formatDateTime(tx.executedAt, locale)}</td>
                    <td className="sym">{tx.symbol}</td>
                    <td>
                      <span className={tx.side === "BUY" ? "badge badge-fresh" : "badge badge-failed"}>
                        {tx.side}
                      </span>
                    </td>
                    <td className="num">{formatQuantity(tx.quantity, locale)}</td>
                    <td className="num">{formatCurrency(tx.unitPrice, currency, locale)}</td>
                    <td className="num">{formatCurrency(tx.feeAmount, currency, locale)}</td>
                    <td>{tx.note ?? "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="pagination">
            <div className="pagination-info">{t("transactions.page", { total, page, maxPage })}</div>
            <div className="pagination-btns">
              <button type="button" className="btn btn-ghost btn-sm" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
                {t("transactions.previous")}
              </button>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                disabled={page >= maxPage}
                onClick={() => setPage((p) => p + 1)}
              >
                {t("transactions.next")}
              </button>
            </div>
          </div>
        </>
      ) : null}
    </>
  );
}
