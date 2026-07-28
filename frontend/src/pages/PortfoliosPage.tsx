import { type FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { ApiError, api } from "../api/client";
import type { Portfolio } from "../api/types";
import { usePortfolio } from "../app/PortfolioContext";
import { EmptyState } from "../components/EmptyState";
import { ErrorBox } from "../components/ErrorBox";
import { PageHeader } from "../components/PageHeader";
import { formatDateTime } from "../lib/format";

interface FormState {
  name: string;
  description: string;
  baseCurrency: string;
}

const EMPTY_FORM: FormState = { name: "", description: "", baseCurrency: "USD" };

function fieldError(error: unknown, field: string): string | null {
  if (!(error instanceof ApiError)) return null;
  const msgs = error.fieldErrors[field];
  return msgs && msgs.length > 0 ? msgs[0] : null;
}

export function PortfoliosPage() {
  const queryClient = useQueryClient();
  const { portfolioId, setPortfolioId } = usePortfolio();

  const [editing, setEditing] = useState<Portfolio | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [open, setOpen] = useState(false);

  const listQuery = useQuery({
    queryKey: ["portfolios", { includeArchived: false }],
    queryFn: () => api.portfolios.list(false),
  });

  const createMutation = useMutation({
    mutationFn: () =>
      api.portfolios.create({
        name: form.name.trim(),
        description: form.description.trim() || null,
        baseCurrency: form.baseCurrency.trim().toUpperCase(),
      }),
    onSuccess: async (p) => {
      await queryClient.invalidateQueries({ queryKey: ["portfolios"] });
      setPortfolioId(p.id);
      closeModal();
    },
  });

  const updateMutation = useMutation({
    mutationFn: () =>
      api.portfolios.update(editing!.id, {
        name: form.name.trim(),
        description: form.description.trim() || null,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["portfolios"] });
      closeModal();
    },
  });

  const archiveMutation = useMutation({
    mutationFn: (id: string) => api.portfolios.archive(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["portfolios"] });
    },
  });

  const rows = listQuery.data?.items ?? [];

  const sortedRows = useMemo(
    () => [...rows].sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()),
    [rows],
  );

  const openCreate = () => {
    setEditing(null);
    setForm(EMPTY_FORM);
    setOpen(true);
  };

  const openEdit = (p: Portfolio) => {
    setEditing(p);
    setForm({ name: p.name, description: p.description ?? "", baseCurrency: p.baseCurrency });
    setOpen(true);
  };

  const closeModal = () => {
    setOpen(false);
    setEditing(null);
    setForm(EMPTY_FORM);
  };

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (editing) updateMutation.mutate();
    else createMutation.mutate();
  };

  const pending = createMutation.isPending || updateMutation.isPending;
  const mutationError = createMutation.error ?? updateMutation.error;
  const nameError = fieldError(mutationError, "name");
  const descriptionError = fieldError(mutationError, "description");
  const currencyError = fieldError(mutationError, "baseCurrency");

  return (
    <>
      <PageHeader
        title="Portfolios"
        subtitle="创建、重命名和归档投资组合"
        actions={
          <button type="button" className="btn btn-primary" onClick={openCreate}>
            新建组合
          </button>
        }
      />

      {listQuery.isError ? <ErrorBox error={listQuery.error} onRetry={() => listQuery.refetch()} /> : null}
      {archiveMutation.isError ? <ErrorBox error={archiveMutation.error} /> : null}

      {listQuery.data && listQuery.data.items.length === 0 ? (
        <EmptyState
          icon="📁"
          title="还没有任何组合"
          description="先创建第一个组合，再开始录入交易和查看估值。"
          action={
            <button type="button" className="btn btn-primary" onClick={openCreate}>
              创建第一个组合
            </button>
          }
        />
      ) : null}

      {sortedRows.length > 0 ? (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>名称</th>
                <th>币种</th>
                <th>创建时间</th>
                <th>更新时间</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {sortedRows.map((row) => (
                <tr key={row.id}>
                  <td>
                    <strong>{row.name}</strong>
                    {row.description ? <div className="page-subtitle">{row.description}</div> : null}
                  </td>
                  <td>{row.baseCurrency}</td>
                  <td>{formatDateTime(row.createdAt)}</td>
                  <td>{formatDateTime(row.updatedAt)}</td>
                  <td>{row.id === portfolioId ? <span className="badge badge-fresh">当前组合</span> : <span className="info-pill">可切换</span>}</td>
                  <td>
                    <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
                      <button type="button" className="btn btn-secondary btn-sm" onClick={() => setPortfolioId(row.id)}>
                        选择
                      </button>
                      <button type="button" className="btn btn-ghost btn-sm" onClick={() => openEdit(row)}>
                        编辑
                      </button>
                      <button
                        type="button"
                        className="btn btn-danger btn-sm"
                        disabled={archiveMutation.isPending}
                        onClick={() => archiveMutation.mutate(row.id)}
                      >
                        归档
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}

      {open ? (
        <div className="modal-overlay" role="dialog" aria-modal="true" aria-label={editing ? "编辑组合" : "创建组合"}>
          <div className="modal">
            <header className="modal-header">
              <h2 className="modal-title">{editing ? "编辑组合" : "创建组合"}</h2>
              <button type="button" className="modal-close" onClick={closeModal}>
                x
              </button>
            </header>
            <form className="modal-body" onSubmit={onSubmit}>
              {(createMutation.isError || updateMutation.isError) && (
                <ErrorBox error={mutationError} />
              )}

              <div className="form-group">
                <label className="form-label" htmlFor="portfolio-name">
                  名称
                </label>
                <input
                  id="portfolio-name"
                  className={`form-input${nameError ? " error" : ""}`}
                  value={form.name}
                  required
                  maxLength={120}
                  onChange={(e) => setForm((s) => ({ ...s, name: e.target.value }))}
                />
                {nameError ? <div className="form-error">{nameError}</div> : null}
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="portfolio-desc">
                  描述
                </label>
                <textarea
                  id="portfolio-desc"
                  className={`form-textarea${descriptionError ? " error" : ""}`}
                  value={form.description}
                  maxLength={500}
                  onChange={(e) => setForm((s) => ({ ...s, description: e.target.value }))}
                />
                {descriptionError ? <div className="form-error">{descriptionError}</div> : null}
              </div>

              {!editing ? (
                <div className="form-group">
                  <label className="form-label" htmlFor="portfolio-currency">
                    基础币种
                  </label>
                  <input
                    id="portfolio-currency"
                    className={`form-input${currencyError ? " error" : ""}`}
                    value={form.baseCurrency}
                    maxLength={3}
                    onChange={(e) => setForm((s) => ({ ...s, baseCurrency: e.target.value.toUpperCase() }))}
                  />
                  {currencyError ? <div className="form-error">{currencyError}</div> : null}
                </div>
              ) : null}

              <div className="form-actions">
                <button type="button" className="btn btn-ghost" onClick={closeModal}>
                  取消
                </button>
                <button type="submit" className="btn btn-primary" disabled={pending}>
                  {pending ? "提交中..." : editing ? "保存" : "创建"}
                </button>
              </div>
            </form>
          </div>
        </div>
      ) : null}
    </>
  );
}
