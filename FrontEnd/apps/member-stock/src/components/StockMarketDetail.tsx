import { findStockInstrument } from "../catalog/stockCatalog";
import type { Candle, MarketQuote, StockSummary } from "../types/marketData";
import { calculateMarketChange, chronologicalCloses } from "./marketMath";
import StockDataStatus from "./StockDataStatus";

type StockMarketDetailProps = {
    quote?: MarketQuote;
    stock?: StockSummary;
    candles: Candle[];
    loading: boolean;
    error: string | null;
};

const CHART_WIDTH = 760;
const CHART_HEIGHT = 270;
const CHART_PADDING = 18;

function StockMarketDetail({ quote, stock, candles, loading, error }: StockMarketDetailProps) {
    if (!quote) return <div className="stock-detail stock-empty">선택한 종목의 시세가 없습니다.</div>;

    const catalog = findStockInstrument(quote.symbol);
    const change = calculateMarketChange(quote.lastPrice, candles);
    const direction = change?.direction ?? "flat";
    const latestCandle = [...candles].sort((left, right) => Date.parse(right.timestamp) - Date.parse(left.timestamp))[0];
    const chart = buildChart(candles);

    return (
        <article className={"stock-detail is-" + direction}>
            <header className="stock-detail-header">
                <div>
                    <span className="stock-market-label">{stock?.market ?? catalog?.market ?? "MARKET"}</span>
                    <h2>{catalog?.name ?? stock?.name ?? stock?.englishName ?? quote.symbol}</h2>
                    <p>{quote.symbol} · {stock?.englishName ?? catalog?.englishName ?? ""}</p>
                </div>
                <StockDataStatus status={quote.dataStatus} fetchedAt={quote.fetchedAt} />
            </header>

            <div className="stock-detail-price">
                <strong>{formatPrice(quote.lastPrice, quote.currency)}</strong>
                <span>{quote.currency}</span>
            </div>
            <p className={"stock-change is-" + direction}>
                {change ? signedPrice(change.amount, quote.currency) + " (" + signedPercent(change.rate) + ")" : "등락 정보 계산 중"}
            </p>

            <div className="stock-chart" aria-label={quote.symbol + " 최근 30일 종가 차트"}>
                {loading && candles.length === 0 ? (
                    <div className="stock-chart-placeholder">차트를 불러오는 중입니다.</div>
                ) : chart ? (
                    <svg viewBox={"0 0 " + CHART_WIDTH + " " + CHART_HEIGHT} role="img">
                        <defs>
                            <linearGradient id="stock-chart-fill" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="0%" stopColor="currentColor" stopOpacity="0.22" />
                                <stop offset="100%" stopColor="currentColor" stopOpacity="0" />
                            </linearGradient>
                        </defs>
                        <path className="stock-chart-area" d={chart.area} />
                        <path className="stock-chart-line" d={chart.line} />
                    </svg>
                ) : (
                    <div className="stock-chart-placeholder">표시할 차트 데이터가 없습니다.</div>
                )}
            </div>

            {error && <p className="stock-inline-error">{error}</p>}

            <dl className="stock-metrics">
                <div><dt>시가</dt><dd>{formatOptionalPrice(latestCandle?.openPrice, quote.currency)}</dd></div>
                <div><dt>고가</dt><dd>{formatOptionalPrice(latestCandle?.highPrice, quote.currency)}</dd></div>
                <div><dt>저가</dt><dd>{formatOptionalPrice(latestCandle?.lowPrice, quote.currency)}</dd></div>
                <div><dt>거래량</dt><dd>{formatVolume(latestCandle?.volume)}</dd></div>
            </dl>
        </article>
    );
}

function buildChart(candles: Candle[]) {
    const points = chronologicalCloses(candles);
    if (points.length < 2) return null;

    const values = points.map((point) => point.value);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = max - min || 1;
    const drawableWidth = CHART_WIDTH - CHART_PADDING * 2;
    const drawableHeight = CHART_HEIGHT - CHART_PADDING * 2;
    const coordinates = points.map((point, index) => ({
        x: CHART_PADDING + (drawableWidth * index) / (points.length - 1),
        y: CHART_PADDING + drawableHeight - ((point.value - min) / range) * drawableHeight,
    }));
    const line = coordinates
        .map((point, index) => (index === 0 ? "M" : "L") + point.x.toFixed(2) + "," + point.y.toFixed(2))
        .join(" ");
    const last = coordinates.at(-1)!;
    const area = line + " L" + last.x.toFixed(2) + "," + CHART_HEIGHT
        + " L" + coordinates[0].x.toFixed(2) + "," + CHART_HEIGHT + " Z";
    return { line, area };
}

function formatPrice(value: string | number, currency: string) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) return String(value);
    return new Intl.NumberFormat(currency === "KRW" ? "ko-KR" : "en-US", {
        maximumFractionDigits: currency === "KRW" ? 0 : 2,
    }).format(numeric);
}

function formatOptionalPrice(value: string | undefined, currency: string) {
    return value ? formatPrice(value, currency) : "-";
}

function formatVolume(value: string | undefined) {
    const numeric = Number(value);
    return Number.isFinite(numeric)
        ? new Intl.NumberFormat("ko-KR", { notation: "compact", maximumFractionDigits: 1 }).format(numeric)
        : "-";
}

function signedPrice(value: number, currency: string) {
    return (value > 0 ? "+" : "") + formatPrice(value, currency);
}

function signedPercent(value: number) {
    return (value > 0 ? "+" : "") + new Intl.NumberFormat("ko-KR", {
        style: "percent",
        maximumFractionDigits: 2,
    }).format(value);
}

export default StockMarketDetail;
