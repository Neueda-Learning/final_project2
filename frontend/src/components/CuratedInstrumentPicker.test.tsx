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

  it("marks the selected instrument as selected", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={instruments.find((instrument) => instrument.symbol === "QQQ") ?? null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getByRole("option", { name: /QQQ/i })).toHaveAttribute("aria-selected", "true");
  });

  it("marks unselected instruments as not selected", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={instruments.find((instrument) => instrument.symbol === "QQQ") ?? null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getByRole("option", { name: /VTI/i })).toHaveAttribute("aria-selected", "false");
  });

  it("shows the curated unavailable notice when fewer than ten instruments are present", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments.filter((instrument) => instrument.symbol !== "BND")}
          selectedInstrument={null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(
      screen.getByText(/Some instruments are temporarily unavailable\. Refresh market data and try again\./i),
    ).toBeInTheDocument();
  });

  it("hides the curated unavailable notice when all ten instruments are present", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(
      screen.queryByText(/Some instruments are temporarily unavailable\. Refresh market data and try again\./i),
    ).toBeNull();
  });

  it("renders four sector tabs", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getAllByRole("tab")).toHaveLength(4);
  });

  it("marks only the active sector tab as selected", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={null}
          sectorId="income"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getByRole("tab", { name: /Defensive income/i })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tab", { name: /Core portfolio/i })).toHaveAttribute("aria-selected", "false");
  });

  it("switches to the themes tab through the provided callback", () => {
    const onSectorChange = vi.fn();
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={null}
          sectorId="core"
          onSectorChange={onSectorChange}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    fireEvent.click(screen.getByRole("tab", { name: /Themes & Alternatives/i }));
    expect(onSectorChange).toHaveBeenCalledWith("themes");
  });

  it("shows the active sector symbols in display order", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={null}
          sectorId="technology"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    const options = screen.getAllByRole("option");
    expect(options[0]).toHaveTextContent("AAPL");
    expect(options[1]).toHaveTextContent("MSFT");
    expect(options[2]).toHaveTextContent("NVDA");
  });

  it("preserves instrument metadata in the option title", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={null}
          sectorId="technology"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getByRole("option", { name: /AAPL/i })).toHaveAttribute(
      "title",
      "AAPL Fund · NASDAQ",
    );
  });

  it("renders the listbox label for curated instrument choices", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getByRole("listbox")).toHaveAccessibleName(/Available stocks and ETFs/i);
  });
});
