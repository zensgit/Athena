# Repository Guidelines

## Project Structure & Module Organization
- `ecm-core/` — Spring Boot (Java 17, Maven). Domain code in `src/main/java/com/ecm/core/`, config in `src/main/resources/`, Liquibase in `src/main/resources/db/changelog/`.
- `ecm-frontend/` — React + TypeScript. App under `src/` (components, pages, store slices), static assets in `public/`.
- `docs/` for design/installation. Infra in `docker-compose.yml`, `monitoring/`, and `nginx/`.

## Build, Test, and Development Commands
- Backend (from `ecm-core/`):
  - `mvn clean package` — build jar.
  - `mvn spring-boot:run` — run API at `http://localhost:8080`.
  - `mvn test` — unit/integration tests.
- Frontend (from `ecm-frontend/`):
  - `npm ci` then `npm start` — dev server `http://localhost:3000`.
  - `npm test` — Jest via react-scripts.
  - `npm run lint` / `npm run format` — ESLint/Prettier.
- Full stack: `docker-compose up -d` (API, DB, Elasticsearch, Redis, RabbitMQ, frontend, Nginx, monitoring).

## Coding Style & Naming Conventions
- Java: 4-space indent; packages lowercase, classes `PascalCase`, methods/fields `camelCase`. Use Spring stereotypes (`@Service`, `@Controller`) and Lombok where present.
- TypeScript/React: Prettier + ESLint. Components `PascalCase` (e.g., `DocumentPreview.tsx`), Redux slices `camelCase` (e.g., `authSlice.ts`). Prefer named exports.

## Testing Guidelines
- Backend: Spring Boot Test + Testcontainers. Name `*Test.java`. Use `@SpringBootTest` for integration, `@DataJpaTest` for repositories. Run `mvn test`.
- Frontend: Jest + React Testing Library (react-scripts). Name `*.test.tsx`. Co-locate under `src/`.
- Target meaningful coverage for controllers/services and critical UI flows.

## Commit & Pull Request Guidelines
- Conventional Commits: `feat:`, `fix:`, `chore:`, `docs:`, `refactor:`. Example: `feat(core): add node search endpoint`.
- PRs include: clear summary, linked issues, test output/screenshots, and noted env/config changes.

## Security & Configuration Tips
- Do not commit secrets. Use env vars; see `ecm-core/src/main/resources/application.yml` and `docker-compose.yml` (e.g., DB, Elasticsearch, `REACT_APP_API_BASE_URL`).
- Use Spring profiles (`dev`, `docker`) and frontend `.env` files for local overrides.

## Agent-Specific Instructions
- Keep changes minimal and scoped; avoid breaking public APIs without discussion.
- Update docs/tests with code changes; prefer small, reviewable PRs.
