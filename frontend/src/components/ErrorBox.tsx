import { isApiError } from "../api/client";
import { useLanguage } from "../i18n/LanguageContext";

export function ErrorBox({
  error,
  onRetry,
  fallback,
}: {
  error: unknown;
  onRetry?: () => void;
  fallback?: string;
}) {
  const { t } = useLanguage();
  const message = fallback ?? t("common.requestFailed");
  if (!error) return null;

  if (isApiError(error)) {
    return (
      <div className="error-box" role="alert">
        <div className="error-box__msg">{error.message || message}</div>
        {error.requestId ? <div className="error-box__code">requestId: {error.requestId}</div> : null}
        {onRetry ? (
          <div>
            <button className="btn btn-secondary btn-sm" type="button" onClick={onRetry}>
              {t("common.retry")}
            </button>
          </div>
        ) : null}
      </div>
    );
  }

  return (
    <div className="error-box" role="alert">
      <div className="error-box__msg">{message}</div>
      {onRetry ? (
        <div>
          <button className="btn btn-secondary btn-sm" type="button" onClick={onRetry}>
            {t("common.retry")}
          </button>
        </div>
      ) : null}
    </div>
  );
}
