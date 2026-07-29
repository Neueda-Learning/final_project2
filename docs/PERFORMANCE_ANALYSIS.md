# Portfolio Manager 端到端性能分析报告

> 审计日期：2026-07-29（Asia/Shanghai）  
> 审计范围：数据库、索引与 SQL、Java 数据结构/算法、Spring Boot/JPA API、行情 worker、React/TanStack Query 前端  
> 核心问题：页面首次打开耗时长；切换投资组合需要数秒；持仓和交易页面尤其慢

## 1. 结论摘要

当前性能问题已经复现，而且主因不是数据量大、CPU 不足、前端表格渲染复杂，也不是 MySQL 执行 SQL 本身慢。

根因是：

1. API 和 worker 访问一个远端、多人共享的 MySQL。数据库服务器执行一条业务 SQL 通常只需要 `0.08–18.6 ms`，但客户端每多发送一条数据库命令，就会增加数百毫秒的等待。
2. Spring 的只读事务在当前 Connector/J 默认配置下，除业务 SQL 外还会发送 `SET autocommit`、`SET SESSION TRANSACTION READ ONLY/WRITE`、`COMMIT` 等约 5 条额外命令。一个只读取单条 portfolio 的接口因此也需要约 `2.3 s`。
3. 持仓和交易查询存在明确的 JPA N+1：8 个持仓会触发约 8 次额外 instrument 查询，10 条交易涉及 8 个 instrument 时也会逐个读取。结果是持仓接口 p50 `5.27 s`、交易接口 p50 `5.67 s`。
4. 仪表盘切换同时请求 dashboard 和 performance。两者各自重复校验 portfolio，并各自开启只读事务。切换关键路径实测 p50 `3.11 s`、p95 `3.51 s`。
5. 首次访问还存在“先取 portfolio 列表，再确定默认 portfolio，再取页面数据”的网络瀑布。首次仪表盘 p50 `6.07 s`；持仓页 p50 `9.32 s`；交易页 p50 `10.44 s`。
6. 行情 worker 把 819 个日价格和 481 个历史快照逐行写入远端数据库。一次成功同步约发出 1,446 条数据库命令，并因供应商限流至少等待 304 秒。模型预测约 882 秒，与最近一次实测 `877 s` 基本完全吻合。

因此，优化顺序必须是：

1. 先修复数据库部署/访问路径和数据库命令往返成本。
2. 消灭 N+1，减少事务控制命令，合并页面读取。
3. 批量写入 worker 数据，降低空闲轮询频率。
4. 最后再做前端预取、保留旧数据、路由拆包等体验优化。

如果只优化 React 渲染或增加 skeleton，而不减少数据库往返，页面仍然会慢 3–10 秒。

## 2. 测试边界与环境

### 2.1 运行环境

| 项目 | 实测值 |
|---|---|
| 前端 | React 19.2.8、TanStack Query 5.101.4、Vite 7.3.6，开发服务器 `localhost:5173` |
| API | Java 21、Spring Boot 4.1.0、Hibernate 7.4.1、Tomcat 11，`localhost:8000` |
| worker | 独立 Spring 进程 |
| 数据库 | MySQL 8.4.10，API/worker 通过远端地址访问 |
| API Hikari 池 | min=10、max=10 |
| 当前数据 | 24 个活跃 portfolio、55 个 position、58 条 transaction、43 个 instrument |
| 行情数据 | 819 条日价格、43,625 条分钟 bar、481 条 valuation snapshot |
| 最大示例组合 | 8 个 position、10 条 transaction、21 个 snapshot |

数据库规模很小。任何 2–10 秒的耗时都不能归因于“结果集太大”。

### 2.2 测试方法

- API 单接口：先预热 1 次，再测 7 次；报告 p50、线性插值 p95、响应体大小。
- CRUD：创建、读取、更新、归档、删除 5 组临时空 portfolio；每组均删除完成，无残留。
- portfolio 切换：dashboard 和 performance 同时发起，测 10 个 portfolio 的整体墙钟时间。
- 首次页面请求链：按前端真实依赖顺序模拟 portfolio 列表和页面查询，测 5 次。
- 并发：1、5、10、20 并发读取关键接口；没有触发写入。
- SQL：对实际 portfolio/instrument 执行 `EXPLAIN ANALYZE`，同时检查 `performance_schema` statement digest。
- 运行时：读取 Spring Actuator、Hikari、JVM、进程指标。
- worker：使用真实已完成同步记录、worker 日志和代码级数据库命令计数。
- 前端：执行生产构建，统计 raw/gzip 体积；检查 query key、依赖瀑布、缓存和渲染规模。

### 2.3 未执行的有副作用测试

- 未触发新的行情同步，避免消耗 Twelve Data 配额和产生约 15 分钟的真实任务。
- 未创建一笔新的不可删除交易。交易写入仅测试了既有 idempotency key 的无副作用重放。
- 未在共享数据库中批量造数。大数据量测试方案见第 11 节。
- 内置浏览器会话不可用，因此本次没有伪造 LCP、INP、CLS、主线程或浏览器截图数据。浏览器补测步骤和验收值见第 11.4 节。

审计期间工作区出现了其他并行前端改动。API 实测针对正在运行的版本；前端静态分析和最终构建针对报告生成时的最新工作区。审计没有修改这些并行文件。

## 3. 最重要的实测结果

### 3.1 页面与交互

| 功能场景 | 样本 | p50 | p95 | 判断 |
|---|---:|---:|---:|---|
| 首次打开仪表盘，无 portfolioId | 5 | 6.07 s | 7.80 s | 严重 |
| 首次打开持仓页，无 portfolioId | 5 | 9.32 s | 10.86 s | 阻断级 |
| 首次打开交易页，无 portfolioId | 5 | 10.44 s | 12.42 s | 阻断级 |
| 仪表盘切换 portfolio | 10 | 3.11 s | 3.51 s | 严重 |
| 持仓页切换 portfolio | 7 个接口样本 | 5.27 s | 6.05 s | 阻断级 |
| 交易页切换 portfolio | 7 个接口样本 | 5.67 s | 5.93 s | 阻断级 |
| portfolio 管理页列表 | 7 | 2.67 s | 3.10 s | 严重 |
| 数据状态页 latest sync | 7 | 0.34 s | 0.94 s | 尚可但轮询成本高 |

首次页面的瀑布如下：

```text
无 portfolioId
    │
    ├─ GET /portfolios ─────────────── 约 2.7 s
    │
    └─ 前端 effect 选择第一个 portfolio
            │
            ├─ dashboard + performance ── 约 3.1 s
            ├─ positions ──────────────── 约 5.3 s
            └─ transactions ───────────── 约 5.7 s
```

这解释了为什么“已有 portfolioId 的切换”是 3–6 秒，而“从空 URL 第一次打开”达到 6–12 秒。

### 3.2 API 单接口

| API/功能 | p50 | p95 | 最大响应体 | 主要瓶颈 |
|---|---:|---:|---:|---|
| `GET /health/live` | 3.7 ms | 4.4 ms | 15 B | 无数据库，正常 |
| `GET /health/ready` | 682.8 ms | 966.1 ms | 42 B | 仅 `SELECT 1` 也很慢，直接证明 DB 往返问题 |
| portfolio 列表，pageSize=20 | 2.67 s | 3.10 s | 5.0 KB | 读事务控制命令 + data/count 两条 SQL |
| portfolio 列表，pageSize=100 | 2.33 s | 3.66 s | 6.0 KB | 数据条数不是主因 |
| portfolio 详情 | 2.30 s | 3.01 s | 249 B | 一条实体查询 + 事务控制往返 |
| 创建 portfolio | 3.12 s | 3.91 s | 小 | user、重名校验、insert + 事务控制 |
| 更新 portfolio | 2.33 s | 2.73 s | 小 | 查询、重名校验、update + 事务控制 |
| 归档 portfolio | 1.97 s | 2.34 s | 小 | 查询、update + 事务控制 |
| 删除空 portfolio | 2.32 s | 3.28 s | 0 B | 查询、交易存在性检查、delete + 事务控制 |
| dashboard，8 个 position | 2.75 s | 3.48 s | 5.6 KB | 3 条业务 SQL + 事务控制；SQL 本身只有毫秒级 |
| dashboard，典型 3 个 position | 2.94 s | 3.79 s | 2.4 KB | 比大组合不快，证明不是结果集大小 |
| performance，21 个 point | 2.70 s | 3.99 s | 5.3 KB | 重复 portfolio 校验 + snapshot 查询 + 事务控制 |
| positions，8 个 position | 5.27 s | 6.05 s | 2.2 KB | 明确 N+1 |
| transactions，10 条 | 5.67 s | 5.93 s | 3.8 KB | 明确 N+1 + page count |
| instrument 列表，43 条 | 342.4 ms | 668.1 ms | 7.4 KB | 单 SQL；当前可接受 |
| instrument 搜索 | 342.9 ms | 569.3 ms | 1.7 KB | 当前仅 43 行；未来会全表扫描 |
| latest sync | 341.6 ms | 937.5 ms | 293 B | 单 SQL，远端往返 |
| latest price | 971.0 ms | 1.41 s | 281 B | `requireInstrument` + price 两次往返 |
| tradable prices，60 条上限 | 979.9 ms | 1.01 s | 5.9 KB | `requireInstrument` + history 两次往返 |
| bars 冷缓存，200 条 page | 2.08 s | 2.87 s | 53.5 KB | instrument 校验 + count + data 三次往返 |
| bars 热缓存 | 29.3 ms | 31.4 ms | 53.5 KB | Caffeine 命中后仅 JSON 序列化，效果明显 |
| 交易 idempotent 重放 | 2.33 s | 3.12 s | 小 | 3 次实体/幂等检查 + 事务控制；无新写入 |

portfolio CRUD 的测试数据全部清理完成。

### 3.3 并发

| 接口 | 并发 | p50 | p95 | 吞吐 | 结果 |
|---|---:|---:|---:|---:|---|
| dashboard | 1 | 3.81 s | 3.81 s | 0.26 req/s | 200 |
| dashboard | 5 | 3.89 s | 4.06 s | 1.22 req/s | 全部 200 |
| dashboard | 10 | 3.01 s | 4.09 s | 2.36 req/s | 全部 200 |
| dashboard | 20 | 4.94 s | 6.93 s | 2.70 req/s | 全部 200；已出现 Hikari 排队效应 |
| positions | 1 | 6.50 s | 6.50 s | 0.15 req/s | 200 |
| positions | 5 | 5.67 s | 6.67 s | 0.73 req/s | 全部 200 |
| positions | 10 | 5.35 s | 6.36 s | 1.57 req/s | 全部 200 |

CPU、GC 和线程不是当前瓶颈：

- API 进程 CPU 使用率约 4.3%，系统 CPU 约 25.8%。
- JVM GC pause 总计仅 38 ms。
- Hikari 没有 connection timeout。
- API Hikari 最大连接数为 10；20 并发时 p95 从约 4.1 秒升至 6.9 秒。

## 4. 数据库层分析

### 4.1 SQL 服务器执行很快，客户端等待很慢

代表性 `EXPLAIN ANALYZE`：

| SQL | 实际数据库执行时间 |
|---|---:|
| performance 读取 21 个 snapshot | 0.077 ms |
| position + instrument join，8 行 | 0.086 ms |
| transaction + instrument join，10 行 | 0.203 ms |
| latest price anti-join，21 个候选 price | 0.335 ms |
| worker 读取 168 个 daily close | 0.765 ms |
| bars count，1,170 行 | 1.85 ms |
| bars page，500 行 | 12.2 ms |
| dashboard positions 当前复杂 view | 18.6 ms |

`performance_schema` 中 dashboard summary 的平均数据库执行时间约 `6.83 ms`，dashboard positions 约 `18.32 ms`。

但是：

- `health/ready` 只执行 `SELECT 1`，p50 仍为 `683 ms`。
- portfolio 详情只有一个业务 SELECT，p50 仍为 `2.30 s`。
- MySQL CLI 新建连接后执行 1 条 `SELECT 1` 约 `2.05 s`。
- 同一连接连续执行 20 条简单 SELECT 总计约 `8.74–10.51 s`，扣除建连后，每多一条命令约增加数百毫秒。

结论：当前最昂贵的是协议命令的往返，不是数据库算 SQL 的时间。

### 4.2 事务控制命令被高延迟放大

对单次接口调用前后读取 `performance_schema` 计数：

| 接口 | `SET autocommit` | READ ONLY | READ WRITE | COMMIT |
|---|---:|---:|---:|---:|
| portfolio 详情 | +2 | +1 | +1 | +1 |
| dashboard | +2 | +1 | +1 | +1 |
| positions | +2 | +1 | +1 | +1 |

也就是说，使用 `@Transactional(readOnly = true)` 的一次普通读取，除了业务 SQL，至少还可能产生约 5 次状态切换/提交往返。

这与 MySQL Connector/J 默认行为一致：

- `useLocalSessionState` 默认 false；
- `useLocalTransactionState` 默认 false；
- `elideSetAutoCommits` 默认 false；
- `readOnlyPropagatesToServer` 默认 true，并明确会为了设置只读状态增加一次往返。

官方说明：[MySQL Connector/J Performance Extensions](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-performance-extensions.html)。

这些选项不能盲目开启；应在集成测试确认事务、自动提交和只读保护行为后调整。但在当前高往返环境下，它们有很高的收益潜力。

### 4.3 连接池和共享数据库

API Actuator 累计指标：

| 指标 | 值 |
|---|---:|
| Hikari acquire | 516 次，平均约 273 ms |
| Hikari usage | 516 次，平均约 1.545 s，最大 8.591 s |
| Hikari connection creation | 76 次，平均约 3.06 s |
| API pool | active 1、idle 9、min 10、max 10 |

数据库检查时出现：

- 85 个 schema 连接，最大连接数 151；
- 84 个是 Connector/J 连接；
- 84 个处于 Sleep，只有 1 个非空闲；
- `Threads_running=2`，没有 slow query，没有 deadlock/lock timeout；
- buffer pool 仍有大量空闲页。

这说明多个 API/worker 进程使用相同共享数据库账号和默认 Hikari 最小空闲数，保留了大量空闲连接。连接数不是这次单用户变慢的唯一原因，但它会很快消耗共享实例容量，也使并发测试不稳定。

### 4.4 索引与视图

当前关键索引基本覆盖了小数据集：

- `portfolio_position` 主键 `(portfolio_id, instrument_id)`；
- transaction `(portfolio_id, executed_at DESC)`；
- snapshot `(portfolio_id, valuation_date)`；
- price `(instrument_id, price_date DESC, fetched_at DESC)`；
- intraday `(instrument_id, interval_code, bar_timestamp DESC)`。

当前需要关注但不是第一优先级的问题：

1. `instrument` 搜索使用 `UPPER(column) LIKE '%query%'`，`EXPLAIN ANALYZE` 已显示全表扫描。43 行时只需 `0.13 ms`，达到数万 instrument 时会退化。应考虑规范化搜索列、前缀搜索、全文索引或独立搜索服务。
2. `latest_market_price` 使用 correlated `NOT EXISTS` anti-join。当前每个 instrument 约 21 个日价格，仍很快；多年数据增长后成本会升高。
3. dashboard 的 `portfolio_allocation` 包含 window aggregate。当前执行计划会物化所有 55 个 position，再筛选目标 portfolio，而不是只处理目标的 8 个 position。
4. dashboard positions 同时引用 `position_metrics` 和 `portfolio_allocation`，相当于重复计算 latest price 逻辑。
5. snapshot 上的 unique `(portfolio_id, valuation_date)` 与相同列的普通降序索引高度重复。MySQL 可反向扫描 B-tree，应在确认执行计划后移除冗余索引，降低 worker 写入成本。

## 5. Java 数据结构与算法分析

### 5.1 positions N+1

代码路径：

- [TradingService.java](../backend/src/main/java/com/portfoliomanager/service/TradingService.java) 先执行 `existsById`；
- `findByPortfolioId` 只查询 `PortfolioPosition`；
- `PortfolioPosition.instrument` 是 LAZY；
- `toPositionResponse` 对每一行访问 instrument 的 symbol、name、asset type。

8 个 position 的估算数据库命令：

```text
1  portfolio exists
1  position list
8  lazy instrument loads
5  read-only transaction state/commit
---
15 database commands
```

按当前每次数据库命令数百毫秒计算，得到约 5–6 秒，与实测 `5.27 s` 一致。

修复方式：

- repository 用 `JOIN FETCH p.instrument`；
- 或 `@EntityGraph(attributePaths = "instrument")`；
- 更推荐直接返回只含响应字段的 DTO projection，避免实体和 lazy proxy。

Spring Data 官方支持 fetch graph 和 DTO projection：

- [Spring Data JPA EntityGraph](https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/query-methods.html#jpa.entity-graph)
- [Spring Data JPA Projections](https://docs.spring.io/spring-data/data-jpa/reference/3.5/repositories/projections.html)

### 5.2 transactions N+1

交易页执行：

```text
1  portfolio exists
1  transaction page data
1  transaction count
8  unique lazy instrument loads
5  read-only transaction state/commit
---
约 16 database commands
```

10 条 transaction 只有 8 个不同 instrument，因此 persistence context 会复用同一个 instrument；但仍然产生 8 次额外查询。

修复方式：

- page query 直接 join instrument 并返回 DTO；
- count query 保持独立；
- 不要用 fetch collection；这里是 many-to-one，join fetch 不会破坏分页；
- portfolio 不存在的语义可在 page 为空时再检查，或把 ownership 条件合并进主查询，避免每次都先 `existsById`。

### 5.3 dashboard 的 Java 计算

`positions.stream().filter().map().toList()` 是 O(P)，当前 P=8，完全不是瓶颈。

allocation 已经可以在拿到 position market value 后用一次 O(P) 求和和一次 O(P) 映射完成。没有必要让 SQL 的 `portfolio_allocation` 再物化所有 portfolio 的 position。将 allocation 在 Java 中计算反而会让 SQL 更简单、范围更小。

### 5.4 worker 算法

worker 中以下数据结构选择是合理的：

- symbol 到 target 使用 `HashMap`，O(1) 查找；
- successful instrument 使用 `HashSet`；
- 按日期使用 `TreeMap`，保证快照按时间重放；
- position ledger 使用 `HashMap`。

CPU 算法不是 worker 的主要瓶颈。真正的问题是算法把每个计算结果变成一次单独的远端数据库写入。

## 6. 后端逐功能瓶颈

### 6.1 Portfolio 管理

| 功能 | 实测 | 瓶颈 | 优先级 |
|---|---:|---|---|
| 列表 | p50 2.67 s | Page data + count；服务层只读事务额外状态命令 | P0 |
| 详情 | p50 2.30 s | 一条 SQL 被事务状态命令放大 | P0 |
| 创建 | p50 3.12 s | user、重名、insert 串行 | P1 |
| 更新 | p50 2.33 s | find、重名、update 串行 | P1 |
| 归档 | p50 1.97 s | find + update | P1 |
| 删除 | p50 2.32 s | find + trade exists + delete | P1 |

特别问题：前端 `api.portfolios.list(false)` 没有传 `pageSize`，后端默认只返回 20 条，但数据库有 24 个活跃 portfolio。4 个 portfolio 不会出现在全局 selector 中。这是功能正确性问题，也会让“列表即 selector 数据源”的设计无法扩展。

建议提供单独的轻量 `GET /portfolio-options`：

- 单 SQL；
- 只返回 id、name、currency；
- 不要 count；
- 缓存 30–60 秒，create/update/archive 后精确失效。

### 6.2 Dashboard

一次 dashboard：

1. JPA 查 portfolio + owner；
2. JDBC 查 `portfolio_summary`；
3. JDBC 查 positions + allocation；
4. 事务状态/提交命令。

performance 又独立重复 portfolio + owner，并读取 snapshot。

建议：

- ownership 条件直接进入 JDBC SQL，移除单独 JPA ownedPortfolio；
- dashboard 与 performance 合并为一个页面 API，或至少共享一次 portfolio 校验；
- positions SQL 只处理目标 portfolio；
- allocation 在 Java O(P) 计算；
- 交易/同步完成后精确失效；其余时间可短 TTL 缓存。

目标：portfolio 切换最多 1 个 HTTP 请求、最多 2 条数据库 SQL，不发送无必要的 transaction state 命令。

### 6.3 Holdings / 交易录入

读 position 是当前最严重的在线接口之一，详见 N+1。

交易写入的业务计算是少量 `BigDecimal` 运算，不是 CPU 问题。完整写入路径串行执行：

1. portfolio；
2. instrument；
3. idempotency；
4. position `FOR UPDATE`；
5. transaction insert；
6. position insert/update/delete；
7. transaction state/commit。

本次无副作用 idempotent replay p50 `2.33 s`。真实新交易还会多一次锁查询和两次写入，预计会继续增加约 1 秒以上。

建议：

- ownership 和 active 状态合并到查询；
- idempotency 尽量尽早判断，但必须保证跨 portfolio 安全；
- 保留 position 行锁和同一事务，不能为了性能破坏一致性；
- 通过降低数据库 RTT，而不是移除必要锁来优化写路径。

### 6.4 Transactions

后端已经正确分页，数据库索引也覆盖排序。慢的原因是 N+1，不是分页。

前端又对后端已经排序的 20 条结果执行一次 `sort`。当前成本可以忽略，但这是重复工作，应删除以简化渲染。

### 6.5 Instrument

43 条 instrument 下列表和搜索约 0.34 秒，主要就是一次远端 SQL 往返。

未来规模风险是 substring + `UPPER` 全表扫描。建议先定义产品搜索语义：

- symbol 使用规范化前缀；
- name 需要任意子串时使用 FULLTEXT/ngram 或独立搜索；
- 不要仅靠普通 B-tree 期待 `%query%` 命中索引。

### 6.6 Market Data API

`latestPrice` 和 `tradablePrices` 每次先 `COUNT(*)` 验证 instrument，再执行真实查询。可以通过主查询为空后区分 404，或 join instrument 后一次完成。

bars：

- 冷缓存 p50 2.08 秒；
- 热缓存 p50 29.3 毫秒；
- 当前 Caffeine 20 秒和 private HTTP cache 15 秒有效；
- 冷缓存仍有 instrument、count、data 三次往返；
- offset pagination 允许 page 到 100,000，深页会退化，应改成 `(bar_timestamp, id)` keyset/cursor。

## 7. worker 性能分析

### 7.1 实测同步时长

最近三次成功的 39 instrument 同步：

| 开始 | 类型 | 时长 |
|---|---|---:|
| 2026-07-29 11:02 | MANUAL | 877 s |
| 2026-07-29 10:00 | SCHEDULE | 851 s |
| 2026-07-28 15:57 | MANUAL | 838 s |

也就是一次同步约 `14.0–14.6 分钟`。

### 7.2 命令级时间模型

当前一次成功同步包含：

| 工作 | 数据量/命令 |
|---|---:|
| instrument | 39 |
| 日价格 upsert | 819 次单行 `jdbc.update` |
| 每批进度 update | 39 |
| 当前 valuation | 24 个 portfolio × 2 条 SQL = 48 |
| 历史 trade/close load | 24 × 2 = 48 |
| 历史 snapshot upsert | 481 次单行 `jdbc.update` |
| lock、run、stage、affected portfolio 等 | 约 11 |
| 数据库命令合计 | 约 1,446 |

Twelve Data 当前配置每个 symbol 一次请求，两个请求之间等待 8 秒：

```text
38 个间隔 × 8 秒 = 304 秒
```

数据库命令按每条约 0.4 秒：

```text
1,446 × 0.4 秒 + 304 秒 ≈ 882 秒
```

实际为 877 秒。说明模型与真实运行高度吻合，也证明逐行远端写入是确定性瓶颈。

### 7.3 worker 修复

P0：

1. 日价格收集后使用 `batchUpdate`，并验证 Connector/J `rewriteBatchedStatements=true`；或像 intraday 实现一样构造 100–200 行 multi-value upsert。
2. snapshot 不要逐日 `jdbc.update`；把 481 行按 100–200 行分块批量 upsert。
3. 当前 valuation 也可批量查询/批量 upsert。
4. 最低成本版本应把约 1,446 条命令降到 20–30 条以内。供应商仍然可以保持单 symbol 请求，不改变配额策略。

P1：

- 一次查询加载所有 affected portfolio 的 trade；
- 一次查询加载所有相关 close；
- 在内存中按 portfolio/instrument 分组重放；
- 或进一步改为 set-based SQL/临时表。

注意：之前的同步记录已经出现 Twelve Data 429。数据库批处理与供应商请求批处理是两件事。应批量写数据库，但不能在未确认供应商响应完整性和配额规则前恢复多 symbol 行情请求。

### 7.4 空闲轮询

manual worker 每 2 秒执行：

1. `GET_LOCK`；
2. 查询 pending manual run；
3. `RELEASE_LOCK`。

没有任务时也会持续产生约 1.5 条数据库命令/秒，并在高 RTT 环境下长期占用连接。

建议：

- 先用一条轻量 SQL 检查 pending，再在确有任务时取锁；
- 空闲间隔调到 10–30 秒；
- 更好的是用消息队列/数据库通知；
- 若继续使用数据库队列，用原子 claim 或 `FOR UPDATE SKIP LOCKED`，避免每 2 秒全局 advisory lock。

### 7.5 Intraday

`fetchIntradayWithRetry` 和 `upsertIntradayBars` 当前没有被任何 scheduled/manual 流程调用；前端 `IntradayChart` 也没有页面引用。

数据库中 AAPL 的分钟数据最新到 `2026-07-27 19:59 UTC`，审计日期为 2026-07-29。此功能目前属于“代码存在、在线数据刷新链未接通”，不能把已有 43,625 行当作持续可用。

## 8. 前端性能分析

### 8.1 请求与缓存

全局 QueryClient：

- `staleTime=30s`；
- retry=1；
- query key 按 portfolioId 分隔。

效果：

- 30 秒内切回已打开过的 portfolio 可以直接命中缓存；
- 首次打开一个 portfolio 仍然等待完整 API；
- dashboard 和 performance 并行，方向正确；
- 没有 prefetch，也没有保留上一个 portfolio 的页面数据；
- 同步完成后会 invalidate dashboard/performance，方向正确。

TanStack Query 官方建议用 prefetch 避免请求瀑布，并可用 `placeholderData`/`keepPreviousData` 在 key 改变时保留之前数据：

- [Prefetching & Router Integration](https://tanstack.com/query/latest/docs/framework/react/guides/prefetching)
- [Paginated/Lagged Queries](https://tanstack.com/query/latest/docs/framework/react/guides/paginated-queries)

本项目如果保留上一个 portfolio 的数据，必须：

- 明确显示“正在切换到 X”；
- 对旧数据加遮罩/降低透明度；
- 不允许旧 portfolio 名称与新数据混淆；
- 新请求失败时明确恢复或提示。

这只改善感知性能，不会降低后端 3–6 秒耗时。

### 8.2 portfolio selector

当前 selector 依赖完整 portfolio page，且默认只取 20 条。应改成：

- 轻量 option API；
- 或至少传 `pageSize=100`；
- 保存最近选择的 portfolioId 到 URL 和 localStorage；
- 第一次打开时不必等待列表后再选择默认值；
- 可在 selector focus/hover 时预取候选 portfolio 的 dashboard，但不要一次预取全部 24 个。

### 8.3 bundle

最新生产构建：

| 文件 | raw | gzip |
|---|---:|---:|
| JavaScript 单 chunk | 470.34 KB | 152.32 KB |
| CSS | 21.69 KB | 5.14 KB |
| HTML | 0.49 KB | 0.31 KB |

所有页面和 Chart.js 都在一个入口 chunk。即使用户只打开 portfolio 管理页，也必须下载、解析 chart 相关代码。

建议：

- `React.lazy` + route-level dynamic import；
- dashboard/charts 独立 chunk；
- holdings、transactions、data status 独立 chunk；
- 生产构建设置明确的长期缓存和内容 hash。

Vite 会为 dynamic import 做 code split 和预加载优化，官方说明：[Vite Features / Async Chunk Loading](https://vite.dev/guide/features.html#async-chunk-loading-optimization)。

### 8.4 渲染复杂度

当前最大页面数据：

- dashboard 8 行；
- transaction 20 行/page；
- performance 21 点；
- allocation 8 个扇区；
- bars 200 点/page。

这些规模不需要虚拟列表。React render、数组 `map`、`useMemo` 不是数秒级主因。

达到以下规模后再考虑虚拟化：

- table 可见行 > 200；
- chart point > 2,000；
- 一次 bars page > 1,000。

## 9. 优化优先级与预期收益

### P0：立即处理

#### P0-1 数据库部署与链路

- 本地开发优先使用 compose 中的本地 MySQL。
- 部署环境把 API、worker、MySQL 放在同地域/同 VPC/同可用区。
- 检查当前远端 MySQL 前面的代理、限速、共享账号和连接策略。
- 为每个环境使用独立数据库账号和明确连接上限。

验收：

- 已建立的持久连接 `SELECT 1` p95 < 20 ms；
- `/health/ready` p95 < 50 ms；
- Hikari acquire p95 < 10 ms；
- connection creation p95 < 500 ms。

#### P0-2 消灭 positions/transactions N+1

- DTO projection 或 join fetch instrument；
- 删除不必要的 exists 查询，或仅在空结果时补查；
- 增加集成测试，断言 SQL 数量不随返回行数增长。

验收：

- positions：业务 SELECT ≤ 2，最好 1；
- transactions：含 count 的业务 SELECT ≤ 3；
- 1、10、100 行时 SQL 数量保持常数；
- 当前数据下两者 p95 < 500 ms；同地域 DB 下目标 < 200 ms。

#### P0-3 减少读事务协议命令

优先顺序：

1. dashboard/performance 纯 JDBC/DTO 查询不要包在 JPA read-only transaction 中；
2. 单条读取避免服务层宽事务；
3. 集成验证后评估 Connector/J：
   - `useLocalSessionState=true`
   - `useLocalTransactionState=true`
   - `elideSetAutoCommits=true`
   - `readOnlyPropagatesToServer=false`

不能仅凭报告直接上线这些连接参数；必须验证 commit、rollback、read-only、连接归还后的状态重置。

#### P0-4 worker 批量写

- 819 price upsert 从 819 条命令降到约 5–9 个 chunk；
- 481 snapshot upsert 从 481 条命令降到约 3–5 个 chunk；
- worker 空闲轮询从每 2 秒 3 条命令降到每 10–30 秒最多 1 条。

验收：

- 每次成功同步的数据库命令 < 30；
- 数据库处理阶段 < 30 秒；
- 完整同步接近供应商限流下界，目标 < 6 分钟；
- 相同输入重复同步结果幂等。

### P1：随后处理

#### P1-1 合并页面读取

- 为 dashboard 页面提供单一 API，包括 summary、positions、allocation、performance；
- ownership 直接进入 SQL；
- allocation 在目标 portfolio 范围内计算。

验收：

- portfolio 切换只发 1 个页面请求；
- 切换 p95 < 800 ms；
- 不同 position 数量下数据库命令为常数。

#### P1-2 portfolio option 与默认值

- 轻量 options API；
- 修复当前只显示 20/24 portfolio；
- URL/localStorage 提前提供 portfolioId，消除首屏串行瀑布。

验收：

- 首次页面不再先等待 portfolio list 才发页面 API；
- 首次 dashboard API 请求在 HTML/JS 初始化后立即发起；
- 初始仪表盘 p95 < 1.5 s。

#### P1-3 polling

- idle latest sync 30–60 秒；
- RUNNING 才 3 秒；
- 页面隐藏时不轮询；
- 或改 SSE/WebSocket。

### P2：体验和规模优化

- route-level code splitting；
- dashboard/performance 短 TTL cache、ETag，trade/sync 后精确失效；
- transaction page 使用 `placeholderData`；
- bars 改 cursor pagination；
- instrument 搜索为大规模数据设计索引；
- 评估物化 latest instrument price，避免多年 history 上的 anti-join。

## 10. 优化后的性能预算

| 场景 | 当前 p95 | 第一阶段目标 | 最终目标 |
|---|---:|---:|---:|
| health ready | 966 ms | < 100 ms | < 50 ms |
| portfolio 列表 | 3.10 s | < 500 ms | < 200 ms |
| dashboard API | 3.48 s | < 800 ms | < 300 ms |
| performance API | 3.99 s | < 500 ms | < 200 ms |
| positions | 6.05 s | < 800 ms | < 300 ms |
| transactions | 5.93 s | < 800 ms | < 300 ms |
| portfolio 切换 | 3.51 s | < 1.2 s | < 800 ms |
| 首次 dashboard | 7.80 s | < 2.5 s | < 1.5 s |
| 首次 holdings | 10.86 s | < 2.5 s | < 1.5 s |
| 完整行情同步 | 877 s | < 420 s | < 360 s |
| dashboard 20 并发 | 6.93 s | < 2.5 s | < 1.5 s |

浏览器最终预算：

- LCP p75 < 2.5 s；
- INP p75 < 200 ms；
- CLS p75 < 0.1；
- portfolio 切换从 change event 到新 portfolio 数据可交互 p95 < 800 ms；
- 初始入口 JS gzip < 100 KB，chart 代码不进入非 dashboard 首屏。

## 11. 详细复测方案

### 11.1 API

每个接口：

1. 冷启动 1 次，不计入；
2. 串行 30 次，记录 min/p50/p95/p99/max；
3. 5、10、20 并发各 3 个 wave；
4. 同时记录 Hikari active/pending/acquire、HTTP request timer；
5. 记录返回行数和响应字节，禁止只报平均值。

必须覆盖：

- portfolio list/get/create/update/archive/delete；
- dashboard/performance；
- positions；
- transaction page 1/page 5，pageSize 20/100；
- transaction idempotent replay；
- transaction 新写入，在隔离数据库中 rollback/清理；
- instrument list、精确 symbol、前缀、子串、无结果；
- latest/tradable prices；
- bars 冷/热缓存、pageSize 50/200/500、深页；
- latest sync、manual sync enqueue；
- live/ready。

### 11.2 数据库规模

必须使用隔离数据库，不要在共享演示库造数。

| 规模 | Portfolio | Position/portfolio | Transaction/portfolio | Price history | Snapshot |
|---|---:|---:|---:|---:|---:|
| S | 当前 24 | 2–8 | 2–10 | 1 个月 | 21 天 |
| M | 100 | 50 | 1,000 | 2 年 | 2 年 |
| L | 1,000 | 100 | 10,000 | 5 年 | 5 年 |

每个规模执行：

- `EXPLAIN ANALYZE`；
- rows examined/rows sent；
- temporary table/disk temporary table；
- buffer pool hit；
- lock wait/deadlock；
- snapshot 重建总命令数和时长。

特别断言：

- positions/transactions SQL 数量不随行数增长；
- dashboard allocation 只处理目标 portfolio；
- latest price 不随全表 history 线性恶化；
- instrument substring 搜索有明确性能上限或替代方案。

### 11.3 worker

在 fixture provider 下测：

| 场景 | Instrument | 日价格/只 | Portfolio | 目标 |
|---|---:|---:|---:|---|
| small | 10 | 21 | 10 | DB < 5 s |
| current | 39 | 21 | 24 | DB < 30 s |
| medium | 200 | 252 | 100 | DB < 2 min |

记录：

- provider 时间；
- rate-limit sleep；
- price validation；
- price batch write；
- current valuation；
- historical load；
- in-memory replay；
- snapshot batch write；
- 总 SQL 数；
- 每 chunk 失败后的幂等重试。

### 11.4 浏览器补测

本次缺少内置浏览器会话，后续必须在 Chrome DevTools 或自动化浏览器补测：

1. 生产构建，不使用 Vite dev server作为最终指标。
2. Desktop：无网络限速和 Fast 4G 各 10 次。
3. CPU 4× slowdown。
4. 禁用缓存测首次打开；启用缓存测二次访问。
5. 用 Performance/Network 记录：
   - TTFB、FCP、LCP、CLS、long task、JS parse/evaluate；
   - portfolio selector change 时间；
   - dashboard/performance 请求开始、结束；
   - chart commit；
   - React commit 次数；
   - 切换期间是否显示错误 portfolio 的旧数据。
6. 分别测试 dashboard、portfolio、holdings、transactions、data status。
7. 在 1、20、100 个 table row 下重复。

不能只跑 Lighthouse 总分。必须保存 HAR、trace 和每个交互的 p50/p95。

## 12. 推荐实施顺序

```text
同地域/本地 DB 与连接基线
    ↓
positions/transactions DTO 查询，消灭 N+1
    ↓
减少 read-only transaction 状态命令
    ↓
合并 dashboard + performance + ownership
    ↓
worker price/snapshot 批量 upsert
    ↓
降低 worker/latest-sync 空闲轮询
    ↓
portfolio option/default 解除首屏瀑布
    ↓
前端 route split、prefetch、placeholder
    ↓
浏览器与 M/L 数据规模复测
```

每一步都应重新运行第 11 节同一套测试。不要一次混合所有修改，否则无法确认收益来自哪里，也容易掩盖一致性回归。

