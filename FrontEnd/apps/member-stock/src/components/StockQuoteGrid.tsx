import { findStockInstrument } from "../catalog/stockCatalog";
import type { MarketQuote, StockSummary } from "../types/marketData";
import { formatDateTime } from "./formatDateTime";

type StockQuoteGridProps = {
    quotes: MarketQuote[];
    stocks: StockSummary[];
    selectedSymbol: string;
    onSelect: (symbol: string) => void;
};

function StockQuoteGrid({ quotes, stocks, selectedSymbol, onSelect }: StockQuoteGridProps) {
    const stockBySymbol = new Map(stocks.map((stock) => [stock.symbol, stock]));
    if (quotes.length === 0) return <p className="stock-empty">조회된 시세가 없습니다.</p>;

    return (
        <div className="stock-quote-grid">
            {quotes.map((quote) => {
                const stock = stockBySymbol.get(quote.symbol);
                const catalog = findStockInstrument(quote.symbol);
                const className = "stock-quote-card"
                    + (quote.dataStatus === "STALE" ? " is-stale" : "")
                    + (selectedSymbol === quote.symbol ? " is-selected" : "");

                return (
                    <button className={className} key={quote.symbol} type="button" onClick={() => onSelect(quote.symbol)}>
                        <div className="stock-quote-heading">
                            <div>
                                <span>{stock?.market ?? catalog?.market ?? "MARKET"}</span>
                                <strong>{catalog?.name ?? stock?.name ?? quote.symbol}</strong>
                            </div>
                            <span className="stock-list-currency">{quote.currency}</span>
                        </div>
                        <p className="stock-name">{quote.symbol} · {stock?.englishName ?? catalog?.englishName ?? quote.symbol}</p>
                        <div className="stock-price-row"><strong>{formatPrice(quote.lastPrice, quote.currency)}</strong></div>
                        {quote.dataStatus === "STALE" && (
                            <p className="stock-stale-banner">마지막 정상 수신 {formatDateTime(quote.timestamp ?? quote.fetchedAt)}</p>
                        )}
                    </button>
                );
            })}
        </div>
    );
}

function formatPrice(value: string, currency: string) {
    const numeric = Number(value);
    if (Number.isNaN(numeric)) return value;
    return new Intl.NumberFormat(currency === "KRW" ? "ko-KR" : "en-US", {
        maximumFractionDigits: currency === "KRW" ? 0 : 2,
    }).format(numeric);
}

export default StockQuoteGrid;
