import { formatDateTime } from "./formatDateTime";
import StockDataStatus from "./StockDataStatus";
import type { MarketQuote } from "../types/marketData";

type StockCandleTableProps = {
    quotes: MarketQuote[];
};

function StockCandleTable({ quotes }: StockCandleTableProps) {
    return (
        <div className="stock-table-wrap">
            <table className="stock-candle-table">
                <thead>
                    <tr>
                        <th>종목</th>
                        <th>시간</th>
                        <th>종가</th>
                        <th>통화</th>
                        <th>상태</th>
                    </tr>
                </thead>
                <tbody>
                    {quotes.length === 0 ? (
                        <tr>
                            <td colSpan={5}>표시할 데이터가 없습니다.</td>
                        </tr>
                    ) : (
                        quotes.map((quote) => (
                            <tr key={quote.symbol}>
                                <td>{quote.symbol}</td>
                                <td>{formatDateTime(quote.timestamp)}</td>
                                <td>{quote.lastPrice}</td>
                                <td>{quote.currency}</td>
                                <td>
                                    <StockDataStatus status={quote.dataStatus} fetchedAt={quote.fetchedAt} />
                                </td>
                            </tr>
                        ))
                    )}
                </tbody>
            </table>
        </div>
    );
}

export default StockCandleTable;
