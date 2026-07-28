import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { Instrument } from "../api/types";
import { CURATED_SECTORS } from "../data/curatedInstruments";
import { LanguageProvider } from "../i18n/LanguageContext";
import { CuratedInstrumentPicker } from "./CuratedInstrumentPicker";

const instruments: Instrument[] = [
  ...new Set(CURATED_SECTORS.flatMap((sector) => [...sector.symbols])),
].map((symbol) => ({
  id: `instrument-${symbol}`,
  symbol,
  name: `${symbol} Fund`,
  assetType: symbol === "AAPL" ? "STOCK" : "ETF",
  exchangeCode: "NASDAQ",
  currency: "USD",
  isActive: true,
}));

describe("CuratedInstrumentPicker", () => {
  it("shows exactly ten choices per sector and selects by click", () => {
    const onSelect = vi.fn();
    const onSectorChange = vi.fn();
    const { rerender } = render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={null}
          sectorId="core"
          onSectorChange={onSectorChange}
          onSelect={onSelect}
        />
      </LanguageProvider>,
    );

    expect(screen.getAllByRole("option")).toHaveLength(10);
    fireEvent.click(screen.getByRole("tab", { name: /Technology & AI/i }));
    expect(onSectorChange).toHaveBeenCalledWith("technology");

    rerender(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={null}
          sectorId="technology"
          onSectorChange={onSectorChange}
          onSelect={onSelect}
        />
      </LanguageProvider>,
    );

    expect(screen.getAllByRole("option")).toHaveLength(10);
    fireEvent.click(screen.getByRole("option", { name: /AAPL/i }));
    expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ symbol: "AAPL" }));
  });
});
