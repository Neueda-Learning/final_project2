import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { ApiError, api } from "../api/client";
import type { Instrument, TradeSide } from "../api/types";
import { usePortfolio } from "../app/PortfolioContext";
import { EmptyState } from "../components/EmptyState";
import { ErrorBox } from "../components/ErrorBox";
import { PageHeader } from "../components/PageHeader";
import { formatCurrency, formatDateTime, formatQuantity } from "../lib/format";

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
      <PageHeader title="Holdings" subtitle="录入交易并查看当前持仓" />

      {!portfolioId ? (
        <EmptyState icon="📊" title="请选择组合" description="左侧选择组合后才能交易和查看持仓。" />
      ) : null}

      {submitMutation.isError ? <ErrorBox error={submitMutation.error} /> : null}
      {positionsQuery.isError ? <ErrorBox error={positionsQuery.error} onRetry={() => positionsQuery.refetch()} /> : null}

      {portfolioId ? (
        <section className="card" style={{ marginBottom: "1rem" }}>
          <div className="section-header">
            <h2 className="section-title">新增交易</h2>
          </div>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!canSubmit) return;
              submitMutation.mutate();
            }}
            style={{ display: "grid", gap: "1rem" }}
          >
            <div className="form-group">
              <label className="form-label">方向</label>
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
                股票/ETF 搜索
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
                    更换
                  </button>
                </div>
              ) : (
                <>
                  <input
                    id="instrument-query"
                    className={`form-input${instrumentError ? " error" : ""}`}
                    placeholder="输入股票代码或名称，例如 AAPL"
                    value={form.query}
                    onChange={(e) => setForm((s) => ({ ...s, query: e.target.value }))}
                  />
                  {instrumentError ? <div className="form-error">{instrumentError}</div> : null}
                  {searchQuery.isError ? <ErrorBox error={searchQuery.error} /> : null}
                  {searchItems.length > 0 ? (
                    <div className="search-results" role="listbox" aria-label="标的搜索结果">
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
                  数量
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
                  单价
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
                  手续费
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
                  成交时间
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
                备注
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
                重置
              </button>
              <button type="submit" className="btn btn-primary" disabled={!canSubmit || submitMutation.isPending}>
                {submitMutation.isPending ? "提交中..." : "提交交易"}
              </button>
            </div>
          </form>
        </section>
      ) : null}

      {portfolioId && positionsQuery.data && positionsQuery.data.items.length === 0 ? (
        <EmptyState
          icon="🪹"
          title="当前没有持仓"
          description="通过上方交易表单录入第一笔买入后，这里会显示持仓。"
        />
      ) : null}

      {positionsQuery.data && positionsQuery.data.items.length > 0 ? (
        <section>
          <div className="section-header">
            <h2 className="section-title">当前持仓</h2>
          </div>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>标的</th>
                  <th>名称</th>
                  <th>类型</th>
                  <th className="num">数量</th>
                  <th className="num">平均成本</th>
                  <th className="num">已实现盈亏</th>
                  <th>首次建仓</th>
                  <th>最近更新</th>
                </tr>
              </thead>
              <tbody>
                {positionsQuery.data.items.map((p) => (
                  <tr key={p.instrumentId}>
                    <td className="sym">{p.symbol}</td>
                    <td>{p.name}</td>
                    <td>{p.assetType}</td>
                    <td className="num">{formatQuantity(p.quantity)}</td>
                    <td className="num">{formatCurrency(p.averageCost, currency)}</td>
                    <td className="num">{formatCurrency(p.realizedPnl, currency)}</td>
                    <td>{formatDateTime(p.openedAt)}</td>
                    <td>{formatDateTime(p.updatedAt)}</td>
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
