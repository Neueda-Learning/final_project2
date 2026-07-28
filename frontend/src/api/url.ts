export function resolveApiUrls(configuredBase: string | undefined) {
  const configured = (configuredBase ?? "").replace(/\/+$/, "");
  const hasVersionPath = configured.endsWith("/api/v1");
  const root = hasVersionPath ? configured.slice(0, -"/api/v1".length) : configured;

  return {
    root,
    v1: hasVersionPath ? configured : `${root}/api/v1`,
  };
}
