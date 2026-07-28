import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api } from "../api/client";
import { ErrorBox } from "../components/ErrorBox";
import { PageHeader } from "../components/PageHeader";
import { SyncStatusBadge } from "../components/StatusBadge";
import { formatDateTime } from "../lib/format";

export function DataStatusPage() {
  const queryClient = useQueryClient();

  const latestSyncQuery = useQuery({
    queryKey: ["latest-sync"],
    queryFn: api.marketData.getLatestSync,
    refetchInterval: 15_000,
  });

  const triggerMutation = useMutation({
    mutationFn: (force: boolean) => api.marketData.triggerSync(force),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["latest-sync"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      await queryClient.invalidateQueries({ queryKey: ["performance"] });
    },
  });

  return (
    <>
      <PageHeader
        title="Data Status"
        subtitle="查看最近同步任务并手动触发行情更新"
        actions={
          <button
            type="button"
            className="btn btn-primary"
            disabled={triggerMutation.isPending}
            onClick={() => triggerMutation.mutate(false)}
          >
            {triggerMutation.isPending ? "触发中..." : "手动同步"}
          </button>
        }
      />

      {triggerMutation.isError ? <ErrorBox error={triggerMutation.error} /> : null}
      {latestSyncQuery.isError ? <ErrorBox error={latestSyncQuery.error} onRetry={() => latestSyncQuery.refetch()} /> : null}

      <section className="card">
        <h2 className="section-title" style={{ marginBottom: "0.75rem" }}>
          最近同步任务
        </h2>
        {latestSyncQuery.isPending ? (
          <div className="info-pill">加载中...</div>
        ) : latestSyncQuery.data ? (
          <div style={{ display: "grid", gap: "0.75rem" }}>
            <div>
              <span className="info-pill">provider: {latestSyncQuery.data.provider}</span>
            </div>
            <div>
              <SyncStatusBadge status={latestSyncQuery.data.status} />
            </div>
            <div>requestedCount: {latestSyncQuery.data.requestedCount}</div>
            <div>successCount: {latestSyncQuery.data.successCount}</div>
            <div>failureCount: {latestSyncQuery.data.failureCount}</div>
            <div>startedAt: {formatDateTime(latestSyncQuery.data.startedAt)}</div>
            <div>completedAt: {formatDateTime(latestSyncQuery.data.completedAt)}</div>
            <div>triggeredBy: {latestSyncQuery.data.triggeredBy}</div>
            {latestSyncQuery.data.errorSummary ? <div>errorSummary: {latestSyncQuery.data.errorSummary}</div> : null}
          </div>
        ) : (
          <p className="page-subtitle">尚无同步记录。</p>
        )}
      </section>
    </>
  );
}
