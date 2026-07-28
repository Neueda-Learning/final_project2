import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { ApiError, api } from "../api/client";
import type { Instrument, TradeSide } from "../api/types";
import { usePortfolio } from "../app/PortfolioContext";
import { CuratedInstrumentPicker } from "../components/CuratedInstrumentPicker";
import { EmptyState } from "../components/EmptyState";
import { ErrorBox } from "../components/ErrorBox";
import { PageHeader } from "../components/PageHeader";
import type { CuratedSectorId } from "../data/curatedInstruments";
import { formatCurrency, formatQuantity } from "../lib/format";
import { useLanguage } from "../i18n/LanguageContext";

interface TradeFormState {
  side: TradeSide;
  instrument: Instrument | null;
  quantity: string;
  executionDate: string;
  executionTimestamp: string;
  feeAmount: string;
  note: string;
}

const initialForm = (): TradeFormState => ({
  side: "BUY",
  instrument: null,
  quantity: "",
  executionDate: "",
  executionTimestamp: "",
  feeAmount: "0",
  note: "",
});

function fieldError(error: unknown, field: string): string | null {
  if (!(error instanceof ApiError)) return null;
  const msgs = error.fieldErrors[field];
  return msgs && msgs.length > 0 ? msgs[0] : null;
}

const MARKET_TIME_ZONE = "America/New_York";

function marketDateKey(timestamp: string): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: MARKET_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date(`${timestamp}Z`));
  const value = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${value.year}-${value.month}-${value.day}`;
}

function marketDateLabel(timestamp: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, {
    timeZone: MARKET_TIME_ZONE,
    year: "numeric",
    month: "short",
    day: "numeric",
    weekday: "short",
  }).format(new Date(`${timestamp}Z`));
}

function marketTimeLabel(timestamp: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, {
    timeZone: MARKET_TIME_ZONE,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(`${timestamp}Z`));
}

export function HoldingsPage() {
  const queryClient = useQueryClient();
  const { portfolioId, selectedPortfolio } = usePortfolio();
  const { locale, t } = useLanguage();

  const [form, setForm] = useState<TradeFormState>(initialForm);
  const [sectorId, setSectorId] = useState<CuratedSectorId>("core");
  const [idemKey, setIdemKey] = useState<string>(crypto.randomUUID());

  const positionsQuery = useQuery({
    queryKey: ["positions", portfolioId],
    queryFn: () => api.positions.list(portfolioId!),
    enabled: Boolean(portfolioId),
  });

  const instrumentsQuery = useQuery({
    queryKey: ["instruments", "curated-universe"],
    queryFn: () => api.instruments.list(50),
    staleTime: 10 * 60 * 1000,
  });

  const barsQuery = useQuery({
    queryKey: ["tradable-bars", form.instrument?.id],
    queryFn: () => {
      const to = new Date();
      const from = new Date(to.getTime() - 7 * 24 * 60 * 60 * 1000);
      return api.marketData.getBars(form.instrument!.id, {
        interval: "1min",
        from: from.toISOString().slice(0, 19),
        to: to.toISOString().slice(0, 19),
        page: 1,
        pageSize: 500,
      });
    },
    enabled: Boolean(form.instrument),
    staleTime: 30_000,
  });

  const bars = useMemo(() => barsQuery.data?.items ?? [], [barsQuery.data]);
  const availableDates = useMemo(
    () => [...new Set(bars.map((bar) => marketDateKey(bar.timestamp)))],
    [bars],
  );
  const barsForDate = useMemo(
    () => bars.filter((bar) => marketDateKey(bar.timestamp) === form.executionDate),
    [bars, form.executionDate],
  );
  const selectedBar = useMemo(
    () => bars.find((bar) => bar.timestamp === form.executionTimestamp) ?? null,
    [bars, form.executionTimestamp],
  );

  const submitMutation = useMutation({
    mutationFn: () =>
      api.transactions.create(
        portfolioId!,
        {
          instrumentId: form.instrument!.id,
          side: form.side,
          quantity: form.quantity,
          executionTimestamp: form.executionTimestamp,
          feeAmount: form.feeAmount || "0",
          note: form.note.trim() || null,
        },
        idemKey,
      ),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["positions", portfolioId] }),
        queryClient.invalidateQueries({ queryKey: ["transactions", portfolioId] }),
        queryClient.invalidateQueries({ queryKey: ["dashboard", portfolioId] }),
      ]);
      setForm(initialForm());
      setIdemKey(crypto.randomUUID());
    },
  });

  useEffect(() => {
    setForm(initialForm());
    setIdemKey(crypto.randomUUID());
  }, [portfolioId]);

  useEffect(() => {
    if (!form.instrument || bars.length === 0 || form.executionTimestamp) return;
    const newestBar = bars[0];
    setForm((state) => ({
      ...state,
      executionDate: marketDateKey(newestBar.timestamp),
      executionTimestamp: newestBar.timestamp,
    }));
  }, [bars, form.executionTimestamp, form.instrument]);

  const canSubmit =
    Boolean(portfolioId) &&
    Boolean(form.instrument) &&
    form.quantity.trim().length > 0 &&
    Boolean(selectedBar);

  const currency = selectedPortfolio?.baseCurrency ?? "USD";
  const quantityError = fieldError(submitMutation.error, "quantity");
  const executionTimestampError = fieldError(submitMutation.error, "executionTimestamp");
  const feeAmountError = fieldError(submitMutation.error, "feeAmount");
  const noteError = fieldError(submitMutation.error, "note");
  const instrumentError = fieldError(submitMutation.error, "instrumentId");

  return (
    <>
      <PageHeader title={t("holdings.title")} subtitle={t("holdings.subtitle")} />

      {!portfolioId ? (
        <EmptyState icon="📊" title={t("common.selectPortfolio")} description={t("holdings.noPortfolioDescription")} />
      ) : null}

      {submitMutation.isError ? <ErrorBox error={submitMutation.error} /> : null}
      {positionsQuery.isError ? <ErrorBox error={positionsQuery.error} onRetry={() => positionsQuery.refetch()} /> : null}

      {portfolioId ? (
        <section className="card trade-card trade-ticket">
          <div className="trade-ticket__header">
            <div>
              <span className="trade-ticket__eyebrow">{t("holdings.ticketEyebrow")}</span>
              <h2 className="section-title">{t("holdings.newTrade")}</h2>
            </div>
            <div className="trade-side-toggle" aria-label={t("holdings.direction")}>
              <button
                type="button"
                className={form.side === "BUY" ? "active-buy" : ""}
                onClick={() => setForm((state) => ({ ...state, side: "BUY" }))}
              >
                {t("holdings.buy")}
              </button>
              <button
                type="button"
                className={form.side === "SELL" ? "active-sell" : ""}
                onClick={() => setForm((state) => ({ ...state, side: "SELL" }))}
              >
                {t("holdings.sell")}
              </button>
            </div>
          </div>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!canSubmit) return;
              submitMutation.mutate();
            }}
            className="trade-form"
          >
            <div className="form-group">
              {instrumentsQuery.isLoading ? (
                <div className="curated-picker-loading" aria-label={t("common.loading")}>
                  <div />
                  <div />
                  <div />
                </div>
              ) : null}
              {instrumentsQuery.isError ? (
                <ErrorBox error={instrumentsQuery.error} onRetry={() => instrumentsQuery.refetch()} />
              ) : null}
              {instrumentsQuery.data ? (
                <CuratedInstrumentPicker
                  instruments={instrumentsQuery.data.items}
                  selectedInstrument={form.instrument}
                  sectorId={sectorId}
                  onSectorChange={(nextSectorId) => {
                    setSectorId(nextSectorId);
                    setForm((state) => ({
                      ...state,
                      instrument: null,
                      executionDate: "",
                      executionTimestamp: "",
                    }));
                  }}
                  onSelect={(instrument) =>
                    setForm((state) => ({
                      ...state,
                      instrument,
                      executionDate: "",
                      executionTimestamp: "",
                    }))
                  }
                />
              ) : null}
              {instrumentError ? <div className="form-error">{instrumentError}</div> : null}
            </div>

            <div className="trade-execution-grid">
              <div className="form-group">
                <label className="form-label" htmlFor="quantity">
                  {t("table.quantity")}
                </label>
                <input
                  id="quantity"
                  className={`form-input${quantityError ? " error" : ""}`}
                  inputMode="decimal"
                  value={form.quantity}
                  onChange={(e) => setForm((s) => ({ ...s, quantity: e.target.value }))}
                />
                {quantityError ? <div className="form-error">{quantityError}</div> : null}
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="execution-date">
                  {t("holdings.tradeDate")}
                </label>
                <select
                  id="execution-date"
                  className={`form-select${executionTimestampError ? " error" : ""}`}
                  value={form.executionDate}
                  disabled={!form.instrument || barsQuery.isLoading}
                  onChange={(event) => {
                    const executionDate = event.target.value;
                    const newestBarForDate = bars.find(
                      (bar) => marketDateKey(bar.timestamp) === executionDate,
                    );
                    setForm((state) => ({
                      ...state,
                      executionDate,
                      executionTimestamp: newestBarForDate?.timestamp ?? "",
                    }));
                  }}
                >
                  <option value="">
                    {barsQuery.isLoading ? t("holdings.barsLoading") : t("holdings.chooseDate")}
                  </option>
                  {availableDates.map((date) => {
                    const representative = bars.find(
                      (bar) => marketDateKey(bar.timestamp) === date,
                    );
                    return (
                      <option key={date} value={date}>
                        {representative ? marketDateLabel(representative.timestamp, locale) : date}
                      </option>
                    );
                  })}
                </select>
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="execution-time">
                  {t("holdings.tradeTime")}
                </label>
                <select
                  id="execution-time"
                  className={`form-select${executionTimestampError ? " error" : ""}`}
                  value={form.executionTimestamp}
                  disabled={!form.executionDate || barsQuery.isLoading}
                  onChange={(event) =>
                    setForm((state) => ({
                      ...state,
                      executionTimestamp: event.target.value,
                    }))
                  }
                >
                  <option value="">{t("holdings.chooseTime")}</option>
                  {barsForDate.map((bar) => (
                    <option key={`${bar.timestamp}-${bar.source}`} value={bar.timestamp}>
                      {marketTimeLabel(bar.timestamp, locale)} · {formatCurrency(bar.close, bar.currency, locale)}
                    </option>
                  ))}
                </select>
              </div>
              <div className="minute-price">
                <span>{t("holdings.minuteClose")}</span>
                <strong>
                  {selectedBar
                    ? formatCurrency(selectedBar.close, selectedBar.currency, locale)
                    : "—"}
                </strong>
                <small>
                  {selectedBar
                    ? `${t("holdings.high")} ${selectedBar.high} · ${t("holdings.low")} ${selectedBar.low}`
                    : t("holdings.selectMinuteHint")}
                </small>
              </div>
            </div>

            <div className="trade-time-note">
              <span aria-hidden="true">◷</span>
              <div>
                <strong>{t("holdings.marketTime")}</strong>
                <span>{t("holdings.tradeTimeHint")}</span>
              </div>
            </div>

            {executionTimestampError ? (
              <div className="form-error">{executionTimestampError}</div>
            ) : null}
            {form.instrument && barsQuery.data?.items.length === 0 ? (
                  <div className="form-error">{t("holdings.noPrices")}</div>
            ) : null}
            {barsQuery.isError ? <ErrorBox error={barsQuery.error} /> : null}

            <details className="trade-optional">
              <summary>{t("holdings.optionalDetails")}</summary>
              <div className="trade-optional__body">
                <div className="form-group">
                <label className="form-label" htmlFor="fee-amount">
                  {t("table.fee")}
                </label>
                <input
                  id="fee-amount"
                  className={`form-input${feeAmountError ? " error" : ""}`}
                  inputMode="decimal"
                  value={form.feeAmount}
                  onChange={(e) => setForm((s) => ({ ...s, feeAmount: e.target.value }))}
                />
                <div className="form-hint">{t("holdings.feeHint")}</div>
                {feeAmountError ? <div className="form-error">{feeAmountError}</div> : null}
                </div>
                <div className="form-group">
                  <label className="form-label" htmlFor="trade-note">
                    {t("table.note")}
                  </label>
                  <textarea
                    id="trade-note"
                    className={`form-textarea${noteError ? " error" : ""}`}
                    maxLength={500}
                    value={form.note}
                    onChange={(event) =>
                      setForm((state) => ({ ...state, note: event.target.value }))
                    }
                  />
                  {noteError ? <div className="form-error">{noteError}</div> : null}
                </div>
              </div>
            </details>

            <div className="form-actions">
              <button
                type="button"
                className="btn btn-ghost"
                onClick={() => {
                  setForm(initialForm());
                  setIdemKey(crypto.randomUUID());
                }}
              >
                {t("holdings.reset")}
              </button>
              <button type="submit" className="btn btn-primary" disabled={!canSubmit || submitMutation.isPending}>
                {submitMutation.isPending ? t("common.submitting") : t("holdings.submit")}
              </button>
            </div>
          </form>
        </section>
      ) : null}

      {portfolioId && positionsQuery.data && positionsQuery.data.items.length === 0 ? (
        <EmptyState
          icon="🪹"
          title={t("holdings.emptyTitle")}
          description={t("holdings.emptyDescription")}
        />
      ) : null}

      {positionsQuery.data && positionsQuery.data.items.length > 0 ? (
        <section>
          <div className="section-header">
            <h2 className="section-title">{t("holdings.current")}</h2>
          </div>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>{t("table.symbol")}</th>
                  <th className="num">{t("table.quantity")}</th>
                  <th className="num">{t("table.averageCost")}</th>
                  <th className="num">{t("table.realizedPnl")}</th>
                </tr>
              </thead>
              <tbody>
                {positionsQuery.data.items.map((p) => (
                  <tr key={p.instrumentId}>
                    <td>
                      <div className="holding-identity">
                        <strong>{p.symbol}</strong>
                        <span>{p.name} · {p.assetType}</span>
                      </div>
                    </td>
                    <td className="num">{formatQuantity(p.quantity, locale)}</td>
                    <td className="num">{formatCurrency(p.averageCost, currency, locale)}</td>
                    <td className="num">{formatCurrency(p.realizedPnl, currency, locale)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ) : null}
    </>
  );
}
