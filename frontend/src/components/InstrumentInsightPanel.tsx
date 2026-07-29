import type { Instrument, MarketBar, MarketPrice } from "../api/types";
import { EmptyState } from "./EmptyState";
import { ErrorBox } from "./ErrorBox";
import { IntradayChart } from "./charts";
import { formatCurrency, formatDate } from "../lib/format";
import { useLanguage } from "../i18n/LanguageContext";

interface Props {
  instrument: Instrument | null;
  priceData: MarketPrice[] | null;
  pricesLoading: boolean;
  barsData: MarketBar[] | null;
  barsLoading: boolean;
  barsError: unknown;
  barsUpdatedAt: number | null;
  onRefreshBars: () => void;
  currency: string;
}

export function InstrumentInsightPanel({
  instrument,
  priceData,
  pricesLoading,
  barsData,
  barsLoading,
  barsError,
  barsUpdatedAt,
  onRefreshBars,
  currency,
}: Props) {
  const { locale, t } = useLanguage();

  if (!instrument) {
    return (
      <div className="card insight-panel insight-panel--empty">
        <EmptyState
          icon="📊"
          title={t("insight.emptyTitle")}
          description={t("insight.emptyDescription")}
        />
      </div>
    );
  }

  // ── Derive price statistics ───────────────────────────────────────
  // priceData is sorted date DESC (most recent first)
  const prices = priceData ?? [];
  const latestPrice = prices[0] ?? null;
  const allCloses = prices
    .map((p) => parseFloat(p.closePrice))
    .filter((v) => !isNaN(v));
  const high60 = allCloses.length > 0 ? Math.max(...allCloses) : null;
  const low60 = allCloses.length > 0 ? Math.min(...allCloses) : null;
  const currentClose = latestPrice ? parseFloat(latestPrice.closePrice) : null;

  // 30-day return (between index 0 and index 29, or last available)
  let return30d: number | null = null;
  const baseIndex = Math.min(29, prices.length - 1);
  if (latestPrice && prices.length >= 2) {
    const cur = parseFloat(latestPrice.closePrice);
    const prev = parseFloat(prices[baseIndex].closePrice);
    if (prev > 0 && prices[baseIndex].priceDate !== latestPrice.priceDate) {
      return30d = ((cur - prev) / prev) * 100;
    }
  }

  // Annualized volatility from daily log returns
  let annualizedVol: number | null = null;
  if (prices.length >= 10) {
    const logReturns = prices
      .slice(0, -1)
      .map((p, i) =>
        Math.log(parseFloat(p.closePrice) / parseFloat(prices[i + 1].closePrice)),
      )
      .filter((v) => isFinite(v));
    if (logReturns.length >= 5) {
      const mean = logReturns.reduce((a, b) => a + b, 0) / logReturns.length;
      const variance =
        logReturns.reduce((a, r) => a + (r - mean) ** 2, 0) / logReturns.length;
      annualizedVol = Math.sqrt(variance * 252) * 100;
    }
  }

  // Where current price sits in the 60-day range (0–100)
  let rangePosition: number | null = null;
  if (currentClose !== null && high60 !== null && low60 !== null && high60 > low60) {
    rangePosition = Math.max(0, Math.min(100, ((currentClose - low60) / (high60 - low60)) * 100));
  }

  const hasStats = allCloses.length > 0 && latestPrice;

  return (
    <div className="card insight-panel">
      {/* ── Identity header ─────────────────────────────────────── */}
      <div className="insight-header">
        <div className="insight-identity">
          <span className="insight-symbol">{instrument.symbol}</span>
          <span className="insight-type-badge">{instrument.assetType}</span>
        </div>
        <p className="insight-name">{instrument.name}</p>
        <p className="insight-meta">
          {instrument.exchangeCode}&nbsp;·&nbsp;{instrument.currency}
        </p>
      </div>

      {/* ── Key stats ───────────────────────────────────────────── */}
      {pricesLoading ? (
        <div className="insight-skeleton">
          <div className="insight-skeleton__row" />
          <div className="insight-skeleton__row insight-skeleton__row--short" />
        </div>
      ) : hasStats ? (
        <>
          <div className="insight-stats">
            <div className="insight-stat">
              <span className="insight-stat__label">{t("insight.latestClose")}</span>
              <span className="insight-stat__value insight-stat__value--price">
                {formatCurrency(latestPrice.closePrice, currency, locale)}
              </span>
              <span className="insight-stat__sub">
                {formatDate(latestPrice.priceDate, locale)}
              </span>
            </div>

            <div className="insight-stat">
              <span className="insight-stat__label">{t("insight.return30d")}</span>
              <span
                className={`insight-stat__value insight-stat__value--num ${
                  return30d === null ? "" : return30d >= 0 ? "insight-positive" : "insight-negative"
                }`}
              >
                {return30d !== null
                  ? `${return30d >= 0 ? "+" : ""}${return30d.toFixed(2)}%`
                  : "—"}
              </span>
              <span className="insight-stat__sub">
                {prices.length} {t("insight.daysSuffix")}
              </span>
            </div>

            <div className="insight-stat">
              <span className="insight-stat__label">{t("insight.high60d")}</span>
              <span className="insight-stat__value insight-stat__value--price">
                {high60 !== null ? formatCurrency(high60, currency, locale) : "—"}
              </span>
            </div>

            <div className="insight-stat">
              <span className="insight-stat__label">{t("insight.low60d")}</span>
              <span className="insight-stat__value insight-stat__value--price">
                {low60 !== null ? formatCurrency(low60, currency, locale) : "—"}
              </span>
            </div>

            {annualizedVol !== null ? (
              <div className="insight-stat">
                <span className="insight-stat__label">{t("insight.volatility")}</span>
                <span className="insight-stat__value">
                  {annualizedVol.toFixed(1)}%
                </span>
                <span className="insight-stat__sub">{t("insight.volatilitySub")}</span>
              </div>
            ) : null}
          </div>
        </>
      ) : (
        <p className="insight-no-data">{t("insight.noData")}</p>
      )}

      {/* ── Intraday chart ──────────────────────────────────────── */}
      <div className="insight-divider" />
      <div className="insight-chart-section">
        <div className="insight-chart-header">
          <span className="insight-chart-title">{t("intraday.title")}</span>
          <div className="insight-chart-actions">
            {barsUpdatedAt && barsUpdatedAt > 0 ? (
              <span className="insight-chart-updated">
                {t("intraday.updated", {
                  time: new Date(barsUpdatedAt).toLocaleTimeString(locale),
                })}
              </span>
            ) : null}
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              disabled={barsLoading}
              onClick={onRefreshBars}
            >
              {barsLoading ? t("common.loading") : t("intraday.refresh")}
            </button>
          </div>
        </div>

        {barsLoading && !barsData ? (
          <div className="insight-chart-loading">{t("common.loading")}</div>
        ) : barsError ? (
          <ErrorBox error={barsError} onRetry={onRefreshBars} />
        ) : barsData && barsData.length > 0 ? (
          <IntradayChart bars={barsData} currency={currency} />
        ) : (
          <EmptyState
            icon="📉"
            title={t("intraday.emptyTitle")}
            description={t("intraday.emptyDescription")}
          />
        )}
      </div>
    </div>
  );
}
