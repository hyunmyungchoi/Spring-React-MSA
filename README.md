<img width="1672" height="941" alt="image" src="https://github.com/user-attachments/assets/82cc9f08-7028-4f07-9f5c-5f412788f2e5" />

## Stock Market Data Slice

The stock market data slice uses the member BFF and stock service to expose
watchlist CRUD and market workspace data. Toss API credentials must be supplied
only through server-side environment variables or Kubernetes Secrets:

- `TOSS_API_CLIENT_ID`
- `TOSS_API_CLIENT_SECRET`
- `TOSS_API_BASE_URL`

Do not commit real credential values. For Kubernetes, copy
`infra/k8s/spring-msa/examples/02-secrets.example.yaml` to a local secret file
and replace the placeholder values outside source control.

Useful verification commands:

```powershell
cd BackEnd\spring-member-stock-service
.\gradlew.bat test

cd ..\spring-member-bff-service
.\gradlew.bat test

cd ..\..\FrontEnd\apps\member
pnpm.cmd run lint
pnpm.cmd run build:stock

cd ..\..\..
python -m unittest infra\ci\test_select_build_matrix.py
```

The stock web polls `GET /bff/stock/market/workspace?symbols=...` every two
seconds while the browser tab is visible. The stock service shares Toss market
responses through Redis cache entries so concurrent browser polling does not
become one external request per browser request.

Load test. Set `PROMETHEUS_URL` so the external Toss request threshold can be
verified:

```powershell
$env:PROMETHEUS_URL="http://prometheus.localtest.me"
k6 run infra\load-tests\stock-market-data.js
```

Environment variables:

- `STOCK_BFF_BASE_URL`: gateway base URL, default `http://user.localtest.me`
- `STOCK_SYMBOLS`: comma-separated symbols, default five common symbols
- `BFF_COOKIE`: authenticated BFF session cookie when required
- `PROMETHEUS_URL`: Prometheus base URL, required for the external request threshold
- `STOCK_TOSS_MAX_REQUESTS`: maximum external Toss requests over the window
