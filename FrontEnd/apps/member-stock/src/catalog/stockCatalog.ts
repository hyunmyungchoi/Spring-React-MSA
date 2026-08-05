export type StockInstrument = {
    symbol: string;
    name: string;
    englishName: string;
    market: "KOSPI" | "KOSDAQ" | "NASDAQ" | "NYSE";
    aliases: string[];
};

export const STOCK_CATALOG: StockInstrument[] = [
    { symbol: "005930", name: "삼성전자", englishName: "Samsung Electronics", market: "KOSPI", aliases: ["삼전", "samsung"] },
    { symbol: "005935", name: "삼성전자우", englishName: "Samsung Electronics Preferred", market: "KOSPI", aliases: ["삼성전자(우)", "삼전우"] },
    { symbol: "000660", name: "SK하이닉스", englishName: "SK hynix", market: "KOSPI", aliases: ["하이닉스", "sk hynix"] },
    { symbol: "373220", name: "LG에너지솔루션", englishName: "LG Energy Solution", market: "KOSPI", aliases: ["엘지에너지솔루션", "lg엔솔"] },
    { symbol: "207940", name: "삼성바이오로직스", englishName: "Samsung Biologics", market: "KOSPI", aliases: ["삼바"] },
    { symbol: "005380", name: "현대차", englishName: "Hyundai Motor", market: "KOSPI", aliases: ["현대자동차", "hyundai"] },
    { symbol: "000270", name: "기아", englishName: "Kia", market: "KOSPI", aliases: ["기아차"] },
    { symbol: "035420", name: "NAVER", englishName: "NAVER", market: "KOSPI", aliases: ["네이버"] },
    { symbol: "035720", name: "카카오", englishName: "Kakao", market: "KOSPI", aliases: ["kakao"] },
    { symbol: "068270", name: "셀트리온", englishName: "Celltrion", market: "KOSPI", aliases: ["celltrion"] },
    { symbol: "051910", name: "LG화학", englishName: "LG Chem", market: "KOSPI", aliases: ["엘지화학"] },
    { symbol: "006400", name: "삼성SDI", englishName: "Samsung SDI", market: "KOSPI", aliases: ["삼성에스디아이"] },
    { symbol: "AAPL", name: "애플", englishName: "Apple", market: "NASDAQ", aliases: ["아이폰", "apple inc"] },
    { symbol: "MSFT", name: "마이크로소프트", englishName: "Microsoft", market: "NASDAQ", aliases: ["마소", "microsoft"] },
    { symbol: "NVDA", name: "엔비디아", englishName: "NVIDIA", market: "NASDAQ", aliases: ["nvidia"] },
    { symbol: "GOOGL", name: "알파벳 A", englishName: "Alphabet Class A", market: "NASDAQ", aliases: ["구글", "google"] },
    { symbol: "AMZN", name: "아마존", englishName: "Amazon", market: "NASDAQ", aliases: ["amazon"] },
    { symbol: "TSLA", name: "테슬라", englishName: "Tesla", market: "NASDAQ", aliases: ["tesla"] },
    { symbol: "META", name: "메타", englishName: "Meta Platforms", market: "NASDAQ", aliases: ["페이스북", "facebook"] },
    { symbol: "TSM", name: "TSMC ADR", englishName: "Taiwan Semiconductor", market: "NYSE", aliases: ["tsmc", "대만반도체", "타이완반도체"] },
    { symbol: "AVGO", name: "브로드컴", englishName: "Broadcom", market: "NASDAQ", aliases: ["broadcom"] },
    { symbol: "AMD", name: "AMD", englishName: "Advanced Micro Devices", market: "NASDAQ", aliases: ["에이엠디"] },
    { symbol: "NFLX", name: "넷플릭스", englishName: "Netflix", market: "NASDAQ", aliases: ["netflix"] },
];

export function searchStockCatalog(query: string, limit = 8) {
    const normalized = normalize(query);
    if (!normalized) return STOCK_CATALOG.slice(0, limit);

    return STOCK_CATALOG
        .map((instrument) => ({ instrument, score: matchScore(instrument, normalized) }))
        .filter((entry) => entry.score < Number.POSITIVE_INFINITY)
        .sort((left, right) => left.score - right.score || left.instrument.name.localeCompare(right.instrument.name, "ko"))
        .slice(0, limit)
        .map((entry) => entry.instrument);
}

export function resolveStockSymbol(query: string) {
    const normalized = normalize(query);
    const exact = STOCK_CATALOG.find((instrument) =>
        searchableValues(instrument).some((value) => normalize(value) === normalized)
    );
    return exact?.symbol ?? query.trim().toUpperCase();
}

export function findStockInstrument(symbol: string) {
    return STOCK_CATALOG.find((instrument) => instrument.symbol === symbol.toUpperCase());
}

function matchScore(instrument: StockInstrument, query: string) {
    const values = searchableValues(instrument).map(normalize);
    if (values.some((value) => value === query)) return 0;
    if (values.some((value) => value.startsWith(query))) return 1;
    if (values.some((value) => value.includes(query))) return 2;
    return Number.POSITIVE_INFINITY;
}

function searchableValues(instrument: StockInstrument) {
    return [instrument.symbol, instrument.name, instrument.englishName, ...instrument.aliases];
}

function normalize(value: string) {
    return value.trim().toLocaleLowerCase("ko-KR").replace(/[\s()_-]/g, "");
}
