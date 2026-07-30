const BEIJING_OFFSET_SUFFIX = "+08:00";
const DATE_ONLY = /^\d{4}-\d{2}-\d{2}$/;
const HAS_TIMEZONE_SUFFIX = /(Z|[+-]\d{2}:?\d{2})$/i;

export const BEIJING_TIME_ZONE = "Asia/Shanghai";

export function parseApiDateTime(value: string | null | undefined): Date | null {
  if (!value) return null;

  const raw = value.trim();
  if (!raw) return null;

  let normalized = raw.includes(" ") ? raw.replace(" ", "T") : raw;
  if (DATE_ONLY.test(normalized)) {
    normalized = `${normalized}T00:00:00${BEIJING_OFFSET_SUFFIX}`;
  } else if (!HAS_TIMEZONE_SUFFIX.test(normalized)) {
    normalized = `${normalized}${BEIJING_OFFSET_SUFFIX}`;
  }

  const parsed = new Date(normalized);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

export function toEpochMs(value: string | null | undefined): number | null {
  const parsed = parseApiDateTime(value);
  return parsed ? parsed.getTime() : null;
}

export function beijingTodayISODate(now = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: BEIJING_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(now);

  const year = parts.find((part) => part.type === "year")?.value;
  const month = parts.find((part) => part.type === "month")?.value;
  const day = parts.find((part) => part.type === "day")?.value;

  if (!year || !month || !day) {
    return "";
  }
  return `${year}-${month}-${day}`;
}

/** Format a decimal string as a localised currency value. */
export function formatCurrency(
  value: string | number | null | undefined,
  currency = "USD",
  locale = "en-US",
): string {
  if (value === null || value === undefined || value === "") return "—";
  const num = typeof value === "string" ? parseFloat(value) : value;
  if (isNaN(num)) return "—";
  return new Intl.NumberFormat(locale, {
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
export function formatQuantity(value: string | null | undefined, locale = "en-US"): string {
  if (!value) return "—";
  const num = parseFloat(value);
  if (isNaN(num)) return value;
  return num.toLocaleString(locale, {
    maximumFractionDigits: 8,
    minimumFractionDigits: 0,
  });
}

/** Format an ISO date string as a short date (e.g. Jul 27, 2026). */
export function formatDate(value: string | null | undefined, locale = "en-US"): string {
  if (!value) return "—";
  const parsed = parseApiDateTime(value);
  if (!parsed) return value;
  return parsed.toLocaleDateString(locale, {
    year: "numeric",
    month: "short",
    day: "numeric",
    timeZone: BEIJING_TIME_ZONE,
  });
}

/** Format an ISO date-time string as a short datetime. */
export function formatDateTime(value: string | null | undefined, locale = "en-US"): string {
  if (!value) return "—";
  const parsed = parseApiDateTime(value);
  if (!parsed) return value;
  return parsed.toLocaleString(locale, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    timeZone: BEIJING_TIME_ZONE,
  });
}

/** Format a clock time in Beijing timezone (HH:mm by locale). */
export function formatTime(
  value: string | number | Date | null | undefined,
  locale = "en-US",
): string {
  if (value === null || value === undefined || value === "") return "—";

  const parsed =
    typeof value === "string"
      ? parseApiDateTime(value)
      : value instanceof Date
        ? value
        : new Date(value);

  if (!parsed || Number.isNaN(parsed.getTime())) {
    return typeof value === "string" ? value : "—";
  }

  return parsed.toLocaleTimeString(locale, {
    hour: "2-digit",
    minute: "2-digit",
    timeZone: BEIJING_TIME_ZONE,
  });
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
