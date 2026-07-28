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

import type { AllocationItem, PerformancePoint } from "../api/types";
import { formatCurrency } from "../lib/format";

ChartJS.register(
  ArcElement,
  Tooltip,
  Legend,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
);

const PALETTE = ["#176b4d", "#26885f", "#3da972", "#7ecf9a", "#b7eac7", "#0f4a35"];

export function AllocationChart({
  data,
  currency,
}: {
  data: AllocationItem[];
  currency: string;
}) {
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
                  return `${label}: ${formatCurrency(value, currency)}`;
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
  return (
    <div className="chart-wrap">
      <Line
        data={{
          labels: points.map((p) => p.valuationDate),
          datasets: [
            {
              label: "市值",
              data: points.map((p) => Number(p.pricedMarketValue)),
              borderColor: "#176b4d",
              backgroundColor: "rgba(23,107,77,0.15)",
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
                callback: (value) => formatCurrency(Number(value), currency),
              },
            },
          },
          plugins: {
            legend: { display: false },
            tooltip: {
              callbacks: {
                label: (ctx) => formatCurrency(ctx.parsed.y, currency),
              },
            },
          },
        }}
      />
    </div>
  );
}
