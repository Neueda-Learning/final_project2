import { describe, expect, it } from "vitest";

import {
  formatCurrency,
  formatDate,
  formatDateTime,
  formatPercent,
  formatQuantity,
  pnlArrow,
  pnlSign,
} from "./format";

describe("format helpers", () => {
  it("formats currency", () => {
    expect(formatCurrency("1234.5", "USD")).toBe("$1,234.50");
    expect(formatCurrency(null, "USD")).toBe("—");
  });

  it("formats numeric currency input", () => {
    expect(formatCurrency(2500, "USD")).toBe("$2,500.00");
  });

  it("returns a dash for invalid currency input", () => {
    expect(formatCurrency("not-a-number", "USD")).toBe("—");
  });

  it("formats percent", () => {
    expect(formatPercent("12.3456")).toBe("+12.35%");
    expect(formatPercent("-0.1")).toBe("-0.10%");
    expect(formatPercent(null)).toBe("—");
  });

  it("omits the plus sign when signed formatting is disabled", () => {
    expect(formatPercent("12.3456", false)).toBe("12.35%");
  });

  it("returns a dash for an empty percent value", () => {
    expect(formatPercent("")).toBe("—");
  });

  it("formats quantity", () => {
    expect(formatQuantity("1000.00000000")).toBe("1,000");
    expect(formatQuantity("1.23450000")).toBe("1.2345");
  });

  it("returns the original quantity string when parsing fails", () => {
    expect(formatQuantity("abc")).toBe("abc");
  });

  it("returns a dash for a missing quantity", () => {
    expect(formatQuantity(undefined)).toBe("—");
  });

  it("formats date and datetime", () => {
    expect(formatDate("2026-07-27")).toContain("2026");
    expect(formatDateTime("2026-07-27T08:30:00Z")).toContain("2026");
  });

  it("returns a dash for an empty date", () => {
    expect(formatDate("")).toBe("—");
  });

  it("returns a dash for an empty datetime", () => {
    expect(formatDateTime(undefined)).toBe("—");
  });

  it("returns pnl sign and arrow", () => {
    expect(pnlSign("10")).toBe("positive");
    expect(pnlSign("-1")).toBe("negative");
    expect(pnlSign("0")).toBe("neutral");
    expect(pnlArrow("10")).toBe("▲");
    expect(pnlArrow("-1")).toBe("▼");
    expect(pnlArrow("0")).toBe("");
  });

  it("treats invalid pnl values as neutral", () => {
    expect(pnlSign("oops")).toBe("neutral");
    expect(pnlArrow("oops")).toBe("");
  });
});
