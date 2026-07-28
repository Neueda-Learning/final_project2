import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { api } from "../api/client";
import { usePortfolio } from "../app/PortfolioContext";
import { EmptyState } from "../components/EmptyState";
import { ErrorBox } from "../components/ErrorBox";
import { PageHeader } from "../components/PageHeader";
import { formatCurrency, formatDateTime, formatQuantity } from "../lib/format";

const PAGE_SIZE = 20;

export function TransactionsPage() {
  const { portfolioId, selectedPortfolio } = usePortfolio();
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
      <PageHeader title="Transactions" subtitle="查看交易历史记录" />

      {!portfolioId ? (
        <EmptyState icon="🧾" title="请选择组合" description="左侧先选中组合后再查看交易历史。" />
      ) : null}

      {query.isError ? <ErrorBox error={query.error} onRetry={() => query.refetch()} /> : null}

      {portfolioId && !query.isPending && sorted.length === 0 ? (
        <EmptyState
          icon="🧾"
          title="暂无交易"
          description="去 Holdings 页面提交第一笔买入或卖出。"
        />
      ) : null}

      {sorted.length > 0 ? (
        <>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>时间</th>
                  <th>标的</th>
                  <th>方向</th>
                  <th className="num">数量</th>
                  <th className="num">单价</th>
                  <th className="num">手续费</th>
                  <th>备注</th>
                </tr>
              </thead>
              <tbody>
                {sorted.map((tx) => (
                  <tr key={tx.id}>
                    <td>{formatDateTime(tx.executedAt)}</td>
                    <td className="sym">{tx.symbol}</td>
                    <td>
                      <span className={tx.side === "BUY" ? "badge badge-fresh" : "badge badge-failed"}>
                        {tx.side}
                      </span>
                    </td>
                    <td className="num">{formatQuantity(tx.quantity)}</td>
                    <td className="num">{formatCurrency(tx.unitPrice, currency)}</td>
                    <td className="num">{formatCurrency(tx.feeAmount, currency)}</td>
                    <td>{tx.note ?? "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="pagination">
            <div className="pagination-info">共 {total} 条，当前第 {page} / {maxPage} 页</div>
            <div className="pagination-btns">
              <button type="button" className="btn btn-ghost btn-sm" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
                上一页
              </button>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                disabled={page >= maxPage}
                onClick={() => setPage((p) => p + 1)}
              >
                下一页
              </button>
            </div>
          </div>
        </>
      ) : null}
    </>
  );
}
