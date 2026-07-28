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
});
