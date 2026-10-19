/**
 * This suite does NOT start docker-compose / mock-backends / the backend — they're three
 * independent processes (Postgres container, two Maven modules) and orchestrating that from
 * Playwright would just duplicate ../README.md's "Run it" section badly. Instead, fail fast
 * here with a clear pointer back to it, rather than letting every single test time out one by
 * one with a generic ECONNREFUSED.
 */
const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080';
const MOCKS_URL = process.env.MOCKS_URL ?? 'http://localhost:8081';

async function check(name: string, url: string) {
  try {
    const res = await fetch(url);
    if (!res.ok && res.status !== 404) {
      throw new Error(`${name} responded with HTTP ${res.status}`);
    }
  } catch (err) {
    throw new Error(
      `\n\n${name} is not reachable at ${url} (${(err as Error).message}).\n` +
        `Start the prototype stack first — see prototype/README.md "Run it":\n` +
        `  1. docker compose up -d        (Postgres, port 55432)\n` +
        `  2. cd mock-backends && mvn spring-boot:run   (port 8081)\n` +
        `  3. cd backend && mvn spring-boot:run         (port 8080)\n`,
    );
  }
}

export default async function globalSetup() {
  await check('backend (/actuator/health)', `${BACKEND_URL}/actuator/health`);
  await check('mock-backends (/commerce-core/inventory/SOF-1001)', `${MOCKS_URL}/commerce-core/inventory/SOF-1001`);
}
