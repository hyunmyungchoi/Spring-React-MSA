import http from "k6/http";
import { check, sleep } from "k6";
import { Rate } from "k6/metrics";

const tossRequestsBounded = new Rate("stock_toss_requests_bounded");

const baseUrl = (__ENV.STOCK_BFF_BASE_URL || "http://user.localtest.me").replace(/\/$/, "");
const symbols = __ENV.STOCK_SYMBOLS || "005930,000660,035420,051910,207940";
const cookie = __ENV.BFF_COOKIE || "";
const prometheusUrl = (__ENV.PROMETHEUS_URL || "").replace(/\/$/, "");
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

export function setup() {
    return {
        baselineTossRequests: readTossRequestCounter(),
    };
}

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

export function teardown(data) {
    if (!prometheusUrl) {
        tossRequestsBounded.add(false);
        return;
    }

    const currentTossRequests = readTossRequestCounter();
    const tossRequestsDuringRun = currentTossRequests - data.baselineTossRequests;

    check(null, {
        "Prometheus query succeeded": () => Number.isFinite(currentTossRequests),
        "stock.toss.requests remains bounded by shared cache": () =>
            tossRequestsDuringRun <= maxTossRequests,
    });
    tossRequestsBounded.add(tossRequestsDuringRun <= maxTossRequests);
}

function readTossRequestCounter() {
    if (!prometheusUrl) {
        return Number.POSITIVE_INFINITY;
    }

    const query = "sum(stock_toss_requests_total)";
    const response = http.get(
        `${prometheusUrl}/api/v1/query?query=${encodeURIComponent(query)}`
    );

    if (response.status !== 200) {
        return Number.POSITIVE_INFINITY;
    }

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
