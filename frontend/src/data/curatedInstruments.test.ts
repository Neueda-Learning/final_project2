import { describe, expect, it } from "vitest";

import { CURATED_SECTORS, curatedSector } from "./curatedInstruments";

describe("curated instrument universe", () => {
  it("offers four sectors with exactly ten unique choices each", () => {
    expect(CURATED_SECTORS).toHaveLength(4);

    for (const sector of CURATED_SECTORS) {
      expect(sector.symbols).toHaveLength(10);
      expect(new Set(sector.symbols).size).toBe(10);
    }
  });

  it("falls back to the core sector", () => {
    expect(curatedSector("core").id).toBe("core");
  });

  it("keeps sectors in the expected display order", () => {
    expect(CURATED_SECTORS.map((sector) => sector.id)).toEqual([
      "core",
      "technology",
      "income",
      "themes",
    ]);
  });

  it("assigns the expected icon to each sector", () => {
    expect(CURATED_SECTORS.map((sector) => sector.icon)).toEqual([
      "◎",
      "✦",
      "◒",
      "◇",
    ]);
  });

  it("assigns a unique accent to each sector", () => {
    expect(new Set(CURATED_SECTORS.map((sector) => sector.accent)).size).toBe(4);
  });

  it("returns the technology sector by id", () => {
    expect(curatedSector("technology").symbols).toContain("NVDA");
  });

  it("returns the income sector by id", () => {
    expect(curatedSector("income").symbols).toContain("SCHD");
  });

  it("returns the themes sector by id", () => {
    expect(curatedSector("themes").symbols).toContain("IBIT");
  });

  it("keeps the core sector diversified with bond exposure", () => {
    expect(curatedSector("core").symbols).toContain("BND");
  });

  it("shares growth symbols across technology and themes intentionally", () => {
    expect(curatedSector("technology").symbols).toContain("COIN");
    expect(curatedSector("themes").symbols).toContain("COIN");
    expect(curatedSector("technology").symbols).toContain("MTUM");
    expect(curatedSector("themes").symbols).toContain("MTUM");
  });

  it("does not duplicate symbols within the income sector", () => {
    const incomeSymbols = curatedSector("income").symbols;
    expect(new Set(incomeSymbols).size).toBe(incomeSymbols.length);
  });

  it("exposes only uppercase ticker symbols", () => {
    for (const sector of CURATED_SECTORS) {
      for (const symbol of sector.symbols) {
        expect(symbol).toBe(symbol.toUpperCase());
      }
    }
  });

  it("covers every curated sector id through the selector", () => {
    for (const sector of CURATED_SECTORS) {
      expect(curatedSector(sector.id)).toBe(sector);
    }
  });
});
