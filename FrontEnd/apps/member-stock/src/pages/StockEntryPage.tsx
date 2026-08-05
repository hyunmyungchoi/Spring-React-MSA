import { useState } from "react";
import type { FormEvent } from "react";
import { createStockWatchItem, deleteStockWatchItem, updateStockWatchItem } from "../api/stockApi";
import { findStockInstrument, resolveStockSymbol } from "../catalog/stockCatalog";
import StockCandleTable from "../components/StockCandleTable";
import StockMarketDetail from "../components/StockMarketDetail";
import StockQuoteGrid from "../components/StockQuoteGrid";
import StockSearchForm from "../components/StockSearchForm";
import { useMarketCandles } from "../hooks/useMarketCandles";
import { useMarketWorkspace } from "../hooks/useMarketWorkspace";
import { toKoreanStockMessage } from "../messages/stockErrorMessages";
import type { PartialFailure } from "../types/marketData";
import type { StockWatchItem } from "../types/stockWatchItem";

const INITIAL_SYMBOLS = ["005930", "AAPL"];
const SYMBOL_PATTERN = /^[A-Z0-9.-]{1,20}$/;
const MAX_SYMBOLS = 200;

function StockEntryPage() {
    const [symbols, setSymbols] = useState(INITIAL_SYMBOLS);
    const [symbolQuery, setSymbolQuery] = useState("");
    const [selectedSymbol, setSelectedSymbol] = useState(INITIAL_SYMBOLS[0]);
    const [selectedItemId, setSelectedItemId] = useState<number | null>(null);
    const [symbol, setSymbol] = useState("");
    const [memo, setMemo] = useState("");
    const [message, setMessage] = useState("");
    const { workspace, loading, error, paused, refresh } = useMarketWorkspace(symbols);
    const { candles, loading: candlesLoading, error: candlesError } = useMarketCandles(selectedSymbol);

    const items = workspace?.watchItems ?? [];
    const quotes = workspace?.prices ?? [];
    const stocks = workspace?.stocks ?? [];
    const failures = workspace?.failures ?? [];
    const selectedQuote = quotes.find((quote) => quote.symbol === selectedSymbol);
    const selectedStock = stocks.find((stock) => stock.symbol === selectedSymbol);
    const displayMessage = message || (error ? toKoreanStockMessage(error, "시세 정보를 불러오지 못했습니다.") : "");

    const resetForm = () => {
        setSelectedItemId(null);
        setSymbol("");
        setMemo("");
    };

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setMessage("");
        const resolvedSymbol = resolveStockSymbol(symbol);

        if (!SYMBOL_PATTERN.test(resolvedSymbol)) {
            setMessage("올바른 종목명 또는 티커를 입력해 주세요.");
            return;
        }

        try {
            if (selectedItemId === null) {
                await createStockWatchItem({ symbol: resolvedSymbol, memo });
                setMessage("관심 종목에 등록했습니다.");
            } else {
                await updateStockWatchItem(selectedItemId, { symbol: resolvedSymbol, memo });
                setMessage("관심 종목을 수정했습니다.");
            }
            resetForm();
            refresh();
        } catch (requestError) {
            setMessage(toKoreanStockMessage(requestError, "관심 종목 저장에 실패했습니다."));
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
            if (selectedItemId === itemId) resetForm();
            refresh();
        } catch (requestError) {
            setMessage(toKoreanStockMessage(requestError, "관심 종목 삭제에 실패했습니다."));
        }
    };

    const addSymbol = (query: string) => {
        const nextSymbol = resolveStockSymbol(query);
        if (!SYMBOL_PATTERN.test(nextSymbol)) {
            setMessage("종목명 또는 티커를 다시 확인해 주세요.");
            return;
        }
        if (symbols.length >= MAX_SYMBOLS && !symbols.includes(nextSymbol)) {
            setMessage("종목은 최대 200개까지 조회할 수 있습니다.");
            return;
        }
        setSymbols((current) => current.includes(nextSymbol) ? current : [...current, nextSymbol]);
        setSelectedSymbol(nextSymbol);
        setSymbolQuery("");
        setMessage("");
    };

    const removeSymbol = (target: string) => {
        if (symbols.length === 1) {
            setMessage("최소 한 개의 조회 종목은 남겨 주세요.");
            return;
        }
        const nextSymbols = symbols.filter((current) => current !== target);
        setSymbols(nextSymbols);
        if (selectedSymbol === target) setSelectedSymbol(nextSymbols[0]);
    };

    return (
        <div className="stock-workspace">
            <section className="info-panel stock-panel stock-overview">
                <div className="stock-section-heading">
                    <div>
                        <span className="stock-kicker">MARKET</span>
                        <h2>오늘의 시장</h2>
                        <p className="entry-copy">종목명으로 찾고 Toss Open API 시세를 한 화면에서 확인하세요.</p>
                    </div>
                    <StockSearchForm
                        query={symbolQuery}
                        symbols={symbols}
                        loading={loading}
                        paused={paused}
                        onQueryChange={setSymbolQuery}
                        onSelect={addSymbol}
                        onRemove={removeSymbol}
                    />
                </div>

                {displayMessage && <p className="status-message">{displayMessage}</p>}
                <FailurePanel failures={failures} />
                <div className="stock-market-board">
                    <aside className="stock-list-panel">
                        <div className="stock-list-heading">
                            <strong>종목 리스트</strong>
                            <span>{quotes.length}개</span>
                        </div>
                        <StockQuoteGrid quotes={quotes} stocks={stocks} selectedSymbol={selectedSymbol} onSelect={setSelectedSymbol} />
                    </aside>
                    <StockMarketDetail
                        quote={selectedQuote}
                        stock={selectedStock}
                        candles={candles}
                        loading={candlesLoading}
                        error={candlesError}
                    />
                </div>
            </section>

            <section className="stock-layout">
                <div className="info-panel stock-panel">
                    <div className="stock-section-heading compact">
                        <div><span className="stock-kicker">WATCHLIST</span><h2>관심 종목</h2></div>
                    </div>
                    <form className="auth-form stock-watch-form" onSubmit={handleSubmit}>
                        <label>
                            종목명 또는 티커
                            <input value={symbol} onChange={(event) => setSymbol(event.target.value)} placeholder="삼성전자, 애플, TSMC" required />
                        </label>
                        <label>
                            메모
                            <input value={memo} onChange={(event) => setMemo(event.target.value)} required />
                        </label>
                        <button className="primary-button" type="submit">{selectedItemId === null ? "등록" : "수정"}</button>
                        {selectedItemId !== null && <button className="secondary-button" type="button" onClick={resetForm}>취소</button>}
                    </form>

                    <div className="stock-watch-list">
                        {items.length === 0 ? (
                            <p className="stock-empty">등록한 관심 종목이 없습니다.</p>
                        ) : (
                            items.map((item) => (
                                <article className="stock-watch-item" key={item.id}>
                                    <button className="stock-watch-select" type="button" onClick={() => addSymbol(item.symbol)}>
                                        <strong>{findStockInstrument(item.symbol)?.name ?? item.symbol}</strong>
                                        <span>{item.symbol} · {item.memo}</span>
                                    </button>
                                    <div className="stock-actions">
                                        <button type="button" onClick={() => handleEdit(item)}>수정</button>
                                        <button type="button" onClick={() => void handleDelete(item.id)}>삭제</button>
                                    </div>
                                </article>
                            ))
                        )}
                    </div>
                </div>

                <section className="info-panel stock-panel">
                    <div className="stock-section-heading">
                        <div>
                            <span className="stock-kicker">DAILY</span>
                            <h2>{findStockInstrument(selectedSymbol)?.name ?? selectedSymbol} 일봉</h2>
                        </div>
                    </div>
                    <StockCandleTable candles={candles} />
                </section>
            </section>
        </div>
    );
}

function FailurePanel({ failures }: { failures: PartialFailure[] }) {
    if (failures.length === 0) return null;
    return (
        <div className="stock-failure-panel">
            {failures.map((failure) => (
                <article key={[failure.component, failure.code, failure.traceId ?? "none"].join("-")}>
                    <strong>{failure.component}</strong>
                    <span>{failure.code}</span>
                    <p>{failure.message}</p>
                    {failure.traceId && <small>Trace ID {failure.traceId}</small>}
                </article>
            ))}
        </div>
    );
}

export default StockEntryPage;
