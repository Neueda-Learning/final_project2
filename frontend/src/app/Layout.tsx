import { useEffect, useMemo, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";

import { api } from "../api/client";
import { PortfolioProvider } from "./PortfolioContext";
import { SyncStatusBadge } from "../components/StatusBadge";

const NAV_ITEMS = [
  { to: "/dashboard", label: "Dashboard" },
  { to: "/portfolios", label: "Portfolios" },
  { to: "/holdings", label: "Holdings" },
  { to: "/transactions", label: "Transactions" },
  { to: "/data-status", label: "Data Status" },
];

export function Layout() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const location = useLocation();

  const portfoliosQuery = useQuery({
    queryKey: ["portfolios", { includeArchived: false }],
    queryFn: () => api.portfolios.list(false),
  });

  const syncQuery = useQuery({
    queryKey: ["latest-sync"],
    queryFn: api.marketData.getLatestSync,
    refetchInterval: 15_000,
  });

  const portfolioId = searchParams.get("portfolioId");
  const portfolios = portfoliosQuery.data?.items ?? [];

  useEffect(() => {
    if (portfolioId || portfolios.length === 0) return;
    const params = new URLSearchParams(searchParams);
    params.set("portfolioId", portfolios[0].id);
    setSearchParams(params, { replace: true });
  }, [portfolioId, portfolios, searchParams, setSearchParams]);

  const selectedPortfolio = useMemo(
    () => portfolios.find((p) => p.id === portfolioId) ?? null,
    [portfolios, portfolioId],
  );

  const setPortfolioId = (id: string) => {
    const params = new URLSearchParams(searchParams);
    params.set("portfolioId", id);
    setSearchParams(params);
  };

  const linkWithCurrentPortfolio = (path: string) => {
    const params = new URLSearchParams(searchParams);
    const search = params.toString();
    return search ? `${path}?${search}` : path;
  };

  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname, location.search]);

  return (
    <PortfolioProvider value={{ portfolioId, setPortfolioId, selectedPortfolio }}>
      <div className="app-layout">
        {mobileOpen && <div className="sidebar-overlay" onClick={() => setMobileOpen(false)} />}

        <aside className={`sidebar ${mobileOpen ? "sidebar--open" : ""}`}>
          <header className="sidebar-header">
            <button
              type="button"
              className="sidebar-logo"
              onClick={() => navigate(linkWithCurrentPortfolio("/dashboard"))}
            >
              Portfolio Manager
            </button>
            <button className="sidebar-close" type="button" onClick={() => setMobileOpen(false)}>
              x
            </button>
          </header>

          <section className="sidebar-section">
            <label className="sidebar-label" htmlFor="portfolio-select">
              Current Portfolio
            </label>
            {portfoliosQuery.isPending ? (
              <div className="sidebar-skeleton" />
            ) : (
              <select
                id="portfolio-select"
                className="sidebar-select"
                value={portfolioId ?? ""}
                onChange={(e) => setPortfolioId(e.target.value)}
              >
                {portfolios.map((portfolio) => (
                  <option key={portfolio.id} value={portfolio.id}>
                    {portfolio.name}
                  </option>
                ))}
              </select>
            )}
          </section>

          <section className="sidebar-section">
            <span className="sidebar-label">Sync Status</span>
            {syncQuery.isPending && <div className="sidebar-skeleton" />}
            {syncQuery.data ? <SyncStatusBadge status={syncQuery.data.status} /> : <span className="info-pill">No run yet</span>}
          </section>

          <nav className="sidebar-nav" aria-label="Primary">
            {NAV_ITEMS.map((item) => (
              <NavLink
                key={item.to}
                to={linkWithCurrentPortfolio(item.to)}
                className={({ isActive }) =>
                  `sidebar-link${isActive ? " sidebar-link--active" : ""}`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </aside>

        <div className="main-wrapper">
          <header className="topbar">
            <button className="topbar-menu-btn" type="button" onClick={() => setMobileOpen(true)}>
              ≡
            </button>
            <div className="topbar-title">Portfolio Manager</div>
          </header>

          <main className="main-content">
            <Outlet />
          </main>
        </div>
      </div>
    </PortfolioProvider>
  );
}
