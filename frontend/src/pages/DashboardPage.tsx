import { useQuery } from "@tanstack/react-query";

import { api } from "../api/client";
import { usePortfolio } from "../app/PortfolioContext";
import { EmptyState } from "../components/EmptyState";
import { ErrorBox } from "../components/ErrorBox";
import { PageHeader } from "../components/PageHeader";
import { SummarySkeleton, TableSkeleton } from "../components/Skeletons";
import { SummaryCards } from "../components/SummaryCards";
import { AllocationChart, PerformanceChart } from "../components/charts";
import { PriceStatusBadge } from "../components/StatusBadge";
import { formatCurrency, formatDate, formatPercent, formatQuantity, pnlSign } from "../lib/format";

export function DashboardPage() {
  const { portfolioId, selectedPortfolio } = usePortfolio();

  const dashboardQuery = useQuery({
    queryKey: ["dashboard", portfolioId],
    queryFn: () => api.analytics.getDashboard(portfolioId!),
    enabled: Boolean(portfolioId),
  });

  const performanceQuery = useQuery({
    queryKey: ["performance", portfolioId],
    queryFn: () => api.analytics.getPerformance(portfolioId!),
    enabled: Boolean(portfolioId),
  });

  const currency = selectedPortfolio?.baseCurrency ?? "USD";

  return (
    <>
      <PageHeader
        title="Dashboard"
        subtitle={selectedPortfolio ? `${selectedPortfolio.name} 的估值与表现` : "选择组合后查看数据"}
      />

      {!portfolioId ? (
        <EmptyState
          icon="🧭"
          title="请选择一个组合"
          description="左侧选择组合后即可查看摘要、持仓、配置和历史表现。"
        />
      ) : null}

      {dashboardQuery.isPending ? <SummarySkeleton /> : null}
      {dashboardQuery.isError ? <ErrorBox error={dashboardQuery.error} onRetry={() => dashboardQuery.refetch()} /> : null}

      {dashboardQuery.data ? (
        <>
          <SummaryCards summary={dashboardQuery.data.summary} currency={currency} />

          <section className="charts-row" aria-label="组合图表">
            <article className="chart-card">
              <h2 className="chart-card__title">资产配置</h2>
              {dashboardQuery.data.allocation.length > 0 ? (
                <AllocationChart data={dashboardQuery.data.allocation} currency={currency} />
              ) : (
                <EmptyState
                  icon="🧩"
                  title="暂无可配置资产"
                  description="当前没有可计入配置图的已定价持仓。"
                />
              )}
            </article>
            <article className="chart-card">
              <h2 className="chart-card__title">历史估值</h2>
              {performanceQuery.isPending ? (
                <TableSkeleton />
              ) : performanceQuery.isError ? (
                <ErrorBox error={performanceQuery.error} onRetry={() => performanceQuery.refetch()} />
              ) : performanceQuery.data && performanceQuery.data.points.length > 0 ? (
                <PerformanceChart points={performanceQuery.data.points} currency={currency} />
              ) : (
                <EmptyState
                  icon="📉"
                  title="暂无估值历史"
                  description="完成行情同步后，系统会逐日生成估值快照。"
                />
              )}
            </article>
          </section>

          <section>
            <div className="section-header">
              <h2 className="section-title">持仓明细</h2>
            </div>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>标的</th>
                    <th>类型</th>
                    <th className="num">数量</th>
                    <th className="num">成本</th>
                    <th className="num">现价</th>
                    <th className="num">市值</th>
                    <th className="num">未实现盈亏</th>
                    <th className="num">收益率</th>
                    <th>价格日期</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  {dashboardQuery.data.positions.map((position) => (
                    <tr key={position.instrumentId}>
                      <td className="sym">{position.symbol}</td>
                      <td>{position.assetType}</td>
                      <td className="num">{formatQuantity(position.quantity)}</td>
                      <td className="num">{formatCurrency(position.costBasis, currency)}</td>
                      <td className="num">{formatCurrency(position.closePrice, currency)}</td>
                      <td className="num">{formatCurrency(position.marketValue, currency)}</td>
                      <td className={`num ${pnlSign(position.unrealizedPnl)}`}>
                        {formatCurrency(position.unrealizedPnl, currency)}
                      </td>
                      <td className={`num ${pnlSign(position.returnPct)}`}>{formatPercent(position.returnPct)}</td>
                      <td>{formatDate(position.priceDate)}</td>
                      <td>
                        <PriceStatusBadge status={position.priceStatus} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      ) : null}
    </>
  );
}
