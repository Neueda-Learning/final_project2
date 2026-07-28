import { describe, expect, it } from "vitest";

import { resolveApiUrls } from "./url";

describe("resolveApiUrls", () => {
  it("adds the API version path to a server origin", () => {
    expect(resolveApiUrls("http://localhost:8000")).toEqual({
      root: "http://localhost:8000",
      v1: "http://localhost:8000/api/v1",
    });
  });

  it("does not duplicate a configured API version path", () => {
    expect(resolveApiUrls("http://localhost:8000/api/v1/")).toEqual({
      root: "http://localhost:8000",
      v1: "http://localhost:8000/api/v1",
    });
  });

  it("supports same-origin Vite proxy mode", () => {
    expect(resolveApiUrls(undefined)).toEqual({
      root: "",
      v1: "/api/v1",
    });
  });
});
