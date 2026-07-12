import type { FormEvent } from "react";

type StockSearchFormProps = {
    value: string;
    loading: boolean;
    paused: boolean;
    onValueChange: (value: string) => void;
    onSubmit: () => void;
};

function StockSearchForm({ value, loading, paused, onValueChange, onSubmit }: StockSearchFormProps) {
    const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        onSubmit();
    };

    return (
        <form className="stock-search-form" onSubmit={handleSubmit}>
            <label>
                종목
                <input
                    value={value}
                    onChange={(event) => onValueChange(event.target.value.toUpperCase())}
                    placeholder="005930,AAPL"
                    aria-label="조회 종목"
                />
            </label>
            <button className="primary-button" type="submit" disabled={loading}>
                {loading ? "갱신 중" : "조회"}
            </button>
            <span className={paused ? "stock-poll-state is-paused" : "stock-poll-state"}>
                {paused ? "일시정지" : "2초 갱신"}
            </span>
        </form>
    );
}

export default StockSearchForm;
