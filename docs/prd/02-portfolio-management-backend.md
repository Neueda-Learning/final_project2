<!-- generated-by: gsd-doc-writer -->

# PRD 2: User and Portfolio Management Backend

## 1. Ownership

| Item | Details |
|---|---|
| Primary owner | Member 2 (replace with a name before submission) |
| Scope | User context, portfolio APIs, MySQL, backend tests, and shared API-documentation configuration |
| Primary tables | `app_user`, `portfolio` |
| Out of scope | Frontend pages, trading, market data, and valuation implementation |
| Interface contracts | [API Reference](../API.md) / [OpenAPI Specification](../openapi.yaml) |
| Status | Pending implementation |

## 2. Module Goal

Provide the current demonstration-user context and portfolio CRUD, creating a consistent portfolio-ownership boundary for the other three backend modules. Also establish the Spring Boot application, common error response, Swagger UI, and OpenAPI export.

## 3. Functional Scope

- Seeded demonstration user and current-user dependency.
- List, create, retrieve, update, delete empty portfolios, and archive portfolios.
- User-ownership filtering.
- Unique active portfolio names.
- Spring Boot application, Spring MVC controllers, Springdoc metadata, and API-documentation URLs.
- Common `ErrorResponse` and request ID.

## 4. Interfaces

| Method | Path | Success status |
|---|---|---:|
| GET | `/api/v1/portfolios` | 200 |
| POST | `/api/v1/portfolios` | 201 |
| GET | `/api/v1/portfolios/{portfolioId}` | 200 |
| PATCH | `/api/v1/portfolios/{portfolioId}` | 200 |
| DELETE | `/api/v1/portfolios/{portfolioId}` | 204 |
| POST | `/api/v1/portfolios/{portfolioId}/archive` | 200 |

See [API.md](../API.md) for each interface's request, response, errors, and curl examples. The implementation must match [openapi.yaml](../openapi.yaml).

## 5. Business Rules

- Resolve the current user from the server request context; never accept an arbitrary client-supplied user ID.
- The MVP portfolio base currency is fixed to USD.
- Active portfolio names are case-insensitively unique within one user's scope.
- Return 404 for any portfolio not owned by the current user.
- A portfolio with transaction history cannot be hard-deleted; it can only be archived.
- Archived portfolios are excluded from the list by default.

## 6. MySQL Deliverables

- `app_user`
- `portfolio`
- User foreign key and query indexes
- Generated `active_name` column
- User-scoped unique key for active names

Database tests must prove:

- Email is unique and non-null.
- Every portfolio belongs to a valid user.
- A user cannot have two active portfolios with the same name.
- After archiving, a new portfolio can reuse the name.
- Different users can use the same name.

## 7. API Documentation Configuration Responsibility

Member 2 configures:

- Swagger UI: `/docs`
- OpenAPI JSON: `/v3/api-docs`
- API title, version, description, and tag order
- Request-body examples, response examples, and additional error responses

Spring MVC interfaces must declare:

- Request/response Java record DTOs; never expose JPA entities directly
- `@Valid` and Bean Validation field constraints
- Correct HTTP status codes
- Springdoc `@Operation`, `@ApiResponse`, `@Schema`, and examples
- Common error handling through `@RestControllerAdvice`

Members 3–5 maintain schemas and examples for their own interfaces; Member 2 ensures the complete OpenAPI document can be generated and accessed.

## 8. Backend Tests

### Domain and API Tests

- Portfolio CRUD.
- Name conflicts and field validation.
- User isolation and 404 behavior.
- Failure to delete a portfolio with history.
- Default filtering after archive.

### Database Tests

- Foreign keys, unique keys, and generated columns.
- MySQL migrations execute against an empty database.
- When identical names are created concurrently, only one request succeeds.

### API Documentation Tests

- `/v3/api-docs` returns 200.
- `/docs` is accessible.
- Every business route has a tag, success response, and standard error response.
- Diff the generated OpenAPI schema against repository [openapi.yaml](../openapi.yaml).

## 9. Acceptance Criteria

### AC-PM-01: Create Portfolio

A valid request returns 201, the response matches the `Portfolio` schema, and MySQL records the correct user.

### AC-PM-02: Name Conflict

When creating an active portfolio whose name differs only in case, MySQL rejects the duplicate and the API returns 409 `PORTFOLIO_NAME_CONFLICT`.

### AC-PM-03: User Isolation

When another user accesses a portfolio, return 404 without disclosing resource information.

### AC-PM-04: API Documentation

Swagger UI displays request and success/error response examples and supports Try it out against the development API.

## 10. Handoffs

- Provide portfolio interfaces and mock examples to Member 1.
- Provide portfolio-ownership validation dependencies to Members 3–5.
- Merge the OpenAPI tags and error schemas maintained by the four backend members.

## 11. Definition of Done

- Portfolio interfaces, MySQL migrations, and tests all pass.
- Swagger, ReDoc, and OpenAPI JSON are accessible.
- API.md, openapi.yaml, and actual routes agree.
