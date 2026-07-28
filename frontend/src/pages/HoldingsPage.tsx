import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { ApiError, api } from "../api/client";
import type { Instrument, TradeSide } from "../api/types";
import { usePortfolio } from "../app/PortfolioContext";
import { EmptyState } from "../components/EmptyState";
import { ErrorBox } from "../components/ErrorBox";
import { PageHeader } from "../components/PageHeader";
import { formatCurrency, formatDateTime, formatQuantity } from "../lib/format";
import { useLanguage } from "../i18n/LanguageContext";

interface TradeFormState {
  side: TradeSide;
  instrument: Instrument | null;
  query: string;
  quantity: string;
  unitPrice: string;
  feeAmount: string;
  executedAt: string;
  note: string;
}

const initialForm = (): TradeFormState => ({
  side: "BUY",
  instrument: null,
  query: "",
  quantity: "",
  unitPrice: "",
  feeAmount: "0",
  executedAt: new Date().toISOString().slice(0, 16),
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
  const [idemKey, setIdemKey] = useState<string>(crypto.randomUUID());

  const positionsQuery = useQuery({
    queryKey: ["positions", portfolioId],
    queryFn: () => api.positions.list(portfolioId!),
    enabled: Boolean(portfolioId),
  });

  const searchQuery = useQuery({
    queryKey: ["instruments", form.query],
    queryFn: () => api.instruments.search(form.query, 10),
    enabled: form.query.trim().length > 0,
  });

  const submitMutation = useMutation({
    mutationFn: () =>
      api.transactions.create(
        portfolioId!,
        {
          instrumentId: form.instrument!.id,
          side: form.side,
          quantity: form.quantity,
          unitPrice: form.unitPrice,
          feeAmount: form.feeAmount || "0",
          executedAt: new Date(form.executedAt).toISOString(),
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
    form.unitPrice.trim().length > 0;

  const searchItems = useMemo(() => searchQuery.data?.items ?? [], [searchQuery.data]);
  const currency = selectedPortfolio?.baseCurrency ?? "USD";
  const quantityError = fieldError(submitMutation.error, "quantity");
  const unitPriceError = fieldError(submitMutation.error, "unitPrice");
  const feeAmountError = fieldError(submitMutation.error, "feeAmount");
  const executedAtError = fieldError(submitMutation.error, "executedAt");
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

            <div className="form-group search-wrap">
              <label className="form-label" htmlFor="instrument-query">
                {t("holdings.search")}
              </label>
              {form.instrument ? (
                <div className="search-selected">
                  <div>
                    <strong>{form.instrument.symbol}</strong> · {form.instrument.name}
                  </div>
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    onClick={() => setForm((s) => ({ ...s, instrument: null, query: "" }))}
                  >
                    {t("holdings.change")}
                  </button>
                </div>
              ) : (
                <>
                  <input
                    id="instrument-query"
                    className={`form-input${instrumentError ? " error" : ""}`}
                    placeholder={t("holdings.searchPlaceholder")}
                    value={form.query}
                    onChange={(e) => setForm((s) => ({ ...s, query: e.target.value }))}
                  />
                  {instrumentError ? <div className="form-error">{instrumentError}</div> : null}
                  {searchQuery.isError ? <ErrorBox error={searchQuery.error} /> : null}
                  {searchItems.length > 0 ? (
                    <div className="search-results" role="listbox" aria-label={t("holdings.searchResults")}>
                      {searchItems.map((item) => (
                        <button
                          type="button"
                          key={item.id}
                          className="search-result-item"
                          onClick={() => setForm((s) => ({ ...s, instrument: item, query: item.symbol }))}
                        >
                          <span className="search-result-item__symbol">{item.symbol}</span>
                          <span className="search-result-item__name">{item.name}</span>
                          <span className="search-result-item__type">{item.assetType}</span>
                        </button>
                      ))}
                    </div>
                  ) : null}
                </>
              )}
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
                <label className="form-label" htmlFor="unit-price">
                  {t("table.unitPrice")}
                </label>
                <input
                  id="unit-price"
                  className={`form-input${unitPriceError ? " error" : ""}`}
                  inputMode="decimal"
                  value={form.unitPrice}
                  onChange={(e) => setForm((s) => ({ ...s, unitPrice: e.target.value }))}
                />
                {unitPriceError ? <div className="form-error">{unitPriceError}</div> : null}
              </div>
            </div>

            <div className="form-row">
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
                {feeAmountError ? <div className="form-error">{feeAmountError}</div> : null}
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="executed-at">
                  {t("holdings.executedAt")}
                </label>
                <input
                  id="executed-at"
                  className={`form-input${executedAtError ? " error" : ""}`}
                  type="datetime-local"
                  value={form.executedAt}
                  onChange={(e) => setForm((s) => ({ ...s, executedAt: e.target.value }))}
                />
                {executedAtError ? <div className="form-error">{executedAtError}</div> : null}
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
