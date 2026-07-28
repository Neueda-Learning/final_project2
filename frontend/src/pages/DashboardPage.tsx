import { useEffect, useMemo, useState } from "react";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";

import { api } from "../api/client";
import { usePortfolio } from "../app/PortfolioContext";
import { EmptyState } from "../components/EmptyState";
import { ErrorBox } from "../components/ErrorBox";
import { PageHeader } from "../components/PageHeader";
import { SummarySkeleton, TableSkeleton } from "../components/Skeletons";
import { SummaryCards } from "../components/SummaryCards";
import { AllocationChart, IntradayChart, PerformanceChart } from "../components/charts";
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
  const [intradayInstrumentId, setIntradayInstrumentId] = useState("");
  const [intradayDays, setIntradayDays] = useState(1);
  const [intradayAnchor, setIntradayAnchor] = useState(
    () => new Date().toISOString().slice(0, 19),
  );

  useEffect(() => {
    const firstId = dashboardQuery.data?.positions[0]?.instrumentId ?? "";
    setIntradayInstrumentId((current) =>
      dashboardQuery.data?.positions.some((position) => position.instrumentId === current)
        ? current
        : firstId,
    );
  }, [dashboardQuery.data?.positions]);

  const intradayFrom = useMemo(() => {
    const value = new Date(`${intradayAnchor}Z`);
    value.setUTCDate(value.getUTCDate() - intradayDays);
    return value.toISOString().slice(0, 19);
  }, [intradayAnchor, intradayDays]);

  const intradayQuery = useInfiniteQuery({
    queryKey: [
      "market-bars",
      intradayInstrumentId,
      intradayDays,
      intradayAnchor,
    ],
    queryFn: ({ pageParam }) =>
      api.marketData.getBars(intradayInstrumentId, {
        interval: "1min",
        from: intradayFrom,
        to: intradayAnchor,
        page: pageParam,
        pageSize: 300,
      }),
    initialPageParam: 1,
    getNextPageParam: (lastPage) =>
      lastPage.hasNext ? lastPage.page + 1 : undefined,
    enabled: Boolean(intradayInstrumentId),
    staleTime: 20_000,
    refetchInterval: 60_000,
  });

  const intradayBars = useMemo(
    () =>
      (intradayQuery.data?.pages.flatMap((page) => page.items) ?? [])
        .slice()
        .sort((left, right) => left.timestamp.localeCompare(right.timestamp)),
    [intradayQuery.data],
  );
  const selectedIntradayPosition = dashboardQuery.data?.positions.find(
    (position) => position.instrumentId === intradayInstrumentId,
  );
  const latestIntradayBar = intradayBars.at(-1);

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

          <section className="card intraday-card" aria-label={t("intraday.title")}>
            <div className="intraday-card__header">
              <div>
                <div className="intraday-card__eyebrow">{t("intraday.eyebrow")}</div>
                <h2 className="section-title">{t("intraday.title")}</h2>
                <p className="page-subtitle">{t("intraday.subtitle")}</p>
              </div>
              <div className="intraday-controls">
                <label>
                  <span className="sr-only">{t("intraday.instrument")}</span>
                  <select
                    value={intradayInstrumentId}
                    onChange={(event) => {
                      setIntradayInstrumentId(event.target.value);
                      setIntradayAnchor(new Date().toISOString().slice(0, 19));
                    }}
                  >
                    {dashboardQuery.data.positions.map((position) => (
                      <option key={position.instrumentId} value={position.instrumentId}>
                        {position.symbol}
                      </option>
                    ))}
                  </select>
                </label>
                <div className="segmented-control" aria-label={t("intraday.range")}>
                  {[1, 3, 5].map((days) => (
                    <button
                      key={days}
                      type="button"
                      className={days === intradayDays ? "is-active" : ""}
                      onClick={() => {
                        setIntradayDays(days);
                        setIntradayAnchor(new Date().toISOString().slice(0, 19));
                      }}
                    >
                      {days}{t("intraday.daySuffix")}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {latestIntradayBar ? (
              <div className="intraday-quote">
                <strong>
                  {selectedIntradayPosition?.symbol}{" "}
                  {formatCurrency(latestIntradayBar.close, currency, locale)}
                </strong>
                <span>
                  {t("intraday.updated", {
                    time: new Date(`${latestIntradayBar.timestamp}Z`)
                      .toLocaleString(locale),
                  })}
                </span>
              </div>
            ) : null}

            {intradayQuery.isPending ? (
              <TableSkeleton />
            ) : intradayQuery.isError ? (
              <ErrorBox error={intradayQuery.error} onRetry={() => intradayQuery.refetch()} />
            ) : intradayBars.length > 0 ? (
              <>
                <IntradayChart bars={intradayBars} currency={currency} />
                <div className="intraday-card__footer">
                  <span>{t("intraday.points", { count: intradayBars.length })}</span>
                  {intradayQuery.hasNextPage ? (
                    <button
                      type="button"
                      className="btn btn-secondary"
                      disabled={intradayQuery.isFetchingNextPage}
                      onClick={() => intradayQuery.fetchNextPage()}
                    >
                      {intradayQuery.isFetchingNextPage
                        ? t("common.loading")
                        : t("intraday.loadOlder")}
                    </button>
                  ) : null}
                </div>
              </>
            ) : (
              <EmptyState
                icon="〽"
                title={t("intraday.emptyTitle")}
                description={t("intraday.emptyDescription")}
              />
            )}
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
