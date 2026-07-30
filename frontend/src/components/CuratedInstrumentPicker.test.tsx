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

  it("renders the curated title with the step indicator", () => {
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

    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByText(/Choose what to trade/i)).toBeInTheDocument();
  });

  it("renders the controlled data universe guard text", () => {
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

    expect(screen.getByText(/Controlled data universe/i)).toBeInTheDocument();
  });

  it("renders the sector tablist label", () => {
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

    expect(screen.getByRole("tablist")).toHaveAccessibleName(/Investment sectors/i);
  });

  it("keeps sector tabs in curated display order", () => {
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

    const tabLabels = screen.getAllByRole("tab").map((tab) => tab.textContent?.trim() ?? "");

    expect(tabLabels[0]).toMatch(/^◎Core portfolio$/i);
    expect(tabLabels[1]).toMatch(/^✦Technology & AI$/i);
    expect(tabLabels[2]).toMatch(/^◒Defensive income$/i);
    expect(tabLabels[3]).toMatch(/^◇Themes & alternatives$/i);
  });

  it("applies active and accent classes to sector tabs", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={null}
          sectorId="themes"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getByRole("tab", { name: /Themes & Alternatives/i })).toHaveClass(
      "sector-tab",
      "sector-tab--amber",
      "is-active",
    );
    expect(screen.getByRole("tab", { name: /Core portfolio/i })).toHaveClass("sector-tab", "sector-tab--indigo");
    expect(screen.getByRole("tab", { name: /Core portfolio/i })).not.toHaveClass("is-active");
  });

  it("adds the selected class to the chosen option", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={instruments.find((instrument) => instrument.symbol === "VOO") ?? null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getByRole("option", { name: /VOO/i })).toHaveClass("instrument-choice", "is-selected");
  });

  it("shows no selected option when the selected instrument is outside the active sector", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={instruments.find((instrument) => instrument.symbol === "AAPL") ?? null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getAllByRole("option").every((option) => option.getAttribute("aria-selected") === "false")).toBe(true);
  });

  it("filters out instruments that are not part of the active sector", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments.filter((instrument) => instrument.symbol === "AAPL")}
          selectedInstrument={null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.queryAllByRole("option")).toHaveLength(0);
    expect(
      screen.getByText(/Some instruments are temporarily unavailable\. Refresh market data and try again\./i),
    ).toBeInTheDocument();
  });

  it("does not change sectors when selecting an instrument", () => {
    const onSectorChange = vi.fn();
    const onSelect = vi.fn();
    render(
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

    fireEvent.click(screen.getByRole("option", { name: /VOO/i }));

    expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ symbol: "VOO" }));
    expect(onSectorChange).not.toHaveBeenCalled();
  });

  it("delegates the current sector id when the active tab is clicked", () => {
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

    fireEvent.click(screen.getByRole("tab", { name: /Core portfolio/i }));
    expect(onSectorChange).toHaveBeenCalledWith("core");
  });

  it("renders each option asset type label", () => {
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

    expect(screen.getByRole("option", { name: /AAPL/i })).toHaveTextContent("STOCK");
    expect(screen.getByRole("option", { name: /MSFT/i })).toHaveTextContent("ETF");
  });

  it("renders a checkmark for the selected option", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={instruments.find((instrument) => instrument.symbol === "VOO") ?? null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getByRole("option", { name: /VOO/i })).toHaveTextContent("✓");
  });

  it("does not render a checkmark for unselected options", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={instruments.find((instrument) => instrument.symbol === "VOO") ?? null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getByRole("option", { name: /VTI/i })).not.toHaveTextContent("✓");
  });

  it("preserves curated order when the incoming instruments are shuffled", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={[...instruments].reverse()}
          selectedInstrument={null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getAllByRole("option").map((option) => option.textContent?.trim() ?? "").slice(0, 4)).toEqual([
      expect.stringContaining("VOO"),
      expect.stringContaining("VTI"),
      expect.stringContaining("QQQ"),
      expect.stringContaining("VT"),
    ]);
  });

  it("uses the latest instrument entry when duplicate symbols are provided", () => {
    const duplicateAapl = {
      ...instruments.find((instrument) => instrument.symbol === "AAPL")!,
      id: "instrument-AAPL-duplicate",
      name: "AAPL Updated Fund",
      exchangeCode: "NYSE",
    };

    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={[...instruments, duplicateAapl]}
          selectedInstrument={duplicateAapl}
          sectorId="technology"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getByRole("option", { name: /AAPL/i })).toHaveAttribute("title", "AAPL Updated Fund · NYSE");
    expect(screen.getByRole("option", { name: /AAPL/i })).toHaveAttribute("aria-selected", "true");
  });

  it("does not select an option when only the symbol matches but the id differs", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={{
            ...(instruments.find((instrument) => instrument.symbol === "VOO") ?? instruments[0]),
            id: "different-id",
          }}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getByRole("option", { name: /VOO/i })).toHaveAttribute("aria-selected", "false");
  });

  it("passes the deduplicated instrument instance to onSelect", () => {
    const duplicateAapl = {
      ...instruments.find((instrument) => instrument.symbol === "AAPL")!,
      id: "instrument-AAPL-duplicate",
      name: "AAPL Updated Fund",
    };
    const onSelect = vi.fn();

    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={[...instruments, duplicateAapl]}
          selectedInstrument={null}
          sectorId="technology"
          onSectorChange={vi.fn()}
          onSelect={onSelect}
        />
      </LanguageProvider>,
    );

    fireEvent.click(screen.getByRole("option", { name: /AAPL/i }));
    expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ id: "instrument-AAPL-duplicate" }));
  });

  it("renders no options and shows the notice when there are no instruments", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={[]}
          selectedInstrument={null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.queryAllByRole("option")).toHaveLength(0);
    expect(screen.getByText(/Some instruments are temporarily unavailable/i)).toBeInTheDocument();
  });

  it("falls back to the core sector list for an unexpected runtime sector id", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={null}
          sectorId={"unexpected" as never}
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    const options = screen.getAllByRole("option");
    expect(options[0]).toHaveTextContent("VOO");
    expect(options).toHaveLength(10);
  });

  it("keeps the notice hidden when the active sector has all ten symbols plus extra off-sector instruments", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={[
            ...instruments.filter((instrument) => CURATED_SECTORS[0].symbols.includes(instrument.symbol)),
            {
              id: "instrument-SPY",
              symbol: "SPY",
              name: "SPY Fund",
              assetType: "ETF",
              exchangeCode: "NYSE",
              currency: "USD",
              isActive: true,
            },
          ]}
          selectedInstrument={null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getAllByRole("option")).toHaveLength(10);
    expect(screen.queryByText(/Some instruments are temporarily unavailable/i)).toBeNull();
  });

  it("renders sector tabs as button elements", () => {
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

    for (const tab of screen.getAllByRole("tab")) {
      expect(tab.tagName).toBe("BUTTON");
      expect(tab).toHaveAttribute("type", "button");
    }
  });

  it("renders instrument options as button elements", () => {
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

    for (const option of screen.getAllByRole("option")) {
      expect(option.tagName).toBe("BUTTON");
      expect(option).toHaveAttribute("type", "button");
    }
  });

  it("marks exactly one option as selected when a visible instrument is selected", () => {
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

    expect(screen.getAllByRole("option", { selected: true })).toHaveLength(1);
    expect(screen.getByRole("option", { name: /QQQ/i })).toHaveClass("is-selected");
  });

  it("leaves every option unselected when the selected instrument is null", () => {
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

    expect(screen.queryAllByRole("option", { selected: true })).toHaveLength(0);
    expect(screen.getAllByRole("option").every((option) => !option.className.includes("is-selected"))).toBe(true);
  });

  it("fires sector changes for each clicked tab in order", () => {
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

    fireEvent.click(screen.getByRole("tab", { name: /Technology & AI/i }));
    fireEvent.click(screen.getByRole("tab", { name: /Defensive income/i }));
    fireEvent.click(screen.getByRole("tab", { name: /Themes & Alternatives/i }));

    expect(onSectorChange.mock.calls).toEqual([["technology"], ["income"], ["themes"]]);
  });

  it("updates the visible option set when the sector prop changes on rerender", () => {
    const view = render(
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

    expect(screen.getByRole("option", { name: /VOO/i })).toBeInTheDocument();

    view.rerender(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={null}
          sectorId="themes"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.queryByRole("option", { name: /VOO/i })).toBeNull();
    expect(screen.getByRole("option", { name: /IBIT/i })).toBeInTheDocument();
  });

  it("renders shared theme symbols while excluding core-only symbols", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={null}
          sectorId="themes"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getByRole("option", { name: /COIN/i })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: /GLD/i })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /VOO/i })).toBeNull();
  });

  it("renders dotted ticker symbols without alteration", () => {
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

    expect(screen.getByRole("option", { name: /BRK\.B/i })).toHaveTextContent("BRK.B");
  });

  it("selects a shared symbol correctly in the themes sector", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={instruments.find((instrument) => instrument.symbol === "COIN") ?? null}
          sectorId="themes"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getByRole("option", { name: /COIN/i })).toHaveAttribute("aria-selected", "true");
    expect(screen.getAllByRole("option", { selected: true })).toHaveLength(1);
  });

  it("keeps the unavailable notice hidden when duplicate in-sector rows still resolve to all ten symbols", () => {
    const duplicateQqq = {
      ...instruments.find((instrument) => instrument.symbol === "QQQ")!,
      id: "instrument-QQQ-duplicate",
      name: "QQQ Latest Fund",
    };

    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={[...instruments.filter((instrument) => instrument.symbol !== "QQQ"), duplicateQqq]}
          selectedInstrument={null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getAllByRole("option")).toHaveLength(10);
    expect(screen.queryByText(/Some instruments are temporarily unavailable/i)).toBeNull();
    expect(screen.getByRole("option", { name: /QQQ/i })).toHaveAttribute("title", "QQQ Latest Fund · NASDAQ");
  });

  it("marks exactly one active tab for the current sector", () => {
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

    expect(screen.getAllByRole("tab", { selected: true })).toHaveLength(1);
    expect(screen.getByRole("tab", { name: /Technology & AI/i })).toHaveClass("is-active");
  });

  it("keeps all tabs inactive when the runtime sector id is unexpected", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={null}
          sectorId={"unexpected" as never}
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.queryAllByRole("tab", { selected: true })).toHaveLength(0);
    expect(screen.getAllByRole("tab").every((tab) => !tab.className.includes("is-active"))).toBe(true);
  });

  it("updates the selected option when selectedInstrument changes on rerender", () => {
    const view = render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={instruments.find((instrument) => instrument.symbol === "VOO") ?? null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getByRole("option", { name: /VOO/i })).toHaveAttribute("aria-selected", "true");

    view.rerender(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={instruments.find((instrument) => instrument.symbol === "VTI") ?? null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getByRole("option", { name: /VOO/i })).toHaveAttribute("aria-selected", "false");
    expect(screen.getByRole("option", { name: /VTI/i })).toHaveAttribute("aria-selected", "true");
  });

  it("clears the selected option when selectedInstrument becomes null on rerender", () => {
    const view = render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={instruments.find((instrument) => instrument.symbol === "VOO") ?? null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    view.rerender(
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

    expect(screen.queryAllByRole("option", { selected: true })).toHaveLength(0);
  });

  it("hides the selected checkmark when the chosen instrument moves outside the active sector", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={instruments.find((instrument) => instrument.symbol === "AAPL") ?? null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getAllByRole("option").every((option) => !option.textContent?.includes("✓"))).toBe(true);
  });

  it("renders the first three theme symbols in curated order", () => {
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={null}
          sectorId="themes"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    const options = screen.getAllByRole("option");
    expect(options[0]).toHaveTextContent("IBIT");
    expect(options[1]).toHaveTextContent("COIN");
    expect(options[2]).toHaveTextContent("ICLN");
  });

  it("excludes income-only symbols from the technology sector", () => {
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

    expect(screen.queryByRole("option", { name: /JNJ/i })).toBeNull();
    expect(screen.queryByRole("option", { name: /PG/i })).toBeNull();
  });

  it("calls onSelect for each repeated click on the same option", () => {
    const onSelect = vi.fn();
    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={instruments}
          selectedInstrument={null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={onSelect}
        />
      </LanguageProvider>,
    );

    const option = screen.getByRole("option", { name: /VOO/i });
    fireEvent.click(option);
    fireEvent.click(option);

    expect(onSelect).toHaveBeenCalledTimes(2);
    expect(onSelect.mock.calls[0][0]).toEqual(expect.objectContaining({ symbol: "VOO" }));
    expect(onSelect.mock.calls[1][0]).toEqual(expect.objectContaining({ symbol: "VOO" }));
  });

  it("updates the unavailable notice when the active sector regains its missing symbol on rerender", () => {
    const incompleteCore = instruments.filter((instrument) => instrument.symbol !== "BND");
    const view = render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={incompleteCore}
          selectedInstrument={null}
          sectorId="core"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getByText(/Some instruments are temporarily unavailable/i)).toBeInTheDocument();

    view.rerender(
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

    expect(screen.queryByText(/Some instruments are temporarily unavailable/i)).toBeNull();
    expect(screen.getAllByRole("option")).toHaveLength(10);
  });

  it("keeps duplicated shared symbols rendered only once in the themes sector", () => {
    const duplicateCoin = {
      ...instruments.find((instrument) => instrument.symbol === "COIN")!,
      id: "instrument-COIN-duplicate",
      name: "COIN Duplicate Fund",
    };

    render(
      <LanguageProvider>
        <CuratedInstrumentPicker
          instruments={[...instruments, duplicateCoin]}
          selectedInstrument={null}
          sectorId="themes"
          onSectorChange={vi.fn()}
          onSelect={vi.fn()}
        />
      </LanguageProvider>,
    );

    expect(screen.getAllByRole("option", { name: /COIN/i })).toHaveLength(1);
    expect(screen.getAllByRole("option")).toHaveLength(10);
  });
});
