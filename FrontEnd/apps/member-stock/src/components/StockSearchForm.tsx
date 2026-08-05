import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { findStockInstrument, searchStockCatalog } from "../catalog/stockCatalog";

type StockSearchFormProps = {
    query: string;
    symbols: string[];
    loading: boolean;
    paused: boolean;
    onQueryChange: (value: string) => void;
    onSelect: (value: string) => void;
    onRemove: (symbol: string) => void;
};

function StockSearchForm({ query, symbols, loading, paused, onQueryChange, onSelect, onRemove }: StockSearchFormProps) {
    const [focused, setFocused] = useState(false);
    const suggestions = useMemo(() => searchStockCatalog(query), [query]);

    const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (query.trim()) onSelect(query);
    };

    return (
        <form className="stock-search-form" onSubmit={handleSubmit}>
            <div className="stock-search-field">
                <label htmlFor="stock-search">종목 검색</label>
                <div className="stock-search-input-wrap">
                    <input
                        id="stock-search"
                        value={query}
                        onChange={(event) => onQueryChange(event.target.value)}
                        onFocus={() => setFocused(true)}
                        onBlur={() => window.setTimeout(() => setFocused(false), 120)}
                        placeholder="삼성전자, 애플, TSMC"
                        autoComplete="off"
                        aria-autocomplete="list"
                        aria-controls="stock-suggestions"
                    />
                    {focused && (
                        <div className="stock-suggestions" id="stock-suggestions" role="listbox">
                            {suggestions.map((instrument) => (
                                <button
                                    key={instrument.symbol}
                                    type="button"
                                    role="option"
                                    onMouseDown={(event) => event.preventDefault()}
                                    onClick={() => onSelect(instrument.symbol)}
                                >
                                    <span className="stock-suggestion-logo">{instrument.name.slice(0, 1)}</span>
                                    <span><strong>{instrument.name}</strong><small>{instrument.englishName}</small></span>
                                    <em>{instrument.symbol} · {instrument.market}</em>
                                </button>
                            ))}
                            {suggestions.length === 0 && <p>티커를 직접 입력해 조회할 수 있습니다.</p>}
                        </div>
                    )}
                </div>
                <div className="stock-symbol-chips">
                    {symbols.map((symbol) => (
                        <button key={symbol} type="button" onClick={() => onRemove(symbol)} title="조회 목록에서 제거">
                            <span>{findStockInstrument(symbol)?.name ?? symbol}</span>
                            <small>{symbol}</small>
                            <b aria-hidden="true">×</b>
                        </button>
                    ))}
                </div>
            </div>
            <button className="primary-button stock-search-submit" type="submit" disabled={loading || !query.trim()}>
                {loading ? "갱신 중" : "추가"}
            </button>
            <span className={paused ? "stock-poll-state is-paused" : "stock-poll-state"}>
                {paused ? "일시정지" : "2초 갱신"}
            </span>
        </form>
    );
}

export default StockSearchForm;
