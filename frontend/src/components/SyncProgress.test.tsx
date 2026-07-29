import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { SyncRun } from "../api/types";
import { LanguageProvider } from "../i18n/LanguageContext";
import { SyncProgress } from "./SyncProgress";

const runningSync: SyncRun = {
  id: "sync-1",
  provider: "twelve-data",
  status: "RUNNING",
  stage: "FETCHING_MARKET_DATA",
  requestedCount: 5,
  successCount: 2,
  failureCount: 0,
  startedAt: "2026-07-29T10:00:00",
  completedAt: null,
  triggeredBy: "MANUAL",
  errorSummary: null,
};

describe("SyncProgress", () => {
  it("reports processed instruments as determinate progress", () => {
    render(
      <LanguageProvider>
        <SyncProgress sync={runningSync} />
      </LanguageProvider>,
    );

    expect(screen.getByText("Fetching market data")).toBeInTheDocument();
    expect(screen.getByText("2 / 5 · 40%")).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "40");
  });

  it("uses indeterminate progress while valuations are rebuilt", () => {
    render(
      <LanguageProvider>
        <SyncProgress
          sync={{
            ...runningSync,
            stage: "REBUILDING_HISTORICAL_VALUATIONS",
            successCount: 5,
          }}
        />
      </LanguageProvider>,
    );

    expect(screen.getByText("Rebuilding valuation history")).toBeInTheDocument();
    expect(screen.getByText("In progress")).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).not.toHaveAttribute("aria-valuenow");
  });
});
