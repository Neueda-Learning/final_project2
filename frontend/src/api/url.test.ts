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

  it("normalizes a root URL with trailing slashes", () => {
    expect(resolveApiUrls("http://localhost:8000///")).toEqual({
      root: "http://localhost:8000",
      v1: "http://localhost:8000/api/v1",
    });
  });

  it("normalizes a versioned URL with extra trailing slashes", () => {
    expect(resolveApiUrls("http://localhost:8000/api/v1///")).toEqual({
      root: "http://localhost:8000",
      v1: "http://localhost:8000/api/v1",
    });
  });

  it("builds relative API URLs when given a relative base path", () => {
    expect(resolveApiUrls("/backend")).toEqual({
      root: "/backend",
      v1: "/backend/api/v1",
    });
  });

  it("treats an empty configured base as same-origin mode", () => {
    expect(resolveApiUrls("")).toEqual({
      root: "",
      v1: "/api/v1",
    });
  });
});
