import { Navigate, Route, Routes } from "react-router-dom";

import { Layout } from "./Layout";
import { DashboardPage } from "../pages/DashboardPage";
import { PortfoliosPage } from "../pages/PortfoliosPage";
import { HoldingsPage } from "../pages/HoldingsPage";
import { TransactionsPage } from "../pages/TransactionsPage";
import { DataStatusPage } from "../pages/DataStatusPage";

export function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route element={<Layout />}>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/portfolios" element={<PortfoliosPage />} />
        <Route path="/holdings" element={<HoldingsPage />} />
        <Route path="/transactions" element={<TransactionsPage />} />
        <Route path="/data-status" element={<DataStatusPage />} />
      </Route>
    </Routes>
  );
}
