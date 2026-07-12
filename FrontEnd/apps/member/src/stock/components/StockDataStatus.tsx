import { formatDateTime } from "./formatDateTime";
import type { DataStatus } from "../types/marketData";

type StockDataStatusProps = {
    status: DataStatus;
    fetchedAt: string;
};

function StockDataStatus({ status, fetchedAt }: StockDataStatusProps) {
    return (
        <span className={status === "STALE" ? "stock-status is-stale" : "stock-status"}>
            {status === "STALE" ? "지연" : "최신"} · {formatDateTime(fetchedAt)}
        </span>
    );
}

export default StockDataStatus;
