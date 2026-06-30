import { bffGet } from "../../common/api/bffClient";

export async function fetchStockMe() {
    return bffGet<unknown>("/bff/stock/me");
}
