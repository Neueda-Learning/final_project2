import { createContext, useContext, type ReactNode } from "react";

import type { Portfolio } from "../api/types";

export interface PortfolioContextValue {
  portfolioId: string | null;
  setPortfolioId: (id: string) => void;
  selectedPortfolio: Portfolio | null;
}

const PortfolioContext = createContext<PortfolioContextValue | null>(null);

export function PortfolioProvider({
  value,
  children,
}: {
  value: PortfolioContextValue;
  children: ReactNode;
}) {
  return <PortfolioContext.Provider value={value}>{children}</PortfolioContext.Provider>;
}

// The provider and hook intentionally share this small context module.
// eslint-disable-next-line react-refresh/only-export-components
export function usePortfolio() {
  const ctx = useContext(PortfolioContext);
  if (!ctx) {
    throw new Error("usePortfolio must be used within PortfolioProvider");
  }
  return ctx;
}
