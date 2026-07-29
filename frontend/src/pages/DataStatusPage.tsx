import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api } from "../api/client";
import { ErrorBox } from "../components/ErrorBox";
import { PageHeader } from "../components/PageHeader";
import { SyncStatusBadge } from "../components/StatusBadge";
import { formatDateTime } from "../lib/format";
import { useLanguage } from "../i18n/LanguageContext";

export function DataStatusPage() {
  const queryClient = useQueryClient();
  const { locale, t } = useLanguage();

  const latestSyncQuery = useQuery({
    queryKey: ["latest-sync"],
    queryFn: api.marketData.getLatestSync,
    refetchInterval: (query) =>
      query.state.data?.status === "RUNNING" ? 3_000 : 15_000,
  });

  const triggerMutation = useMutation({
    mutationFn: (force: boolean) => api.marketData.triggerSync(force),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["latest-sync"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      await queryClient.invalidateQueries({ queryKey: ["performance"] });
      await queryClient.invalidateQueries({ queryKey: ["intraday-bars"] });
    },
  });
  const syncData = latestSyncQuery.data;
  const syncIsRunning = syncData?.status === "RUNNING";
  const syncIsStuck = syncIsRunning
    && syncData != null
    && new Date().getTime() - new Date(syncData.startedAt).getTime() > 10 * 60 * 1000;
  // Show progress for both running and terminal states so the bar doesn't
  // snap back to 0% when a run fails or completes.
  const syncProgress = syncData != null && syncData.requestedCount > 0
    ? Math.round(
        ((syncData.successCount + syncData.failureCount)
          / syncData.requestedCount) * 100,
      )
    : 0;
  const syncStageLabel = syncData
    ? {
        QUEUED: t("data.stage.queued"),
        FETCHING_MARKET_DATA: t("data.stage.fetching"),
        REFRESHING_CURRENT_VALUATIONS: t("data.stage.refreshingValuations"),
        REBUILDING_HISTORICAL_VALUATIONS: t("data.stage.rebuildingHistory"),
        COMPLETED: t("data.stage.completed"),
      }[syncData.stage]
    : null;

  return (
    <>
      <PageHeader
        title={t("data.title")}
        subtitle={t("data.subtitle")}
        actions={
          <div style={{ display: "flex", gap: 8 }}>
            {syncIsStuck ? (
              <button
                type="button"
                className="btn btn-danger"
                disabled={triggerMutation.isPending}
                onClick={() => triggerMutation.mutate(true)}
                title="强制中断当前卡死的同步并重新开始"
              >
                {t("data.forceSync")}
              </button>
            ) : null}
            <button
              type="button"
              className="btn btn-primary"
              disabled={triggerMutation.isPending || syncIsRunning}
              onClick={() => triggerMutation.mutate(false)}
            >
              {triggerMutation.isPending || syncIsRunning
                ? t("data.syncing")
                : t("data.syncNow")}
            </button>
          </div>
        }
      />

      {triggerMutation.isError ? <ErrorBox error={triggerMutation.error} /> : null}
      {latestSyncQuery.isError ? <ErrorBox error={latestSyncQuery.error} onRetry={() => latestSyncQuery.refetch()} /> : null}

      <section className="card data-status-card">
        <h2 className="section-title">
          {t("data.latest")}
        </h2>
        {latestSyncQuery.isPending ? (
          <div className="info-pill">{t("common.loading")}</div>
        ) : latestSyncQuery.data ? (
          <>
          {syncData && syncData.requestedCount > 0 ? (
            <div className="sync-progress" aria-label={t("data.progress")}>
              <div className="sync-progress__meta">
                <span>{t("data.progress")}</span>
                <strong>{syncProgress}%</strong>
              </div>
              <div className="sync-progress__track">
                <span style={{ width: `${syncProgress}%` }} />
              </div>
            </div>
          ) : syncIsRunning ? (
            <div className="sync-progress" aria-label={t("data.progress")}>
              <div className="sync-progress__meta">
                <span>{t("data.progress")}</span>
                <strong>—</strong>
              </div>
              <div className="sync-progress__track">
                <span style={{ width: "0%" }} />
              </div>
            </div>
          ) : null}
          <dl className="detail-grid">
            <div><dt>{t("data.provider")}</dt><dd>{latestSyncQuery.data.provider}</dd></div>
            <div><dt>{t("table.status")}</dt><dd>
              <SyncStatusBadge status={latestSyncQuery.data.status} />
            </dd></div>
            <div><dt>{t("data.stage")}</dt><dd>{syncStageLabel}</dd></div>
            <div><dt>{t("data.requested")}</dt><dd>{latestSyncQuery.data.requestedCount}</dd></div>
            <div><dt>{t("data.successful")}</dt><dd>{latestSyncQuery.data.successCount}</dd></div>
            <div><dt>{t("data.failed")}</dt><dd>{latestSyncQuery.data.failureCount}</dd></div>
            <div><dt>{t("data.started")}</dt><dd>{formatDateTime(latestSyncQuery.data.startedAt, locale)}</dd></div>
            <div><dt>{t("data.completed")}</dt><dd>{formatDateTime(latestSyncQuery.data.completedAt, locale)}</dd></div>
            <div><dt>{t("data.triggeredBy")}</dt><dd>{latestSyncQuery.data.triggeredBy}</dd></div>
            {latestSyncQuery.data.errorSummary ? <div><dt>{t("data.errorSummary")}</dt><dd>{latestSyncQuery.data.errorSummary}</dd></div> : null}
          </dl>
          </>
        ) : (
          <p className="page-subtitle">{t("data.none")}</p>
        )}
      </section>
    </>
  );
}
