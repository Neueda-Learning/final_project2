import { describe, expect, it } from "vitest";

import {
  beijingTodayISODate,
  formatCurrency,
  formatDate,
  formatDateTime,
  formatPercent,
  formatQuantity,
  pnlArrow,
  pnlSign,
  toEpochMs,
} from "./format";

describe("format helpers", () => {
  it("formats currency", () => {
    expect(formatCurrency("1234.5", "USD")).toBe("$1,234.50");
    expect(formatCurrency(null, "USD")).toBe("—");
  });

  it("formats percent", () => {
    expect(formatPercent("12.3456")).toBe("+12.35%");
    expect(formatPercent("-0.1")).toBe("-0.10%");
    expect(formatPercent(null)).toBe("—");
  });

  it("formats quantity", () => {
    expect(formatQuantity("1000.00000000")).toBe("1,000");
    expect(formatQuantity("1.23450000")).toBe("1.2345");
  });

  it("formats date and datetime", () => {
    expect(formatDate("2026-07-27")).toContain("2026");
    expect(formatDateTime("2026-07-27T08:30:00Z")).toContain("2026");
  });

  it("parses timezone-free API timestamps as Beijing time", () => {
    expect(toEpochMs("2026-07-27T16:00:00"))
      .toBe(new Date("2026-07-27T08:00:00Z").getTime());
  });

  it("generates today date in Beijing timezone", () => {
    expect(beijingTodayISODate(new Date("2026-07-27T17:30:00Z")))
      .toBe("2026-07-28");
  });

  it("returns pnl sign and arrow", () => {
    expect(pnlSign("10")).toBe("positive");
    expect(pnlSign("-1")).toBe("negative");
    expect(pnlSign("0")).toBe("neutral");
    expect(pnlArrow("10")).toBe("▲");
    expect(pnlArrow("-1")).toBe("▼");
    expect(pnlArrow("0")).toBe("");
  });
});
