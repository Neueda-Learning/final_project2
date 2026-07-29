import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { api } from "../api/client";
import type { DashboardResponse, Portfolio, SyncRun } from "../api/types";
import { PortfolioProvider } from "../app/PortfolioContext";
import { LanguageProvider } from "../i18n/LanguageContext";
import { DashboardPage } from "./DashboardPage";

const portfolio: Portfolio = {
  id: "portfolio-1",
  name: "Demo Portfolio",
  description: null,
  baseCurrency: "USD",
  isArchived: false,
  createdAt: "2026-07-29T09:00:00",
  updatedAt: "2026-07-29T09:00:00",
};

const dashboard: DashboardResponse = {
  portfolio: {
    id: portfolio.id,
    name: portfolio.name,
    baseCurrency: portfolio.baseCurrency,
  },
  summary: {
    positionCount: 0,
    pricedPositionCount: 0,
    unpricedPositionCount: 0,
    pricedMarketValue: "0",
    totalCostBasis: "0",
    pricedCostBasis: "0",
    unrealizedPnl: "0",
    returnPct: null,
    newestPriceDate: null,
    oldestUsedPriceDate: null,
  },
  positions: [],
  allocation: [],
};

const runningSync: SyncRun = {
  id: "sync-1",
  provider: "twelve-data",
  status: "RUNNING",
  stage: "FETCHING_MARKET_DATA",
  requestedCount: 5,
  successCount: 1,
  failureCount: 1,
  startedAt: "2026-07-29T10:00:00",
  completedAt: null,
  triggeredBy: "MANUAL",
  errorSummary: null,
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe("DashboardPage market-data sync", () => {
  it("starts a sync and shows its live progress without leaving the dashboard", async () => {
    vi.spyOn(api.analytics, "getDashboard").mockResolvedValue(dashboard);
    vi.spyOn(api.analytics, "getPerformance").mockResolvedValue({
      portfolioId: portfolio.id,
      baseCurrency: "USD",
      points: [],
    });
    vi.spyOn(api.marketData, "getLatestSync")
      .mockResolvedValueOnce(null)
      .mockResolvedValue(runningSync);
    const triggerSync = vi.spyOn(api.marketData, "triggerSync")
      .mockResolvedValue(runningSync);

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <LanguageProvider>
          <PortfolioProvider
            value={{
              portfolioId: portfolio.id,
              selectedPortfolio: portfolio,
              setPortfolioId: vi.fn(),
            }}
          >
            <DashboardPage />
          </PortfolioProvider>
        </LanguageProvider>
      </QueryClientProvider>,
    );

    await waitFor(() => expect(api.marketData.getLatestSync).toHaveBeenCalled());
    fireEvent.click(screen.getByRole("button", { name: "Sync now" }));

    expect(await screen.findByText("Twelve Data synchronization")).toBeInTheDocument();
    expect(triggerSync).toHaveBeenCalledWith(false);
    expect(screen.getByText("2 / 5 · 40%")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Syncing..." })).toBeDisabled();
  });

  it("refreshes portfolio analytics when a running sync completes", async () => {
    const getDashboard = vi.spyOn(api.analytics, "getDashboard")
      .mockResolvedValue(dashboard);
    const getPerformance = vi.spyOn(api.analytics, "getPerformance")
      .mockResolvedValue({
        portfolioId: portfolio.id,
        baseCurrency: "USD",
        points: [],
      });
    vi.spyOn(api.marketData, "getLatestSync").mockResolvedValue(runningSync);

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <LanguageProvider>
          <PortfolioProvider
            value={{
              portfolioId: portfolio.id,
              selectedPortfolio: portfolio,
              setPortfolioId: vi.fn(),
            }}
          >
            <DashboardPage />
          </PortfolioProvider>
        </LanguageProvider>
      </QueryClientProvider>,
    );

    expect(await screen.findByText("Twelve Data synchronization")).toBeInTheDocument();
    await waitFor(() => {
      expect(getDashboard).toHaveBeenCalledTimes(1);
      expect(getPerformance).toHaveBeenCalledTimes(1);
    });

    act(() => {
      queryClient.setQueryData(["latest-sync"], {
        ...runningSync,
        status: "SUCCEEDED",
        stage: "COMPLETED",
        successCount: 4,
        completedAt: "2026-07-29T10:01:00",
      } satisfies SyncRun);
    });

    await waitFor(() => {
      expect(getDashboard).toHaveBeenCalledTimes(2);
      expect(getPerformance).toHaveBeenCalledTimes(2);
    });
    expect(screen.queryByText("Twelve Data synchronization")).not.toBeInTheDocument();
  });
});
