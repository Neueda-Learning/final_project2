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

  it("caps processed progress at the requested count", () => {
    render(
      <LanguageProvider>
        <SyncProgress
          sync={{
            ...runningSync,
            successCount: 4,
            failureCount: 3,
          }}
        />
      </LanguageProvider>,
    );

    expect(screen.getByText("5 / 5 · 100%")).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "100");
  });

  it("uses indeterminate progress when no instruments were requested", () => {
    render(
      <LanguageProvider>
        <SyncProgress
          sync={{
            ...runningSync,
            requestedCount: 0,
            successCount: 0,
            failureCount: 0,
          }}
        />
      </LanguageProvider>,
    );

    expect(screen.getByText("In progress")).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).not.toHaveAttribute("aria-valuenow");
  });

  it("renders queued stage text", () => {
    render(
      <LanguageProvider>
        <SyncProgress sync={{ ...runningSync, stage: "QUEUED" }} />
      </LanguageProvider>,
    );

    expect(screen.getByText("Queued")).toBeInTheDocument();
  });

  it("renders refreshing current valuations stage text", () => {
    render(
      <LanguageProvider>
        <SyncProgress sync={{ ...runningSync, stage: "REFRESHING_CURRENT_VALUATIONS" }} />
      </LanguageProvider>,
    );

    expect(screen.getByText("Refreshing current valuations")).toBeInTheDocument();
  });

  it("renders completed stage text", () => {
    render(
      <LanguageProvider>
        <SyncProgress sync={{ ...runningSync, stage: "COMPLETED" }} />
      </LanguageProvider>,
    );

    expect(screen.getByText("Completed")).toBeInTheDocument();
  });

  it("adds the compact modifier class when requested", () => {
    const { container } = render(
      <LanguageProvider>
        <SyncProgress sync={runningSync} compact />
      </LanguageProvider>,
    );

    expect(container.firstChild).toHaveClass("sync-progress--compact");
  });

  it("adds the indeterminate modifier class when the stage is not determinate", () => {
    const { container } = render(
      <LanguageProvider>
        <SyncProgress sync={{ ...runningSync, stage: "QUEUED" }} />
      </LanguageProvider>,
    );

    expect(container.firstChild).toHaveClass("sync-progress--indeterminate");
  });

  it("omits the indeterminate modifier when market data progress is determinate", () => {
    const { container } = render(
      <LanguageProvider>
        <SyncProgress sync={runningSync} />
      </LanguageProvider>,
    );

    expect(container.firstChild).not.toHaveClass("sync-progress--indeterminate");
  });

  it("describes progress through aria-valuetext", () => {
    render(
      <LanguageProvider>
        <SyncProgress sync={runningSync} />
      </LanguageProvider>,
    );

    expect(screen.getByRole("progressbar")).toHaveAttribute(
      "aria-valuetext",
      "Fetching market data. 2 / 5 · 40%",
    );
  });

  it("renders failure counts as part of processed totals", () => {
    render(
      <LanguageProvider>
        <SyncProgress
          sync={{
            ...runningSync,
            successCount: 1,
            failureCount: 2,
          }}
        />
      </LanguageProvider>,
    );

    expect(screen.getByText("3 / 5 · 60%")).toBeInTheDocument();
  });
});
