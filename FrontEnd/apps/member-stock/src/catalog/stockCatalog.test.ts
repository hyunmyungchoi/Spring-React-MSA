import { describe, expect, it } from "vitest";
import { resolveStockSymbol, searchStockCatalog } from "./stockCatalog";

describe("stockCatalog", () => {
    it.each([
        ["삼성전자", "005930"],
        ["삼성전자(우)", "005935"],
        ["애플", "AAPL"],
        ["Apple", "AAPL"],
        ["TSMC", "TSM"],
    ])("resolves %s to %s", (query, symbol) => {
        expect(resolveStockSymbol(query)).toBe(symbol);
    });

    it("returns common and preferred shares for a Samsung search", () => {
        expect(searchStockCatalog("삼성전자").map((item) => item.symbol))
            .toEqual(expect.arrayContaining(["005930", "005935"]));
    });
});
