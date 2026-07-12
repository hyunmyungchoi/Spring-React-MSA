import { formatDateTime } from "./formatDateTime";
import StockDataStatus from "./StockDataStatus";
import type { MarketQuote, StockSummary } from "../types/marketData";

type StockQuoteGridProps = {
    quotes: MarketQuote[];
    stocks: StockSummary[];
};

function StockQuoteGrid({ quotes, stocks }: StockQuoteGridProps) {
    const stockBySymbol = new Map(stocks.map((stock) => [stock.symbol, stock]));

    if (quotes.length === 0) {
        return <p className="stock-empty">조회된 시세가 없습니다.</p>;
    }

    return (
        <div className="stock-quote-grid">
            {quotes.map((quote) => {
                const stock = stockBySymbol.get(quote.symbol);

                return (
                    <article className={quote.dataStatus === "STALE" ? "stock-quote-card is-stale" : "stock-quote-card"} key={quote.symbol}>
                        <div className="stock-quote-heading">
                            <div>
                                <span>{stock?.market ?? "MARKET"}</span>
                                <strong>{quote.symbol}</strong>
                            </div>
                            <StockDataStatus status={quote.dataStatus} fetchedAt={quote.fetchedAt} />
                        </div>
                        <p className="stock-name">{stock?.name ?? stock?.englishName ?? quote.symbol}</p>
                        <div className="stock-price-row">
                            <strong>{formatPrice(quote.lastPrice, quote.currency)}</strong>
                            <span>{quote.currency}</span>
                        </div>
                        {quote.dataStatus === "STALE" && (
                            <p className="stock-stale-banner">마지막 정상 수신: {formatDateTime(quote.timestamp ?? quote.fetchedAt)}</p>
                        )}
                    </article>
                );
            })}
        </div>
    );
}

function formatPrice(value: string, currency: string) {
    const numeric = Number(value);

    if (Number.isNaN(numeric)) {
        return value;
    }

    return new Intl.NumberFormat(currency === "KRW" ? "ko-KR" : "en-US", {
        maximumFractionDigits: currency === "KRW" ? 0 : 2,
    }).format(numeric);
}

export default StockQuoteGrid;
