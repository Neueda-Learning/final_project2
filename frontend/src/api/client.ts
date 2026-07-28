import type {
  Portfolio,
  PortfolioPage,
  PortfolioCreate,
  PortfolioUpdate,
  InstrumentList,
  Transaction,
  TransactionPage,
  TransactionCreate,
  PositionList,
  SyncRun,
  MarketPrice,
  MarketBarPage,
  DashboardResponse,
  PerformanceResponse,
  LiveHealth,
  ReadyHealth,
  ErrorResponse,
} from "./types";

// Base URL: empty string = same origin (Vite proxy in local dev).
// Set VITE_API_BASE_URL to e.g. http://localhost:8000 in docker-compose.
const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");
const V1 = `${API_BASE}/api/v1`;

// ─── Error class ─────────────────────────────────────────────────────────────
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly fieldErrors: Record<string, string[]>,
    public readonly requestId: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function isApiError(e: unknown): e is ApiError {
  return e instanceof ApiError;
}

// ─── Internal fetch wrapper ───────────────────────────────────────────────────
async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  headers.set("Accept", "application/json");
  if (init?.body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const res = await fetch(url, {
    ...init,
    headers,
  });

  if (res.status === 204) return undefined as T;

  let json: unknown = null;
  try {
    json = await res.json();
  } catch {
    // non-JSON body
  }

  if (!res.ok) {
    const err = json as ErrorResponse | null;
    throw new ApiError(
      res.status,
      err?.code ?? "UNKNOWN_ERROR",
      err?.message ?? `HTTP ${res.status}`,
      err?.fieldErrors ?? {},
      err?.requestId ?? "",
    );
  }

  return json as T;
}

function qs(params: Record<string, string | number | boolean | undefined>): string {
  const entries = Object.entries(params).filter(([, v]) => v !== undefined) as [
    string,
    string | number | boolean,
  ][];
  if (entries.length === 0) return "";
  return "?" + new URLSearchParams(entries.map(([k, v]) => [k, String(v)]));
}

// ─── Public API surface ───────────────────────────────────────────────────────
export const api = {
  portfolios: {
    list: (includeArchived = false) =>
      request<PortfolioPage>(`${V1}/portfolios${qs({ includeArchived })}`),

    get: (id: string) => request<Portfolio>(`${V1}/portfolios/${id}`),

    create: (data: PortfolioCreate) =>
      request<Portfolio>(`${V1}/portfolios`, {
        method: "POST",
        body: JSON.stringify(data),
      }),

    update: (id: string, data: PortfolioUpdate) =>
      request<Portfolio>(`${V1}/portfolios/${id}`, {
        method: "PATCH",
        body: JSON.stringify(data),
      }),

    delete: (id: string) =>
      request<void>(`${V1}/portfolios/${id}`, { method: "DELETE" }),

    archive: (id: string) =>
      request<Portfolio>(`${V1}/portfolios/${id}/archive`, { method: "POST" }),
  },

  instruments: {
    search: (query: string, limit = 10) =>
      request<InstrumentList>(`${V1}/instruments${qs({ query, limit })}`),
  },

  transactions: {
    list: (portfolioId: string, page = 1, pageSize = 20, sort = "-executedAt") =>
      request<TransactionPage>(
        `${V1}/portfolios/${portfolioId}/transactions${qs({ page, pageSize, sort })}`,
      ),

    create: (
      portfolioId: string,
      data: TransactionCreate,
      idempotencyKey: string,
    ) =>
      request<Transaction>(`${V1}/portfolios/${portfolioId}/transactions`, {
        method: "POST",
        body: JSON.stringify(data),
        headers: { "Idempotency-Key": idempotencyKey },
      }),
  },

  positions: {
    list: (portfolioId: string) =>
      request<PositionList>(`${V1}/portfolios/${portfolioId}/positions`),
  },

  marketData: {
    triggerSync: (force = false) =>
      request<SyncRun>(`${V1}/market-data/sync`, {
        method: "POST",
        body: JSON.stringify({ force }),
      }),

    getLatestSync: () =>
      request<SyncRun | null>(`${V1}/market-data/sync-runs/latest`),

    getLatestPrice: (instrumentId: string) =>
      request<MarketPrice>(`${V1}/instruments/${instrumentId}/latest-price`),

    getTradablePrices: (instrumentId: string, limit = 60) =>
      request<MarketPrice[]>(
        `${V1}/instruments/${instrumentId}/tradable-prices${qs({ limit })}`,
      ),

    getBars: (
      instrumentId: string,
      params: {
        interval?: string;
        from?: string;
        to?: string;
        page?: number;
        pageSize?: number;
      } = {},
    ) =>
      request<MarketBarPage>(
        `${V1}/instruments/${instrumentId}/bars${qs({
          interval: params.interval ?? "1min",
          from: params.from,
          to: params.to,
          page: params.page ?? 1,
          pageSize: params.pageSize ?? 200,
        })}`,
      ),
  },

  analytics: {
    getDashboard: (portfolioId: string) =>
      request<DashboardResponse>(`${V1}/portfolios/${portfolioId}/dashboard`),

    getPerformance: (portfolioId: string, from?: string, to?: string) =>
      request<PerformanceResponse>(
        `${V1}/portfolios/${portfolioId}/performance${qs({ from, to })}`,
      ),
  },

  health: {
    liveness: () => request<LiveHealth>(`${API_BASE}/health/live`),
    readiness: () => request<ReadyHealth>(`${API_BASE}/health/ready`),
  },
};
