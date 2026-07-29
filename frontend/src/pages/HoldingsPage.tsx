import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { ApiError, api } from "../api/client";
import type { Instrument, TradeSide } from "../api/types";
import { usePortfolio } from "../app/PortfolioContext";
import { CuratedInstrumentPicker } from "../components/CuratedInstrumentPicker";
import { EmptyState } from "../components/EmptyState";
import { ErrorBox } from "../components/ErrorBox";
import { PageHeader } from "../components/PageHeader";
import { IntradayChart } from "../components/charts";
import type { CuratedSectorId } from "../data/curatedInstruments";
import { formatCurrency, formatQuantity } from "../lib/format";
import { useLanguage } from "../i18n/LanguageContext";

interface TradeFormState {
  side: TradeSide;
  instrument: Instrument | null;
  quantity: string;
  tradeDate: string;
  unitPrice: string;
  feeAmount: string;
  note: string;
}

function localTodayISODate(): string {
  const now = new Date();
  const tzOffsetMs = now.getTimezoneOffset() * 60_000;
  return new Date(now.getTime() - tzOffsetMs).toISOString().slice(0, 10);
}

const initialForm = (): TradeFormState => ({
  side: "BUY",
  instrument: null,
  quantity: "",
  tradeDate: localTodayISODate(),
  unitPrice: "",
  feeAmount: "0",
  note: "",
});

function fieldError(error: unknown, field: string): string | null {
  if (!(error instanceof ApiError)) return null;
  const msgs = error.fieldErrors[field];
  return msgs && msgs.length > 0 ? msgs[0] : null;
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

  const pricesQuery = useQuery({
    queryKey: ["tradable-prices", form.instrument?.id],
    queryFn: () => api.marketData.getTradablePrices(form.instrument!.id, 60),
    enabled: Boolean(form.instrument),
    staleTime: 10 * 60 * 1000,
  });

  const referencePrice = useMemo(
    () =>
      pricesQuery.data?.find((price) => price.priceDate === form.tradeDate) ?? null,
    [form.tradeDate, pricesQuery.data],
  );

  const barsQuery = useQuery({
    queryKey: ["intraday-bars", form.instrument?.id],
    queryFn: async () => {
      const id = form.instrument!.id;
      const symbol = form.instrument!.symbol;
      console.log(`[IntradayChart] Fetching bars for ${symbol} (${id})`);
      try {
        const result = await api.marketData.getBars(id, { interval: "1min", pageSize: 120 });
        console.log(`[IntradayChart] Response for ${symbol}: ${result.items.length} bars, total=${result.total}`);
        return result;
      } catch (err) {
        console.error(`[IntradayChart] Error fetching bars for ${symbol}:`, err);
        throw err;
      }
    },
    enabled: Boolean(form.instrument),
    staleTime: 0,
    refetchInterval: 60 * 1000,
  });

  const submitMutation = useMutation({
    mutationFn: () =>
      api.transactions.create(
        portfolioId!,
        {
          instrumentId: form.instrument!.id,
          side: form.side,
          quantity: form.quantity,
          tradeDate: form.tradeDate,
          unitPrice: form.unitPrice,
          feeAmount: form.feeAmount || "0",
          note: form.note.trim() || null,
        },
        idemKey,
      ),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["positions", portfolioId] }),
        queryClient.invalidateQueries({ queryKey: ["transactions", portfolioId] }),
        queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
        queryClient.invalidateQueries({ queryKey: ["performance"] }),
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
    if (!form.instrument || !form.tradeDate || pricesQuery.isPending) return;
    setForm((state) =>
      state.unitPrice
        ? state
        : {
            ...state,
            unitPrice: referencePrice?.closePrice ?? "",
          },
    );
  }, [form.instrument, form.tradeDate, pricesQuery.isPending, referencePrice]);

  const canSubmit =
    Boolean(portfolioId) &&
    Boolean(form.instrument) &&
    form.quantity.trim().length > 0 &&
    form.tradeDate.length > 0 &&
    form.unitPrice.trim().length > 0;

  const currency = selectedPortfolio?.baseCurrency ?? "USD";
  const quantityError = fieldError(submitMutation.error, "quantity");
  const tradeDateError = fieldError(submitMutation.error, "tradeDate");
  const unitPriceError = fieldError(submitMutation.error, "unitPrice");
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
                      unitPrice: "",
                    }));
                  }}
                  onSelect={(instrument) =>
                    setForm((state) => ({
                      ...state,
                      instrument,
                      unitPrice: "",
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
                <input
                  type="date"
                  id="execution-date"
                  className={`form-input${tradeDateError ? " error" : ""}`}
                  value={form.tradeDate}
                  onChange={(event) =>
                    setForm((state) => ({
                      ...state,
                      tradeDate: event.target.value,
                      unitPrice: "",
                    }))
                  }
                />
                {tradeDateError ? <div className="form-error">{tradeDateError}</div> : null}
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="unit-price">
                  {t("holdings.unitPrice")}
                </label>
                <input
                  id="unit-price"
                  className={`form-input${unitPriceError ? " error" : ""}`}
                  inputMode="decimal"
                  placeholder="0.00"
                  value={form.unitPrice}
                  onChange={(event) =>
                    setForm((state) => ({
                      ...state,
                      unitPrice: event.target.value,
                    }))
                  }
                />
                <div className="form-hint">
                  {form.instrument && pricesQuery.isPending
                    ? t("holdings.referencePriceLoading")
                    : referencePrice
                      ? t("holdings.referencePriceFound", {
                          price: formatCurrency(
                            referencePrice.closePrice,
                            referencePrice.currency,
                            locale,
                          ),
                        })
                      : t("holdings.manualPriceHint")}
                </div>
                {unitPriceError ? <div className="form-error">{unitPriceError}</div> : null}
                {pricesQuery.isError ? (
                  <div className="form-error">{t("holdings.referencePriceUnavailable")}</div>
                ) : null}
              </div>
            </div>

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

      {form.instrument ? (
        <section className="card intraday-card" aria-label={t("intraday.title")}>
          <div className="intraday-card__header">
            <div>
              <p className="intraday-card__eyebrow">{t("intraday.eyebrow")}</p>
              <h2 className="section-title">
                {form.instrument.symbol} &mdash; {t("intraday.title")}
              </h2>
            </div>
            <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 4 }}>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                disabled={barsQuery.isFetching}
                onClick={() => {
                  console.log("[IntradayChart] Manual refresh triggered");
                  barsQuery.refetch();
                }}
              >
                {barsQuery.isFetching ? t("common.loading") : t("intraday.refresh")}
              </button>
              {barsQuery.dataUpdatedAt ? (
                <span style={{ fontSize: "0.75rem", color: "#667085" }}>
                  {t("intraday.updated", { time: new Date(barsQuery.dataUpdatedAt).toLocaleTimeString(locale) })}
                </span>
              ) : null}
              {barsQuery.isError ? (
                <span style={{ fontSize: "0.75rem", color: "#d92d20" }}>
                  ⚠ 请求失败，请检查后端服务
                </span>
              ) : null}
            </div>
          </div>
          {barsQuery.isPending ? (
            <div style={{ height: 360, display: "flex", alignItems: "center", justifyContent: "center", color: "#667085" }}>
              {t("common.loading")}
            </div>
          ) : barsQuery.isError ? (
            <ErrorBox error={barsQuery.error} onRetry={() => barsQuery.refetch()} />
          ) : barsQuery.data && barsQuery.data.items.length > 0 ? (
            <>
              <IntradayChart bars={barsQuery.data.items} currency={form.instrument.currency} />
              <p className="intraday-card__footer">
                {t("intraday.points", { count: String(barsQuery.data.items.length) })}
                {barsQuery.dataUpdatedAt
                  ? ` · ${t("intraday.updated", { time: new Date(barsQuery.dataUpdatedAt).toLocaleTimeString(locale) })}`
                  : null}
              </p>
            </>
          ) : (
            <EmptyState
              icon="📉"
              title={t("intraday.emptyTitle")}
              description={t("intraday.emptyDescription")}
            />
          )}
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
