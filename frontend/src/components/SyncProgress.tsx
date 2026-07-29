import type { SyncRun, SyncStage } from "../api/types";
import { useLanguage } from "../i18n/LanguageContext";

function useStageLabel(stage: SyncStage) {
  const { t } = useLanguage();

  return {
    QUEUED: t("data.stage.queued"),
    FETCHING_MARKET_DATA: t("data.stage.fetching"),
    REFRESHING_CURRENT_VALUATIONS: t("data.stage.refreshingValuations"),
    REBUILDING_HISTORICAL_VALUATIONS: t("data.stage.rebuildingHistory"),
    COMPLETED: t("data.stage.completed"),
  }[stage];
}

export function SyncStageText({ stage }: { stage: SyncStage }) {
  return <>{useStageLabel(stage)}</>;
}

export function SyncProgress({
  sync,
  compact = false,
}: {
  sync: SyncRun;
  compact?: boolean;
}) {
  const { t } = useLanguage();
  const stageLabel = useStageLabel(sync.stage);
  const processedCount = Math.min(
    sync.requestedCount,
    sync.successCount + sync.failureCount,
  );
  const isDeterminate =
    sync.stage === "FETCHING_MARKET_DATA" && sync.requestedCount > 0;
  const percentage = isDeterminate
    ? Math.round((processedCount / sync.requestedCount) * 100)
    : null;
  const progressText = isDeterminate
    ? t("data.processed", {
        processed: processedCount,
        total: sync.requestedCount,
        percentage: percentage ?? 0,
      })
    : t("data.inProgress");

  return (
    <div
      className={[
        "sync-progress",
        compact ? "sync-progress--compact" : "",
        isDeterminate ? "" : "sync-progress--indeterminate",
      ].filter(Boolean).join(" ")}
      aria-label={t("data.progress")}
    >
      <div className="sync-progress__meta">
        <span>{stageLabel}</span>
        <strong>{progressText}</strong>
      </div>
      <div
        className="sync-progress__track"
        role="progressbar"
        aria-valuemin={isDeterminate ? 0 : undefined}
        aria-valuemax={isDeterminate ? 100 : undefined}
        aria-valuenow={percentage ?? undefined}
        aria-valuetext={`${stageLabel}. ${progressText}`}
      >
        <span style={isDeterminate ? { width: `${percentage}%` } : undefined} />
      </div>
    </div>
  );
}
