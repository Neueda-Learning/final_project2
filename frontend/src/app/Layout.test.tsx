import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useSearchParams } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import { api } from "../api/client";
import type { Portfolio } from "../api/types";
import { LanguageProvider } from "../i18n/LanguageContext";
import { Layout } from "./Layout";

function SearchParamProbe() {
  const [searchParams] = useSearchParams();
  return <div data-testid="portfolio-id">{searchParams.get("portfolioId") ?? ""}</div>;
}

function renderLayout(initialEntry: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <LanguageProvider>
        <MemoryRouter initialEntries={[initialEntry]}>
          <Routes>
            <Route element={<Layout />}>
              <Route path="/holdings" element={<SearchParamProbe />} />
            </Route>
          </Routes>
        </MemoryRouter>
      </LanguageProvider>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("Layout portfolio selection guards", () => {
  it("clears stale portfolioId when no active portfolios remain", async () => {
    vi.spyOn(api.portfolios, "list").mockResolvedValue({
      items: [],
      page: 1,
      pageSize: 20,
      total: 0,
    });
    vi.spyOn(api.marketData, "getLatestSync").mockResolvedValue(null);

    renderLayout("/holdings?portfolioId=old-portfolio");

    await waitFor(() => {
      expect(screen.getByTestId("portfolio-id").textContent).toBe("");
    });
  });

  it("replaces an invalid portfolioId with the first active portfolio", async () => {
    const portfolio: Portfolio = {
      id: "portfolio-1",
      name: "Main",
      description: null,
      baseCurrency: "USD",
      isArchived: false,
      createdAt: "2026-07-30T08:00:00",
      updatedAt: "2026-07-30T08:00:00",
    };

    vi.spyOn(api.portfolios, "list").mockResolvedValue({
      items: [portfolio],
      page: 1,
      pageSize: 20,
      total: 1,
    });
    vi.spyOn(api.marketData, "getLatestSync").mockResolvedValue(null);

    renderLayout("/holdings?portfolioId=deleted-id");

    await waitFor(() => {
      expect(screen.getByTestId("portfolio-id").textContent).toBe(portfolio.id);
    });
  });

  it("assigns the first active portfolio when portfolioId is missing", async () => {
    const first: Portfolio = {
      id: "portfolio-1",
      name: "Main",
      description: null,
      baseCurrency: "USD",
      isArchived: false,
      createdAt: "2026-07-30T08:00:00",
      updatedAt: "2026-07-30T08:00:00",
    };
    const second: Portfolio = {
      id: "portfolio-2",
      name: "Satellite",
      description: null,
      baseCurrency: "USD",
      isArchived: false,
      createdAt: "2026-07-30T08:05:00",
      updatedAt: "2026-07-30T08:05:00",
    };

    vi.spyOn(api.portfolios, "list").mockResolvedValue({
      items: [first, second],
      page: 1,
      pageSize: 20,
      total: 2,
    });
    vi.spyOn(api.marketData, "getLatestSync").mockResolvedValue(null);

    renderLayout("/holdings");

    await waitFor(() => {
      expect(screen.getByTestId("portfolio-id").textContent).toBe(first.id);
    });
  });

  it("keeps a valid portfolioId unchanged", async () => {
    const first: Portfolio = {
      id: "portfolio-1",
      name: "Main",
      description: null,
      baseCurrency: "USD",
      isArchived: false,
      createdAt: "2026-07-30T08:00:00",
      updatedAt: "2026-07-30T08:00:00",
    };
    const second: Portfolio = {
      id: "portfolio-2",
      name: "Satellite",
      description: null,
      baseCurrency: "USD",
      isArchived: false,
      createdAt: "2026-07-30T08:05:00",
      updatedAt: "2026-07-30T08:05:00",
    };

    vi.spyOn(api.portfolios, "list").mockResolvedValue({
      items: [first, second],
      page: 1,
      pageSize: 20,
      total: 2,
    });
    vi.spyOn(api.marketData, "getLatestSync").mockResolvedValue(null);

    renderLayout("/holdings?portfolioId=portfolio-2");

    await waitFor(() => {
      expect(screen.getByTestId("portfolio-id").textContent).toBe(second.id);
    });
  });

  it("does not clear portfolioId when the portfolio list request fails", async () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    vi.spyOn(api.portfolios, "list").mockRejectedValue(new Error("request failed"));
    vi.spyOn(api.marketData, "getLatestSync").mockResolvedValue(null);

    renderLayout("/holdings?portfolioId=old-portfolio");

    await waitFor(() => {
      expect(api.portfolios.list).toHaveBeenCalled();
    });
    expect(screen.getByTestId("portfolio-id").textContent).toBe("old-portfolio");
    consoleError.mockRestore();
  });
});
