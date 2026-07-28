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
import { useLanguage } from "../i18n/LanguageContext";

export function DashboardPage() {
  const { portfolioId, selectedPortfolio } = usePortfolio();
  const { locale, t } = useLanguage();

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
        title={t("dashboard.title")}
        subtitle={
          selectedPortfolio
            ? t("dashboard.subtitle", { name: selectedPortfolio.name })
            : t("dashboard.noSelectionSubtitle")
        }
      />

      {!portfolioId ? (
        <EmptyState
          icon="🧭"
          title={t("dashboard.emptyTitle")}
          description={t("dashboard.emptyDescription")}
        />
      ) : null}

      {dashboardQuery.isPending ? <SummarySkeleton /> : null}
      {dashboardQuery.isError ? <ErrorBox error={dashboardQuery.error} onRetry={() => dashboardQuery.refetch()} /> : null}

      {dashboardQuery.data ? (
        <>
          <SummaryCards summary={dashboardQuery.data.summary} currency={currency} />

          <section className="charts-row" aria-label={t("dashboard.charts")}>
            <article className="chart-card">
              <h2 className="chart-card__title">{t("dashboard.allocation")}</h2>
              {dashboardQuery.data.allocation.length > 0 ? (
                <AllocationChart data={dashboardQuery.data.allocation} currency={currency} />
              ) : (
                <EmptyState
                  icon="🧩"
                  title={t("dashboard.noAllocationTitle")}
                  description={t("dashboard.noAllocationDescription")}
                />
              )}
            </article>
            <article className="chart-card">
              <h2 className="chart-card__title">{t("dashboard.performance")}</h2>
              {performanceQuery.isPending ? (
                <TableSkeleton />
              ) : performanceQuery.isError ? (
                <ErrorBox error={performanceQuery.error} onRetry={() => performanceQuery.refetch()} />
              ) : performanceQuery.data && performanceQuery.data.points.length > 0 ? (
                <PerformanceChart points={performanceQuery.data.points} currency={currency} />
              ) : (
                <EmptyState
                  icon="📉"
                  title={t("dashboard.noPerformanceTitle")}
                  description={t("dashboard.noPerformanceDescription")}
                />
              )}
            </article>
          </section>

          <section>
            <div className="section-header">
              <h2 className="section-title">{t("dashboard.holdings")}</h2>
            </div>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>{t("table.symbol")}</th>
                    <th>{t("table.type")}</th>
                    <th className="num">{t("table.quantity")}</th>
                    <th className="num">{t("table.cost")}</th>
                    <th className="num">{t("table.price")}</th>
                    <th className="num">{t("table.marketValue")}</th>
                    <th className="num">{t("table.unrealizedPnl")}</th>
                    <th className="num">{t("table.return")}</th>
                    <th>{t("table.priceDate")}</th>
                    <th>{t("table.status")}</th>
                  </tr>
                </thead>
                <tbody>
                  {dashboardQuery.data.positions.map((position) => (
                    <tr key={position.instrumentId}>
                      <td className="sym">{position.symbol}</td>
                      <td>{position.assetType}</td>
                      <td className="num">{formatQuantity(position.quantity, locale)}</td>
                      <td className="num">{formatCurrency(position.costBasis, currency, locale)}</td>
                      <td className="num">{formatCurrency(position.closePrice, currency, locale)}</td>
                      <td className="num">{formatCurrency(position.marketValue, currency, locale)}</td>
                      <td className={`num ${pnlSign(position.unrealizedPnl)}`}>
                        {formatCurrency(position.unrealizedPnl, currency, locale)}
                      </td>
                      <td className={`num ${pnlSign(position.returnPct)}`}>{formatPercent(position.returnPct)}</td>
                      <td>{formatDate(position.priceDate, locale)}</td>
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
