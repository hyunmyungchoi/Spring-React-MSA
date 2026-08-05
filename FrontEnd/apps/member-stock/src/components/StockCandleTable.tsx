import type { Candle } from "../types/marketData";
import { formatDateTime } from "./formatDateTime";

type StockCandleTableProps = { candles: Candle[] };

function StockCandleTable({ candles }: StockCandleTableProps) {
    return (
        <div className="stock-table-wrap">
            <table className="stock-candle-table">
                <thead>
                    <tr><th>날짜</th><th>시가</th><th>고가</th><th>저가</th><th>종가</th><th>거래량</th></tr>
                </thead>
                <tbody>
                    {candles.length === 0 ? (
                        <tr><td colSpan={6}>표시할 일봉 데이터가 없습니다.</td></tr>
                    ) : (
                        candles.slice(0, 10).map((candle) => (
                            <tr key={candle.timestamp}>
                                <td>{formatDateTime(candle.timestamp)}</td>
                                <td>{candle.openPrice}</td>
                                <td>{candle.highPrice}</td>
                                <td>{candle.lowPrice}</td>
                                <td>{candle.closePrice}</td>
                                <td>{new Intl.NumberFormat("ko-KR").format(Number(candle.volume))}</td>
                            </tr>
                        ))
                    )}
                </tbody>
            </table>
        </div>
    );
}

export default StockCandleTable;
