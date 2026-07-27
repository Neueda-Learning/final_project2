import { useQuery } from "@tanstack/react-query";

import { api } from "../api/client";

export function App() {
  const portfolios = useQuery({
    queryKey: ["portfolios"],
    queryFn: api.portfolios.list,
  });

  return (
    <main className="shell">
      <header className="hero">
        <p className="eyebrow">Portfolio Manager</p>
        <h1>投资组合概览</h1>
        <p>统一管理股票与 ETF，查看持仓、成本、行情与估值。</p>
      </header>

      <section className="panel" aria-labelledby="portfolio-heading">
        <div className="panel-heading">
          <h2 id="portfolio-heading">我的组合</h2>
          <button type="button">新建组合</button>
        </div>

        {portfolios.isPending && <p>正在加载组合…</p>}
        {portfolios.isError && (
          <p role="alert">API 尚未连接。启动后端后即可加载真实数据。</p>
        )}
        {portfolios.data?.items.length === 0 && <p>还没有组合，请先创建一个。</p>}
        <ul className="portfolio-list">
          {portfolios.data?.items.map((portfolio) => (
            <li key={portfolio.id}>
              <strong>{portfolio.name}</strong>
              <span>{portfolio.baseCurrency}</span>
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
