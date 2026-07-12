import http from "k6/http";
import { check, sleep } from "k6";
import { Rate } from "k6/metrics";

const tossRequestsBounded = new Rate("stock_toss_requests_bounded");

const baseUrl = (__ENV.STOCK_BFF_BASE_URL || "http://user.localtest.me").replace(/\/$/, "");
const symbols = __ENV.STOCK_SYMBOLS || "005930,000660,035420,051910,207940";
const cookie = __ENV.BFF_COOKIE || "";
const prometheusUrl = (__ENV.PROMETHEUS_URL || "").replace(/\/$/, "");
const tossMetricWindow = __ENV.STOCK_TOSS_METRIC_WINDOW || "3m";
const maxTossRequests = Number(__ENV.STOCK_TOSS_MAX_REQUESTS || "80");

export const options = {
    scenarios: {
        stock_market_data: {
            executor: "constant-vus",
            vus: 20,
            duration: "2m",
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"],
        http_req_duration: ["p(95)<500"],
        stock_toss_requests_bounded: ["rate==1"],
    },
};

export default function () {
    const response = http.get(
        `${baseUrl}/bff/stock/market/workspace?symbols=${encodeURIComponent(symbols)}`,
        { headers: requestHeaders() }
    );

    check(response, {
        "workspace response is 2xx": (res) => res.status >= 200 && res.status < 300,
    });

    sleep(2);
}

export function teardown() {
    if (!prometheusUrl) {
        tossRequestsBounded.add(false);
        return;
    }

    const query = `sum(increase(stock_toss_requests_total[${tossMetricWindow}]))`;
    const response = http.get(
        `${prometheusUrl}/api/v1/query?query=${encodeURIComponent(query)}`
    );
    const value = response.status === 200 ? prometheusValue(response) : Number.POSITIVE_INFINITY;

    check(response, {
        "Prometheus query succeeded": (res) => res.status === 200,
        "stock.toss.requests remains bounded by shared cache": () =>
            value <= maxTossRequests,
    });
    tossRequestsBounded.add(value <= maxTossRequests);
}

function prometheusValue(response) {
    try {
        const body = response.json();
        return Number(body?.data?.result?.[0]?.value?.[1] || "0");
    } catch {
        return Number.POSITIVE_INFINITY;
    }
}

function requestHeaders() {
    if (!cookie) {
        return {};
    }

    return { Cookie: cookie };
}
