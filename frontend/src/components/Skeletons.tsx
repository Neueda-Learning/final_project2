export function SummarySkeleton() {
  return (
    <div className="summary-grid" aria-hidden="true">
      {Array.from({ length: 4 }).map((_, i) => (
        <div className="skeleton skeleton-card" key={i} />
      ))}
    </div>
  );
}

export function TableSkeleton() {
  return (
    <div className="card" aria-hidden="true">
      {Array.from({ length: 6 }).map((_, i) => (
        <div className="skeleton skeleton-text" style={{ width: `${100 - i * 9}%` }} key={i} />
      ))}
    </div>
  );
}
