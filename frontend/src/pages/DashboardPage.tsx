import { useEffect, useMemo, useRef } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api } from "../api/client";
import { usePortfolio } from "../app/PortfolioContext";
import { EmptyState } from "../components/EmptyState";
import { ErrorBox } from "../components/ErrorBox";
import { PageHeader } from "../components/PageHeader";
import { SummarySkeleton, TableSkeleton } from "../components/Skeletons";
import { SummaryCards } from "../components/SummaryCards";
import { AllocationChart, PerformanceChart } from "../components/charts";
import { PriceStatusBadge, SyncStatusBadge } from "../components/StatusBadge";
import { SyncProgress } from "../components/SyncProgress";
import { formatCurrency, formatDate, formatPercent, formatQuantity, pnlSign } from "../lib/format";
import { useLanguage } from "../i18n/LanguageContext";

export function DashboardPage() {
  const { portfolioId, selectedPortfolio } = usePortfolio();
  const { locale, t } = useLanguage();
  const queryClient = useQueryClient();
  const runningRunId = useRef<string | null>(null);

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

  const latestSyncQuery = useQuery({
    queryKey: ["latest-sync"],
    queryFn: api.marketData.getLatestSync,
    refetchInterval: (query) =>
      query.state.data?.status === "RUNNING" ? 3_000 : 15_000,
  });

  const triggerSyncMutation = useMutation({
    mutationFn: () => api.marketData.triggerSync(false),
    onSuccess: async (syncRun) => {
      queryClient.setQueryData(["latest-sync"], syncRun);
      await queryClient.invalidateQueries({ queryKey: ["latest-sync"] });
    },
  });

  useEffect(() => {
    const syncRun = latestSyncQuery.data;
    if (!syncRun) return;

    if (syncRun.status === "RUNNING") {
      runningRunId.current = syncRun.id;
      return;
    }

    if (runningRunId.current === syncRun.id) {
      runningRunId.current = null;
      void Promise.all([
        queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
        queryClient.invalidateQueries({ queryKey: ["performance"] }),
      ]);
    }
  }, [latestSyncQuery.data, queryClient]);

  const currency = selectedPortfolio?.baseCurrency ?? "USD";
  const chartPoints = useMemo(() => {
    const points = performanceQuery.data?.points ?? [];
    if (!dashboardQuery.data?.summary.newestPriceDate) return points;

    const latestDate = dashboardQuery.data.summary.newestPriceDate;
    const next = [...points];
    const mergedPoint = {
      valuationDate: latestDate,
      pricedMarketValue: dashboardQuery.data.summary.pricedMarketValue,
      totalCostBasis: dashboardQuery.data.summary.totalCostBasis,
      pricedCostBasis: dashboardQuery.data.summary.pricedCostBasis,
      unrealizedPnl: dashboardQuery.data.summary.unrealizedPnl,
      returnPct: dashboardQuery.data.summary.returnPct,
      pricedPositionCount: dashboardQuery.data.summary.pricedPositionCount,
      unpricedPositionCount: dashboardQuery.data.summary.unpricedPositionCount,
    };

    const existingIndex = next.findIndex((p) => p.valuationDate === latestDate);
    if (existingIndex >= 0) {
      next[existingIndex] = {
        ...next[existingIndex],
        ...mergedPoint,
      };
    } else {
      next.push(mergedPoint);
    }

    next.sort((a, b) => a.valuationDate.localeCompare(b.valuationDate));
    return next;
  }, [dashboardQuery.data, performanceQuery.data]);
  const syncRun = latestSyncQuery.data;
  const syncIsRunning = syncRun?.status === "RUNNING";
  const syncProviderName =
    syncRun?.provider === "twelve-data" ? "Twelve Data" : syncRun?.provider;

  return (
    <>
      <PageHeader
        title={t("dashboard.title")}
        subtitle={
          selectedPortfolio
            ? t("dashboard.subtitle", { name: selectedPortfolio.name })
            : t("dashboard.noSelectionSubtitle")
        }
        actions={
          <button
            type="button"
            className="btn btn-primary"
            disabled={triggerSyncMutation.isPending || syncIsRunning}
            onClick={() => triggerSyncMutation.mutate()}
          >
            {triggerSyncMutation.isPending
              ? t("data.syncing")
              : syncIsRunning
                ? t("data.syncRunning")
                : t("data.syncNow")}
          </button>
        }
      />

      {triggerSyncMutation.isError ? <ErrorBox error={triggerSyncMutation.error} /> : null}
      {latestSyncQuery.isError && !latestSyncQuery.data ? (
        <ErrorBox error={latestSyncQuery.error} onRetry={() => latestSyncQuery.refetch()} />
      ) : null}

      {syncIsRunning && syncRun ? (
        <section className="live-sync-banner" aria-live="polite">
          <div className="live-sync-banner__header">
            <div>
              <span className="live-sync-banner__eyebrow">{t("dashboard.liveSync")}</span>
              <h2>{t("dashboard.liveSyncTitle", { provider: syncProviderName ?? "" })}</h2>
            </div>
            <SyncStatusBadge status={syncRun.status} />
          </div>
          <SyncProgress sync={syncRun} compact />
          <div className="live-sync-banner__footer">
            <span>{t("dashboard.liveSyncDescription")}</span>
            <strong>
              {t("data.countSummary", {
                success: syncRun.successCount,
                failure: syncRun.failureCount,
              })}
            </strong>
          </div>
        </section>
      ) : null}

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
              ) : chartPoints.length > 0 ? (
                <PerformanceChart points={chartPoints} />
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
