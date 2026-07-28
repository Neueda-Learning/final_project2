// ─── Primitives ───────────────────────────────────────────────────────────────
export type Currency = string;
export type DecimalString = string;
export type AssetType = "STOCK" | "ETF";
export type TradeSide = "BUY" | "SELL";
export type PriceStatus = "FRESH" | "STALE" | "UNAVAILABLE";
export type SyncStatus = "RUNNING" | "SUCCEEDED" | "PARTIAL" | "FAILED";
export type SyncTrigger = "SCHEDULE" | "MANUAL" | "RETRY";

// ─── Error ────────────────────────────────────────────────────────────────────
export interface ErrorResponse {
  code: string;
  message: string;
  fieldErrors: Record<string, string[]>;
  requestId: string;
}

// ─── Portfolio ────────────────────────────────────────────────────────────────
export interface PortfolioCreate {
  name: string;
  description?: string | null;
  baseCurrency: Currency;
}

export interface PortfolioUpdate {
  name?: string;
  description?: string | null;
}

export interface Portfolio {
  id: string;
  name: string;
  description: string | null;
  baseCurrency: Currency;
  isArchived: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PortfolioPage {
  items: Portfolio[];
  page: number;
  pageSize: number;
  total: number;
}

// ─── Instruments ──────────────────────────────────────────────────────────────
export interface Instrument {
  id: string;
  symbol: string;
  name: string;
  assetType: AssetType;
  exchangeCode: string;
  currency: Currency;
  isActive: boolean;
}

export interface InstrumentList {
  items: Instrument[];
}

// ─── Transactions ─────────────────────────────────────────────────────────────
export interface TransactionCreate {
  instrumentId: string;
  side: TradeSide;
  quantity: DecimalString;
  tradeDate: string;
  unitPrice: DecimalString;
  feeAmount?: DecimalString;
  note?: string | null;
}

export interface Transaction {
  id: string;
  portfolioId: string;
  instrumentId: string;
  symbol: string;
  side: TradeSide;
  quantity: DecimalString;
  unitPrice: DecimalString;
  feeAmount: DecimalString;
  currency: Currency;
  executedAt: string;
  note: string | null;
  createdAt: string;
}

export interface TransactionPage {
  items: Transaction[];
  page: number;
  pageSize: number;
  total: number;
}

// ─── Positions ────────────────────────────────────────────────────────────────
export interface Position {
  instrumentId: string;
  symbol: string;
  name: string;
  assetType: AssetType;
  quantity: DecimalString;
  averageCost: DecimalString;
  realizedPnl: DecimalString;
  openedAt: string;
  updatedAt: string;
}

export interface PositionList {
  items: Position[];
}

// ─── Market Data ──────────────────────────────────────────────────────────────
export interface SyncRequest {
  force?: boolean;
}

export interface SyncRun {
  id: string;
  provider: string;
  status: SyncStatus;
  requestedCount: number;
  successCount: number;
  failureCount: number;
  startedAt: string;
  completedAt: string | null;
  triggeredBy: SyncTrigger;
  errorSummary: string | null;
}

export interface MarketPrice {
  instrumentId: string;
  symbol: string;
  priceDate: string;
  closePrice: DecimalString;
  adjustedClose: DecimalString | null;
  currency: Currency;
  source: string;
  sourceTimestamp: string | null;
  fetchedAt: string;
  priceStatus: PriceStatus;
}

export interface MarketBar {
  instrumentId: string;
  symbol: string;
  interval: string;
  timestamp: string;
  open: DecimalString;
  high: DecimalString;
  low: DecimalString;
  close: DecimalString;
  volume: number | null;
  currency: Currency;
  source: string;
}

export interface MarketBarPage {
  items: MarketBar[];
  page: number;
  pageSize: number;
  total: number;
  hasNext: boolean;
}

// ─── Analytics ────────────────────────────────────────────────────────────────
export interface PortfolioSummary {
  positionCount: number;
  pricedPositionCount: number;
  unpricedPositionCount: number;
  pricedMarketValue: DecimalString;
  totalCostBasis: DecimalString;
  pricedCostBasis: DecimalString;
  unrealizedPnl: DecimalString;
  returnPct: DecimalString | null;
  newestPriceDate: string | null;
  oldestUsedPriceDate: string | null;
}

export interface DashboardPortfolio {
  id: string;
  name: string;
  baseCurrency: Currency;
}

export interface DashboardPosition {
  instrumentId: string;
  symbol: string;
  name: string;
  assetType: AssetType;
  quantity: DecimalString;
  averageCost: DecimalString;
  costBasis: DecimalString;
  closePrice: DecimalString | null;
  priceDate: string | null;
  priceSource: string | null;
  priceStatus: PriceStatus;
  marketValue: DecimalString | null;
  unrealizedPnl: DecimalString | null;
  returnPct: DecimalString | null;
  allocationPct: DecimalString | null;
}

export interface AllocationItem {
  instrumentId: string;
  symbol: string;
  marketValue: DecimalString;
  allocationPct: DecimalString;
}

export interface DashboardResponse {
  portfolio: DashboardPortfolio;
  summary: PortfolioSummary;
  positions: DashboardPosition[];
  allocation: AllocationItem[];
}

export interface PerformancePoint {
  valuationDate: string;
  pricedMarketValue: DecimalString;
  totalCostBasis: DecimalString;
  pricedCostBasis: DecimalString;
  unrealizedPnl: DecimalString;
  pricedPositionCount: number;
  unpricedPositionCount: number;
}

export interface PerformanceResponse {
  portfolioId: string;
  baseCurrency: Currency;
  points: PerformancePoint[];
}

// ─── Health ───────────────────────────────────────────────────────────────────
export interface LiveHealth {
  status: "ok";
}

export interface ReadyHealth {
  status: "ready";
  checks: { mysql: "ok" };
}
