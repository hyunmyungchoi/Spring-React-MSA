import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import {
    createStockWatchItem,
    deleteStockWatchItem,
    fetchStockWatchItems,
    updateStockWatchItem,
} from "../api/stockApi";
import type { StockWatchItem } from "../types/stockWatchItem";

// Renders the stock CRUD workspace.
function StockEntryPage() {
    const [items, setItems] = useState<StockWatchItem[]>([]);
    const [selectedItemId, setSelectedItemId] = useState<number | null>(null);
    const [symbol, setSymbol] = useState("");
    const [memo, setMemo] = useState("");
    const [message, setMessage] = useState("");

    const loadItems = async () => {
        const data = await fetchStockWatchItems();
        setItems(data);
    };

    useEffect(() => {
        let ignore = false;

        fetchStockWatchItems()
            .then((data) => {
                if (!ignore) {
                    setItems(data);
                }
            })
            .catch((error) => {
                if (!ignore) {
                    setMessage(error instanceof Error ? error.message : "Stock watch items load failed");
                }
            });

        return () => {
            ignore = true;
        };
    }, []);

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
                setMessage("Stock watch item created.");
            } else {
                await updateStockWatchItem(selectedItemId, { symbol, memo });
                setMessage("Stock watch item updated.");
            }

            resetForm();
            await loadItems();
        } catch (error) {
            setMessage(error instanceof Error ? error.message : "Stock watch item save failed");
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
            setMessage("Stock watch item deleted.");
            if (selectedItemId === itemId) {
                resetForm();
            }
            await loadItems();
        } catch (error) {
            setMessage(error instanceof Error ? error.message : "Stock watch item delete failed");
        }
    };

    return (
        <section className="info-panel">
            <h2>Stock CRUD</h2>
            <form className="auth-form" onSubmit={handleSubmit}>
                <label>
                    Symbol
                    <input value={symbol} onChange={(event) => setSymbol(event.target.value.toUpperCase())} required />
                </label>
                <label>
                    Memo
                    <input value={memo} onChange={(event) => setMemo(event.target.value)} required />
                </label>
                <button className="primary-button" type="submit">
                    {selectedItemId === null ? "Create item" : "Update item"}
                </button>
                {selectedItemId !== null && (
                    <button className="secondary-button" type="button" onClick={resetForm}>
                        Cancel edit
                    </button>
                )}
            </form>

            {message && <p className="status-message">{message}</p>}

            <div className="service-grid">
                {items.map((item) => (
                    <article className="service-tile" key={item.id}>
                        <span>{item.owner}</span>
                        <strong>{item.symbol}</strong>
                        <p className="entry-copy">{item.memo}</p>
                        <div className="user-nav">
                            <button type="button" onClick={() => handleEdit(item)}>
                                Edit
                            </button>
                            <button type="button" onClick={() => void handleDelete(item.id)}>
                                Delete
                            </button>
                        </div>
                    </article>
                ))}
            </div>
        </section>
    );
}

export default StockEntryPage;
