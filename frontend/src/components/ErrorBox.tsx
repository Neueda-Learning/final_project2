import { isApiError } from "../api/client";

export function ErrorBox({
  error,
  onRetry,
  fallback = "请求失败，请稍后重试。",
}: {
  error: unknown;
  onRetry?: () => void;
  fallback?: string;
}) {
  if (!error) return null;

  if (isApiError(error)) {
    return (
      <div className="error-box" role="alert">
        <div className="error-box__msg">{error.message || fallback}</div>
        {error.requestId ? <div className="error-box__code">requestId: {error.requestId}</div> : null}
        {onRetry ? (
          <div>
            <button className="btn btn-secondary btn-sm" type="button" onClick={onRetry}>
              重试
            </button>
          </div>
        ) : null}
      </div>
    );
  }

  return (
    <div className="error-box" role="alert">
      <div className="error-box__msg">{fallback}</div>
      {onRetry ? (
        <div>
          <button className="btn btn-secondary btn-sm" type="button" onClick={onRetry}>
            重试
          </button>
        </div>
      ) : null}
    </div>
  );
}
