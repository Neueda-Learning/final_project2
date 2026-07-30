import { useEffect, useMemo } from "react";
import { NavLink, Outlet, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";

import { api } from "../api/client";
import { SyncStatusBadge } from "../components/StatusBadge";
import { useLanguage } from "../i18n/LanguageContext";
import { PortfolioProvider } from "./PortfolioContext";

export function Layout() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { language, t, toggleLanguage } = useLanguage();

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
  const hasSelectedPortfolio =
    portfolioId !== null && portfolios.some((portfolio) => portfolio.id === portfolioId);

  useEffect(() => {
    if (portfoliosQuery.isPending || portfoliosQuery.isError) return;

    const params = new URLSearchParams(searchParams);

    if (portfolios.length === 0) {
      if (!portfolioId) return;
      params.delete("portfolioId");
      setSearchParams(params, { replace: true });
      return;
    }

    if (hasSelectedPortfolio) return;

    params.set("portfolioId", portfolios[0].id);
    setSearchParams(params, { replace: true });
  }, [
    hasSelectedPortfolio,
    portfolioId,
    portfolios,
    portfoliosQuery.isError,
    portfoliosQuery.isPending,
    searchParams,
    setSearchParams,
  ]);

  const selectedPortfolio = useMemo(
    () => portfolios.find((portfolio) => portfolio.id === portfolioId) ?? null,
    [portfolios, portfolioId],
  );

  const setPortfolioId = (id: string) => {
    const params = new URLSearchParams(searchParams);
    if (id) params.set("portfolioId", id);
    else params.delete("portfolioId");
    setSearchParams(params);
  };

  const linkWithCurrentPortfolio = (path: string) => {
    const search = searchParams.toString();
    return search ? `${path}?${search}` : path;
  };

  const navItems = [
    { to: "/dashboard", label: t("nav.dashboard") },
    { to: "/portfolios", label: t("nav.portfolios") },
    { to: "/holdings", label: t("nav.holdings") },
    { to: "/transactions", label: t("nav.transactions") },
    { to: "/data-status", label: t("nav.dataStatus") },
  ];

  return (
    <PortfolioProvider value={{ portfolioId, setPortfolioId, selectedPortfolio }}>
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      <div className="site-shell">
        <header className="site-header">
          <NavLink className="brand" to={linkWithCurrentPortfolio("/dashboard")}>
            <span className="brand-mark" aria-hidden="true">C</span>
            <span>
              <strong>{t("app.name")}</strong>
              <small>{t("app.tagline")}</small>
            </span>
          </NavLink>

          <nav className="primary-nav" aria-label={t("nav.primary")}>
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={linkWithCurrentPortfolio(item.to)}
                className={({ isActive }) => `nav-link${isActive ? " nav-link--active" : ""}`}
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="header-actions">
            <label className="compact-field">
              <span>{t("layout.portfolio")}</span>
              <select
                value={portfolioId ?? ""}
                onChange={(event) => setPortfolioId(event.target.value)}
                disabled={portfoliosQuery.isPending}
                aria-label={t("layout.portfolio")}
              >
                {portfolios.length === 0 ? (
                  <option value="">{t("layout.noPortfolio")}</option>
                ) : null}
                {portfolios.map((portfolio) => (
                  <option key={portfolio.id} value={portfolio.id}>
                    {portfolio.name}
                  </option>
                ))}
              </select>
            </label>

            <div className="sync-indicator" aria-label={t("layout.sync")}>
              <span>{t("layout.sync")}</span>
              {syncQuery.data ? (
                <SyncStatusBadge status={syncQuery.data.status} />
              ) : (
                <span className="status-dot-label">{t("layout.noRun")}</span>
              )}
            </div>

            <button
              className="language-toggle"
              type="button"
              onClick={toggleLanguage}
              aria-label={language === "en" ? t("layout.switchToChinese") : t("layout.switchToEnglish")}
            >
              <span className={language === "en" ? "is-active" : ""}>EN</span>
              <span aria-hidden="true">/</span>
              <span className={language === "zh" ? "is-active" : ""}>{t("layout.chinese")}</span>
            </button>
          </div>
        </header>

        <main className="main-content" id="main-content">
          <Outlet />
        </main>
      </div>
    </PortfolioProvider>
  );
}
