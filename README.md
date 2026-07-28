# Portfolio Manager

A portfolio-management MVP for stocks and ETFs. The project combines a React and
TypeScript client, a Java 21 Spring Boot API, an independent Spring market-data
worker, and MySQL 8.

## Project structure

```text
frontend/             React and Vite client
backend/              Spring MVC API, domain model, repositories, and services
worker/               Scheduled market-data synchronization process
db/                   Database initialization and seed data
docs/                 Architecture, product requirements, OpenAPI, and schema docs
e2e/                  End-to-end test workspace
infra/                Container and deployment configuration
```

## Start with Docker

Copy the environment file:

```bash
cp .env.example .env
```

The default provider is the live Twelve Data REST API. Configure a real key before
starting:

```text
MARKET_DATA_PROVIDER=twelve-data
TWELVE_DATA_API_KEY=replace_with_your_api_key
```

Then start the complete environment:

```bash
docker compose up --build
```

The `fixture` provider is intended only for automated tests and offline demos. Run
the live provider integration test separately after configuring a key:

```bash
mvn -pl worker -Dtest=TwelveDataLiveIntegrationTest test
```

Service endpoints:

- Frontend: http://localhost:5173
- API: http://localhost:8000
- Swagger UI: http://localhost:8000/docs
- OpenAPI JSON: http://localhost:8000/v3/api-docs

## Start without Docker

Backend:

```bash
mvn -pl backend spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

## Validate

```bash
mvn test
cd frontend
npm test -- --run
npm run lint
npm run build
```

The canonical API contract is [docs/openapi.yaml](docs/openapi.yaml). The database
definition is [docs/database/schema.sql](docs/database/schema.sql). The current
baseline uses Spring Boot 4.1.0 and Java 21.
