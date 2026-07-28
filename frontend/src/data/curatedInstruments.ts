export type CuratedSectorId = "core" | "technology" | "income" | "themes";

export interface CuratedSector {
  id: CuratedSectorId;
  icon: string;
  accent: string;
  symbols: readonly string[];
}

export const CURATED_SECTORS: readonly CuratedSector[] = [
  {
    id: "core",
    icon: "◎",
    accent: "indigo",
    symbols: ["VOO", "VTI", "QQQ", "VT", "VXUS", "VWO", "IEMG", "IWM", "VB", "BND"],
  },
  {
    id: "technology",
    icon: "✦",
    accent: "violet",
    symbols: ["AAPL", "MSFT", "NVDA", "TSLA", "QQQ", "SOXX", "SMH", "SKYY", "MTUM", "COIN"],
  },
  {
    id: "income",
    icon: "◒",
    accent: "emerald",
    symbols: ["SCHD", "JNJ", "PG", "BRK.B", "JPM", "XLP", "XLV", "O", "VNQ", "GLD"],
  },
  {
    id: "themes",
    icon: "◇",
    accent: "amber",
    symbols: ["IBIT", "COIN", "ICLN", "TAN", "IBB", "XBI", "IPO", "MTUM", "SH", "GLD"],
  },
] as const;

export function curatedSector(id: CuratedSectorId): CuratedSector {
  return CURATED_SECTORS.find((sector) => sector.id === id) ?? CURATED_SECTORS[0];
}
