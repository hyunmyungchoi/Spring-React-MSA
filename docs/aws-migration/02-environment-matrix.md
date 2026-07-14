# Environment Matrix

AWS root domain: `hyuncloudlab.com`

| 변수명 | 로컬 Docker | 로컬 Kubernetes | AWS ECS | Secret 여부 |
|---|---|---|---|---|
| `AUTH_SERVER_ISSUER` | `http://spring-member-gateway:8080` | `http://user.localtest.me` | `https://app.hyuncloudlab.com` | No |
| `USER_FRONTEND_LOGIN_URI` | `http://localhost:5173/auth` | `http://user.localtest.me/auth` | `https://app.hyuncloudlab.com/auth` | No |
| `ADMIN_FRONTEND_LOGIN_URI` | `http://localhost:5176/auth` | `http://admin.localtest.me/auth` | `https://admin.hyuncloudlab.com/auth` | No |
| `BFF_FRONTEND_REDIRECT_URI` | `http://localhost:5173` | `http://user.localtest.me` | `https://app.hyuncloudlab.com` | No |
| `BFF_OAUTH2_AUTHORIZATION_URI` | `http://localhost:5173/oauth2/authorize` | `http://user.localtest.me/oauth2/authorize` | `https://app.hyuncloudlab.com/oauth2/authorize` | No |
| `BFF_OAUTH2_REDIRECT_URI` | `http://localhost:5173/bff/login/oauth2/code/member-bff` | `http://user.localtest.me/bff/login/oauth2/code/member-bff` | `https://app.hyuncloudlab.com/bff/login/oauth2/code/member-bff` | No |
| `BFF_OAUTH2_END_SESSION_URI` | `http://localhost:5173/connect/logout` | `http://user.localtest.me/connect/logout` | `https://app.hyuncloudlab.com/connect/logout` | No |
| `BFF_OAUTH2_LOGOUT_URI` | `http://localhost:5173/logout` | `http://user.localtest.me/logout` | `https://app.hyuncloudlab.com/logout` | No |
| `ADMIN_BFF_FRONTEND_REDIRECT_URI` | `http://localhost:5176` | `http://admin.localtest.me` | `https://admin.hyuncloudlab.com` | No |
| `ADMIN_BFF_OAUTH2_AUTHORIZATION_URI` | `http://localhost:5176/oauth2/authorize` | `http://admin.localtest.me/oauth2/authorize` | `https://admin.hyuncloudlab.com/oauth2/authorize` | No |
| `ADMIN_BFF_OAUTH2_REDIRECT_URI` | `http://localhost:5176/admin-bff/login/oauth2/code/admin-bff` | `http://admin.localtest.me/admin-bff/login/oauth2/code/admin-bff` | `https://admin.hyuncloudlab.com/admin-bff/login/oauth2/code/admin-bff` | No |
| `ADMIN_BFF_OAUTH2_END_SESSION_URI` | `http://localhost:5176/connect/logout` | `http://admin.localtest.me/connect/logout` | `https://admin.hyuncloudlab.com/connect/logout` | No |
| `ADMIN_BFF_OAUTH2_LOGOUT_URI` | `http://localhost:5176/logout` | `http://admin.localtest.me/logout` | `https://admin.hyuncloudlab.com/logout` | No |
| `GATEWAY_CORS_ALLOWED_ORIGIN` | `http://localhost:5173` | `http://user.localtest.me` | `https://app.hyuncloudlab.com` | No |
| `ADMIN_GATEWAY_CORS_ALLOWED_ORIGIN` | `http://localhost:5176` | `http://admin.localtest.me` | `https://admin.hyuncloudlab.com` | No |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/msa` | `jdbc:postgresql://postgres:5432/msa` | RDS endpoint value from SSM/Task Definition | No |
| `SPRING_DATASOURCE_USERNAME` | local value | ConfigMap value | RDS username from SSM/Task Definition | No |
| `SPRING_DATASOURCE_PASSWORD` | `.env.local` | Kubernetes Secret | Secrets Manager or SSM SecureString | Yes |
| `SPRING_DATA_REDIS_HOST` | `redis` | `redis` | ElastiCache endpoint from SSM/Task Definition | No |
| `SPRING_DATA_REDIS_PASSWORD` | `.env.local` if enabled | Kubernetes Secret | Secrets Manager or SSM SecureString | Yes |
| `APP_KAFKA_ENABLED` | `false` by default | `true` for local Kafka manifests | `false` for first AWS cutover | No |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | optional | `kafka.kafka.svc.cluster.local:9092` | unset while Kafka is disabled | No |
| `SPRING_MSA_INTERNAL_API_TOKEN` | `.env.local` | Kubernetes Secret | Secrets Manager or SSM SecureString | Yes |
| `TOSS_API_CLIENT_ID` | `.env.local` | Kubernetes Secret | Secrets Manager or SSM SecureString | Yes |
| `TOSS_API_CLIENT_SECRET` | `.env.local` | Kubernetes Secret | Secrets Manager or SSM SecureString | Yes |

AWS ECS Task Definitions are intentionally not part of this repository state yet. Do not create `docker-compose-aws.yml` for ECS deployment.
