import { useState } from "react";
import type { FormEvent } from "react";
import {
    createStockWatchItem,
    deleteStockWatchItem,
    updateStockWatchItem,
} from "../api/stockApi";
import StockCandleTable from "../components/StockCandleTable";
import StockQuoteGrid from "../components/StockQuoteGrid";
import StockSearchForm from "../components/StockSearchForm";
import { useMarketWorkspace } from "../hooks/useMarketWorkspace";
import { toKoreanStockMessage } from "../messages/stockErrorMessages";
import type { PartialFailure } from "../types/marketData";
import type { StockWatchItem } from "../types/stockWatchItem";

const INITIAL_SYMBOLS = ["005930", "AAPL"];
const SYMBOL_PATTERN = /^[A-Z0-9.-]{1,20}$/;
const MAX_SYMBOLS = 200;

function StockEntryPage() {
    const [symbols, setSymbols] = useState(INITIAL_SYMBOLS);
    const [symbolQuery, setSymbolQuery] = useState(INITIAL_SYMBOLS.join(","));
    const [selectedItemId, setSelectedItemId] = useState<number | null>(null);
    const [symbol, setSymbol] = useState("");
    const [memo, setMemo] = useState("");
    const [message, setMessage] = useState("");
    const { workspace, loading, error, paused, refresh } = useMarketWorkspace(symbols);

    const items = workspace?.watchItems ?? [];
    const quotes = workspace?.prices ?? [];
    const stocks = workspace?.stocks ?? [];
    const failures = workspace?.failures ?? [];
    const displayMessage = message || (error ? toKoreanStockMessage(error, "시세 정보를 불러오지 못했습니다.") : "");

    const resetForm = () => {
        setSelectedItemId(null);
        setSymbol("");
        setMemo("");
    };

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setMessage("");

        try {
            if (selectedItemId === null) {
                await createStockWatchItem({ symbol, memo });
                setMessage("관심 종목을 등록했습니다.");
            } else {
                await updateStockWatchItem(selectedItemId, { symbol, memo });
                setMessage("관심 종목을 수정했습니다.");
            }

            resetForm();
            refresh();
        } catch (error) {
            setMessage(toKoreanStockMessage(error, "관심 종목 저장에 실패했습니다."));
        }
    };

    const handleEdit = (item: StockWatchItem) => {
        setSelectedItemId(item.id);
        setSymbol(item.symbol);
        setMemo(item.memo);
    };

    const handleDelete = async (itemId: number) => {
        setMessage("");

        try {
            await deleteStockWatchItem(itemId);
            setMessage("관심 종목을 삭제했습니다.");
            if (selectedItemId === itemId) {
                resetForm();
            }
            refresh();
        } catch (error) {
            setMessage(toKoreanStockMessage(error, "관심 종목 삭제에 실패했습니다."));
        }
    };

    const handleSymbolSubmit = () => {
        const nextSymbols = parseSymbols(symbolQuery);

        if (nextSymbols.length === 0) {
            setMessage("조회할 종목을 입력해 주세요.");
            return;
        }

        if (nextSymbols.length > MAX_SYMBOLS) {
            setMessage("종목은 최대 200개까지 조회할 수 있습니다.");
            return;
        }

        if (nextSymbols.some((nextSymbol) => !SYMBOL_PATTERN.test(nextSymbol))) {
            setMessage("종목 코드는 영문, 숫자, 점, 하이픈만 사용할 수 있습니다.");
            return;
        }

        setMessage("");
        setSymbols(nextSymbols);
    };

    return (
        <div className="stock-workspace">
            <section className="info-panel stock-panel">
                <div className="stock-section-heading">
                    <div>
                        <h2>시장 현황</h2>
                        <p className="entry-copy">관심 종목과 실시간 시세</p>
                    </div>
                    <StockSearchForm
                        value={symbolQuery}
                        loading={loading}
                        paused={paused}
                        onValueChange={setSymbolQuery}
                        onSubmit={handleSymbolSubmit}
                    />
                </div>

                {displayMessage && <p className="status-message">{displayMessage}</p>}
                <FailurePanel failures={failures} />
                <StockQuoteGrid quotes={quotes} stocks={stocks} />
            </section>

            <section className="stock-layout">
                <div className="info-panel stock-panel">
                    <div className="stock-section-heading">
                        <h2>관심 종목</h2>
                    </div>
                    <form className="auth-form stock-watch-form" onSubmit={handleSubmit}>
                        <label>
                            종목
                            <input value={symbol} onChange={(event) => setSymbol(event.target.value.toUpperCase())} required />
                        </label>
                        <label>
                            메모
                            <input value={memo} onChange={(event) => setMemo(event.target.value)} required />
                        </label>
                        <button className="primary-button" type="submit">
                            {selectedItemId === null ? "등록" : "수정"}
                        </button>
                        {selectedItemId !== null && (
                            <button className="secondary-button" type="button" onClick={resetForm}>
                                취소
                            </button>
                        )}
                    </form>

                    <div className="stock-watch-list">
                        {items.length === 0 ? (
                            <p className="stock-empty">관심 종목이 없습니다.</p>
                        ) : (
                            items.map((item) => (
                                <article className="stock-watch-item" key={item.id}>
                                    <div>
                                        <strong>{item.symbol}</strong>
                                        <span>{item.memo}</span>
                                    </div>
                                    <div className="stock-actions">
                                        <button type="button" onClick={() => handleEdit(item)}>
                                            수정
                                        </button>
                                        <button type="button" onClick={() => void handleDelete(item.id)}>
                                            삭제
                                        </button>
                                    </div>
                                </article>
                            ))
                        )}
                    </div>
                </div>

                <section className="info-panel stock-panel">
                    <div className="stock-section-heading">
                        <h2>캔들 테이블</h2>
                    </div>
                    <StockCandleTable quotes={quotes} />
                </section>
            </section>
        </div>
    );
}

function FailurePanel({ failures }: { failures: PartialFailure[] }) {
    if (failures.length === 0) {
        return null;
    }

    return (
        <div className="stock-failure-panel">
            {failures.map((failure) => (
                <article key={`${failure.component}-${failure.code}-${failure.traceId ?? "none"}`}>
                    <strong>{failure.component}</strong>
                    <span>{failure.code}</span>
                    <p>{failure.message}</p>
                    {failure.traceId && <small>Trace ID {failure.traceId}</small>}
                </article>
            ))}
        </div>
    );
}

function parseSymbols(value: string) {
    return Array.from(new Set(
        value
            .split(",")
            .map((entry) => entry.trim().toUpperCase())
            .filter(Boolean)
    ));
}

export default StockEntryPage;
