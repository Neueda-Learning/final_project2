import {
  ArcElement,
  CategoryScale,
  Chart as ChartJS,
  Legend,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
} from "chart.js";
import { Doughnut, Line } from "react-chartjs-2";

import type { AllocationItem, MarketBar, PerformancePoint } from "../api/types";
import { formatCurrency } from "../lib/format";
import { useLanguage } from "../i18n/LanguageContext";

ChartJS.register(
  ArcElement,
  Tooltip,
  Legend,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
);

const PALETTE = ["#3158d4", "#111317", "#6c83d9", "#98a2b3", "#8aa4f8", "#475467"];

export function AllocationChart({
  data,
  currency,
}: {
  data: AllocationItem[];
  currency: string;
}) {
  const { locale } = useLanguage();
  return (
    <div className="chart-wrap">
      <Doughnut
        data={{
          labels: data.map((d) => d.symbol),
          datasets: [
            {
              data: data.map((d) => Number(d.marketValue)),
              backgroundColor: data.map((_, i) => PALETTE[i % PALETTE.length]),
              borderWidth: 0,
            },
          ],
        }}
        options={{
          maintainAspectRatio: false,
          plugins: {
            legend: { position: "bottom" },
            tooltip: {
              callbacks: {
                label: (ctx) => {
                  const label = ctx.label ?? "";
                  const value = ctx.parsed as number;
                  return `${label}: ${formatCurrency(value, currency, locale)}`;
                },
              },
            },
          },
        }}
      />
    </div>
  );
}

export function PerformanceChart({
  points,
  currency,
}: {
  points: PerformancePoint[];
  currency: string;
}) {
  const { locale, t } = useLanguage();
  return (
    <div className="chart-wrap">
      <Line
        data={{
          labels: points.map((p) => p.valuationDate),
          datasets: [
            {
              label: t("chart.marketValue"),
              data: points.map((p) => Number(p.pricedMarketValue)),
              borderColor: "#3158d4",
              backgroundColor: "rgba(49,88,212,0.12)",
              tension: 0.25,
              pointRadius: 2,
              fill: true,
            },
          ],
        }}
        options={{
          maintainAspectRatio: false,
          scales: {
            y: {
              ticks: {
                callback: (value) => formatCurrency(Number(value), currency, locale),
              },
            },
          },
          plugins: {
            legend: { display: false },
            tooltip: {
              callbacks: {
                label: (ctx) => formatCurrency(ctx.parsed.y, currency, locale),
              },
            },
          },
        }}
      />
    </div>
  );
}

export function IntradayChart({
  bars,
  currency,
}: {
  bars: MarketBar[];
  currency: string;
}) {
  const { locale, t } = useLanguage();
  return (
    <div className="chart-wrap chart-wrap--intraday">
      <Line
        data={{
          labels: bars.map((bar) =>
            new Date(`${bar.timestamp}Z`).toLocaleTimeString(locale, {
              month: "short",
              day: "numeric",
              hour: "2-digit",
              minute: "2-digit",
            }),
          ),
          datasets: [{
            label: t("intraday.close"),
            data: bars.map((bar) => Number(bar.close)),
            borderColor: "#3158d4",
            backgroundColor: "rgba(49,88,212,0.08)",
            borderWidth: 2,
            pointRadius: 0,
            pointHitRadius: 8,
            tension: 0.12,
            fill: true,
          }],
        }}
        options={{
          maintainAspectRatio: false,
          animation: { duration: 250 },
          interaction: { intersect: false, mode: "index" },
          scales: {
            x: {
              ticks: { maxTicksLimit: 8, maxRotation: 0 },
              grid: { display: false },
            },
            y: {
              position: "right",
              ticks: {
                callback: (value) =>
                  formatCurrency(Number(value), currency, locale),
              },
            },
          },
          plugins: {
            legend: { display: false },
            tooltip: {
              callbacks: {
                label: (ctx) =>
                  formatCurrency(ctx.parsed.y, currency, locale),
              },
            },
          },
        }}
      />
    </div>
  );
}
