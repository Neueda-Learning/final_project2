import { formatCurrency, formatPercent, pnlSign } from "../lib/format";
import type { PortfolioSummary } from "../api/types";
import { useLanguage } from "../i18n/LanguageContext";

export function SummaryCards({
  summary,
  currency,
}: {
  summary: PortfolioSummary;
  currency: string;
}) {
  const { locale, t } = useLanguage();
  return (
    <section className="summary-grid" aria-label={t("summary.label")}>
      <article className="summary-card">
        <div className="summary-card__label">{t("summary.marketValue")}</div>
        <div className="summary-card__value">{formatCurrency(summary.pricedMarketValue, currency, locale)}</div>
      </article>
      <article className="summary-card">
        <div className="summary-card__label">{t("summary.costBasis")}</div>
        <div className="summary-card__value">{formatCurrency(summary.totalCostBasis, currency, locale)}</div>
      </article>
      <article className="summary-card">
        <div className="summary-card__label">{t("summary.unrealizedPnl")}</div>
        <div className={`summary-card__value ${pnlSign(summary.unrealizedPnl)}`}>
          {formatCurrency(summary.unrealizedPnl, currency, locale)}
        </div>
      </article>
      <article className="summary-card">
        <div className="summary-card__label">{t("summary.return")}</div>
        <div className={`summary-card__value ${pnlSign(summary.returnPct)}`}>
          {formatPercent(summary.returnPct)}
        </div>
      </article>
    </section>
  );
}
