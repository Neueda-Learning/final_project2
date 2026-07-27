export type Currency = string;
export type DecimalString = string;
export type AssetType = "STOCK" | "ETF";
export type TradeSide = "BUY" | "SELL";
export type PriceStatus = "FRESH" | "STALE" | "MISSING";
export type SyncStatus = "RUNNING" | "SUCCEEDED" | "PARTIAL" | "FAILED";

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

export interface Instrument {
  id: string;
  symbol: string;
  name: string;
  assetType: AssetType;
  exchangeCode: string;
  currency: Currency;
  isActive: boolean;
}

export interface Transaction {
  id: string;
  portfolioId: string;
  instrumentId: string;
  side: TradeSide;
  quantity: DecimalString;
  unitPrice: DecimalString;
  feeAmount: DecimalString;
  currency: Currency;
  executedAt: string;
  idempotencyKey: string;
  note: string | null;
  createdAt: string;
}

export interface Position {
  portfolioId: string;
  instrument: Instrument;
  quantity: DecimalString;
  averageCost: DecimalString;
  realizedPnl: DecimalString;
  costBasis: DecimalString;
  closePrice: DecimalString | null;
  marketValue: DecimalString | null;
  unrealizedPnl: DecimalString | null;
  returnPct: DecimalString | null;
  priceDate: string | null;
  priceStatus: PriceStatus;
}

export interface PortfolioSummary {
  portfolioId: string;
  positionCount: number;
  pricedPositionCount: number;
  unpricedPositionCount: number;
  pricedMarketValue: DecimalString;
  totalCostBasis: DecimalString;
  unrealizedPnl: DecimalString;
  returnPct: DecimalString | null;
}

export interface AllocationItem {
  instrumentId: string;
  symbol: string;
  marketValue: DecimalString | null;
  allocationPct: DecimalString | null;
}

export interface ApiError {
  code: string;
  message: string;
  details: Array<{ field?: string; message: string }>;
  requestId?: string;
}
