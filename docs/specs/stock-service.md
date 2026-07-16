# 주식 서비스 스펙

## 범위

관심 종목 CRUD, Toss Investment API 기반 종목·현재가·캔들 조회, Redis cache와 BFF workspace 조합을 정의한다.

## 공개 BFF endpoint

| Method/Path | 설명 |
| --- | --- |
| `GET /bff/stock/me` | Stock Resource Server 사용자 확인 |
| `GET /bff/stock/watch-items` | 내 관심 종목 |
| `POST /bff/stock/watch-items` | 관심 종목 생성 |
| `PUT /bff/stock/watch-items/{itemId}` | 내 관심 종목 수정 |
| `DELETE /bff/stock/watch-items/{itemId}` | 내 관심 종목 삭제 |
| `GET /bff/stock/market/workspace?symbols=...` | 종목·가격·관심 종목 조합 |

Stock Service 직접 endpoint는 `/api/stock/**`이며 JWT 인증이 필수다. 소유자는 JWT `sub`(`authentication.getName()`)로 정한다.

## 관심 종목

요청은 `symbol`, `memo`를 가진다. 응답은 `id`, `symbol`, `memo`, `ownerSub`, `createdAt`, `updatedAt`을 포함한다. 같은 사용자와 symbol 조합은 중복될 수 없고 중복은 409 `WATCH_ITEM_DUPLICATE`다. 다른 사용자의 item ID는 조회하지 않고 404 `WATCH_ITEM_NOT_FOUND`로 처리한다.

## 시장 데이터 endpoint

| Method/Path | 제약 |
| --- | --- |
| `GET /api/stock/market/prices?symbols=A,B` | comma 구분, 최대 200 symbol |
| `GET /api/stock/market/stocks?symbols=A,B` | comma 구분, 최대 200 symbol |
| `GET /api/stock/market/candles/{symbol}?interval=1m&count=100` | interval `1m`/`1d`, count 1~200 |

symbol은 trim 후 대문자로 정규화하며 `[A-Za-z0-9.-]{1,20}`만 허용한다. 잘못된 입력은 `INVALID_MARKET_SYMBOL`, `TOO_MANY_MARKET_SYMBOLS`, `INVALID_CANDLE_INTERVAL`, `INVALID_CANDLE_COUNT` 중 하나다.

가격 응답은 `symbol`, `lastPrice`, `currency`, 원본 `timestamp`, `fetchedAt`, `dataStatus`를 가진다. 숫자는 JSON number가 아니라 정밀도 보존을 위한 문자열이다. `dataStatus`는 `FRESH` 또는 `STALE`다.

## Cache 정책

| 데이터 | fresh TTL | 보조 정책 |
| --- | ---: | --- |
| 현재가 | 2초 | stale copy 5분, symbol별 2초 refresh lock |
| 종목 정보 | 24시간 | 없음 |
| 캔들 | 30초 | interval/count별 key |
| Toss access token | API 만료 기반 | Redis refresh lock |

현재가 cache miss에서 한 replica만 refresh lock을 얻어 Toss API를 호출한다. 다른 요청은 fresh cache를 다시 보고 없으면 stale cache를 사용한다. Toss rate limit 또는 unavailable 오류에서도 stale 가격이 있으면 반환한다.

Toss API 401은 token cache를 제거하고 한 번만 재발급·재요청한다. 429는 `TOSS_RATE_LIMITED`, 기타 가용성 오류는 `TOSS_MARKET_UNAVAILABLE`로 매핑한다.

## BFF workspace

`/bff/stock/market/workspace`는 stocks, prices, watchItems를 독립 호출해 다음 형태로 조합한다.

```json
{
  "stocks": [],
  "prices": [],
  "watchItems": [],
  "failures": [
    { "component": "prices", "code": "...", "message": "...", "traceId": null }
  ]
}
```

구성 요소 하나의 실패로 전체 요청을 실패시키지 않는다. create/update/delete 같은 명령은 partial success가 아니라 downstream 오류를 그대로 실패로 반환한다.

## Metrics

- `stock.cache.hits`
- `stock.cache.stale_served`
- `stock.toss.requests`
- `stock.toss.errors`
- `stock.toss.rate_limited`
- `stock.toss.duration`

endpoint와 outcome tag를 사용한다. 외부 API 호출량이 cache 효과에 맞게 제한되는지 load test와 Prometheus query로 검증한다.

## 수용 기준

- 동일 symbol 동시 요청이 외부 API stampede를 만들지 않는다.
- 소유자가 다른 관심 종목은 수정/삭제할 수 없다.
- stale 응답은 반드시 `STALE`로 표시된다.
- 401 재시도는 최대 한 번이며 429를 무한 재시도하지 않는다.
