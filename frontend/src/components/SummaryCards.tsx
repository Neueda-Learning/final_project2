import { formatCurrency, formatPercent, pnlSign } from "../lib/format";
import type { PortfolioSummary } from "../api/types";

export function SummaryCards({
  summary,
  currency,
}: {
  summary: PortfolioSummary;
  currency: string;
}) {
  return (
    <section className="summary-grid" aria-label="组合摘要">
      <article className="summary-card">
        <div className="summary-card__label">总市值</div>
        <div className="summary-card__value">{formatCurrency(summary.pricedMarketValue, currency)}</div>
      </article>
      <article className="summary-card">
        <div className="summary-card__label">总成本</div>
        <div className="summary-card__value">{formatCurrency(summary.totalCostBasis, currency)}</div>
      </article>
      <article className="summary-card">
        <div className="summary-card__label">未实现盈亏</div>
        <div className={`summary-card__value ${pnlSign(summary.unrealizedPnl)}`}>
          {formatCurrency(summary.unrealizedPnl, currency)}
        </div>
      </article>
      <article className="summary-card">
        <div className="summary-card__label">收益率</div>
        <div className={`summary-card__value ${pnlSign(summary.returnPct)}`}>
          {formatPercent(summary.returnPct)}
        </div>
      </article>
    </section>
  );
}
