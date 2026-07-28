import type { PriceStatus, SyncStatus } from "../api/types";

export function PriceStatusBadge({ status }: { status: PriceStatus }) {
  const cls =
    status === "FRESH"
      ? "badge badge-fresh"
      : status === "STALE"
        ? "badge badge-stale"
        : "badge badge-unavailable";
  return <span className={cls}>{status}</span>;
}

export function SyncStatusBadge({ status }: { status: SyncStatus }) {
  const cls =
    status === "RUNNING"
      ? "badge badge-running"
      : status === "SUCCEEDED"
        ? "badge badge-succeeded"
        : status === "PARTIAL"
          ? "badge badge-partial"
          : "badge badge-failed";
  return <span className={cls}>{status}</span>;
}
