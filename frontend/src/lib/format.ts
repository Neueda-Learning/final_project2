/** Format a decimal string as a localised currency value. */
export function formatCurrency(
  value: string | number | null | undefined,
  currency = "USD",
): string {
  if (value === null || value === undefined || value === "") return "—";
  const num = typeof value === "string" ? parseFloat(value) : value;
  if (isNaN(num)) return "—";
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(num);
}

/** Format a decimal string as a percentage, optionally with a +/- sign. */
export function formatPercent(
  value: string | null | undefined,
  signed = true,
): string {
  if (value === null || value === undefined || value === "") return "—";
  const num = parseFloat(value);
  if (isNaN(num)) return "—";
  const sign = signed && num > 0 ? "+" : "";
  return `${sign}${num.toFixed(2)}%`;
}

/** Format a quantity decimal string, dropping unnecessary trailing zeros. */
export function formatQuantity(value: string | null | undefined): string {
  if (!value) return "—";
  const num = parseFloat(value);
  if (isNaN(num)) return value;
  return num.toLocaleString("en-US", {
    maximumFractionDigits: 8,
    minimumFractionDigits: 0,
  });
}

/** Format an ISO date string as a short date (e.g. Jul 27, 2026). */
export function formatDate(value: string | null | undefined): string {
  if (!value) return "—";
  try {
    // Parse YYYY-MM-DD without timezone offset to avoid off-by-one day
    const d = value.includes("T") ? new Date(value) : new Date(value + "T00:00:00");
    return d.toLocaleDateString("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  } catch {
    return value;
  }
}

/** Format an ISO date-time string as a short datetime. */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "—";
  try {
    return new Date(value).toLocaleString("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return value;
  }
}

/** Return "positive", "negative", or "neutral" for a decimal string P&L. */
export function pnlSign(
  value: string | null | undefined,
): "positive" | "negative" | "neutral" {
  if (!value) return "neutral";
  const num = parseFloat(value);
  if (isNaN(num) || num === 0) return "neutral";
  return num > 0 ? "positive" : "negative";
}

/** Return ▲, ▼, or "" for a decimal P&L value. */
export function pnlArrow(value: string | null | undefined): string {
  const sign = pnlSign(value);
  if (sign === "positive") return "▲";
  if (sign === "negative") return "▼";
  return "";
}
