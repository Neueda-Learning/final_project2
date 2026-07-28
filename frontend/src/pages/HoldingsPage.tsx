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
import { formatCurrency, formatDate, formatDateTime, formatQuantity } from "../lib/format";
import { useLanguage } from "../i18n/LanguageContext";

interface TradeFormState {
  side: TradeSide;
  instrument: Instrument | null;
  quantity: string;
  priceDate: string;
  feeAmount: string;
  note: string;
}

const initialForm = (): TradeFormState => ({
  side: "BUY",
  instrument: null,
  quantity: "",
  priceDate: "",
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
    queryFn: () => api.marketData.getTradablePrices(form.instrument!.id),
    enabled: Boolean(form.instrument),
  });

  const selectedPrice = useMemo(
    () => pricesQuery.data?.find((price) => price.priceDate === form.priceDate) ?? null,
    [pricesQuery.data, form.priceDate],
  );

  const submitMutation = useMutation({
    mutationFn: () =>
      api.transactions.create(
        portfolioId!,
        {
          instrumentId: form.instrument!.id,
          side: form.side,
          quantity: form.quantity,
          priceDate: form.priceDate,
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

  const canSubmit =
    Boolean(portfolioId) &&
    Boolean(form.instrument) &&
    form.quantity.trim().length > 0 &&
    Boolean(selectedPrice);

  const currency = selectedPortfolio?.baseCurrency ?? "USD";
  const quantityError = fieldError(submitMutation.error, "quantity");
  const priceDateError = fieldError(submitMutation.error, "priceDate");
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
        <section className="card trade-card">
          <div className="section-header">
            <h2 className="section-title">{t("holdings.newTrade")}</h2>
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
              <label className="form-label">{t("holdings.direction")}</label>
              <div className="trade-side-toggle">
                <button
                  type="button"
                  className={form.side === "BUY" ? "active-buy" : ""}
                  onClick={() => setForm((s) => ({ ...s, side: "BUY" }))}
                >
                  BUY
                </button>
                <button
                  type="button"
                  className={form.side === "SELL" ? "active-sell" : ""}
                  onClick={() => setForm((s) => ({ ...s, side: "SELL" }))}
                >
                  SELL
                </button>
              </div>
            </div>

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
                  onSectorChange={setSectorId}
                  onSelect={(instrument) =>
                    setForm((state) => ({ ...state, instrument, priceDate: "" }))
                  }
                />
              ) : null}
              {instrumentError ? <div className="form-error">{instrumentError}</div> : null}
            </div>

            <div className="form-row">
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
                <label className="form-label" htmlFor="price-date">
                  {t("holdings.tradeDate")}
                </label>
                <select
                  id="price-date"
                  className={`form-select${priceDateError ? " error" : ""}`}
                  value={form.priceDate}
                  disabled={!form.instrument || pricesQuery.isLoading}
                  onChange={(e) => setForm((s) => ({ ...s, priceDate: e.target.value }))}
                >
                  <option value="">
                    {pricesQuery.isLoading ? t("holdings.priceLoading") : t("holdings.chooseDate")}
                  </option>
                  {(pricesQuery.data ?? []).map((price) => (
                    <option key={`${price.priceDate}-${price.source}`} value={price.priceDate}>
                      {formatDate(price.priceDate, locale)} · {formatCurrency(price.closePrice, price.currency, locale)}
                    </option>
                  ))}
                </select>
                <div className="form-hint">{t("holdings.tradeDateHint")}</div>
                {priceDateError ? <div className="form-error">{priceDateError}</div> : null}
                {form.instrument && pricesQuery.data?.length === 0 ? (
                  <div className="form-error">{t("holdings.noPrices")}</div>
                ) : null}
                {pricesQuery.isError ? <ErrorBox error={pricesQuery.error} /> : null}
              </div>
            </div>

            <div className="form-row">
              <div className="form-group">
                <label className="form-label" htmlFor="unit-price">
                  {t("holdings.officialClose")}
                </label>
                <input
                  id="unit-price"
                  className="form-input"
                  value={selectedPrice
                    ? formatCurrency(selectedPrice.closePrice, selectedPrice.currency, locale)
                    : "—"}
                  readOnly
                  aria-readonly="true"
                />
              </div>
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
                onChange={(e) => setForm((s) => ({ ...s, note: e.target.value }))}
              />
              {noteError ? <div className="form-error">{noteError}</div> : null}
            </div>

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
                  <th>{t("table.name")}</th>
                  <th>{t("table.type")}</th>
                  <th className="num">{t("table.quantity")}</th>
                  <th className="num">{t("table.averageCost")}</th>
                  <th className="num">{t("table.realizedPnl")}</th>
                  <th>{t("table.opened")}</th>
                  <th>{t("table.updated")}</th>
                </tr>
              </thead>
              <tbody>
                {positionsQuery.data.items.map((p) => (
                  <tr key={p.instrumentId}>
                    <td className="sym">{p.symbol}</td>
                    <td>{p.name}</td>
                    <td>{p.assetType}</td>
                    <td className="num">{formatQuantity(p.quantity, locale)}</td>
                    <td className="num">{formatCurrency(p.averageCost, currency, locale)}</td>
                    <td className="num">{formatCurrency(p.realizedPnl, currency, locale)}</td>
                    <td>{formatDateTime(p.openedAt, locale)}</td>
                    <td>{formatDateTime(p.updatedAt, locale)}</td>
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
