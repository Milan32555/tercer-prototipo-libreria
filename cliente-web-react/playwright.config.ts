import { defineConfig } from "@playwright/test";
import path from "node:path";
import { fileURLToPath } from "node:url";

const AQUI = path.dirname(fileURLToPath(import.meta.url));

/**
 * Pruebas de extremo a extremo (E2E) del cliente web contra el servidor REAL con MongoDB.
 *
 * Playwright levanta el servidor GraphQL sobre la base libreria_e2e (la vacía y carga los datos de
 * prueba al arrancar) y el cliente web, y recorre todos los casos de uso en escritorio (1366x768)
 * y en celular (390x844), guardando una captura de cada pantalla.
 *
 * Requisitos: MongoDB en localhost:27017, el jar del servidor compilado (mvnw package) y
 * JAVA_HOME apuntando a un JDK 17. El puerto 8081 debe estar libre.
 *   npm run e2e
 */
const JAVA = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, "bin", "java") : "java";
const JAR = path.resolve(AQUI, "../servidor-libreria-mongo/target/ServidorLibreriaMongo-3.0.0.jar");
const ARGUMENTOS = "--libreria.mongo.base=libreria_e2e --libreria.datos-iniciales.reiniciar=true";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1, // todas las pruebas comparten el mismo servidor y la misma base
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [["list"]],
  use: {
    baseURL: "http://localhost:5173",
    locale: "es-CO",
    timezoneId: "America/Bogota",
    trace: "retain-on-failure"
  },
  projects: [
    { name: "escritorio", use: { viewport: { width: 1366, height: 768 } } },
    { name: "celular", use: { viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true } }
  ],
  webServer: [
    {
      command: `"${JAVA}" -Dfile.encoding=UTF-8 -jar "${JAR}" ${ARGUMENTOS}`,
      url: "http://localhost:8081/graphiql",
      reuseExistingServer: false,
      timeout: 90_000
    },
    {
      command: "npx vite --port 5173 --strictPort",
      url: "http://localhost:5173",
      reuseExistingServer: false,
      timeout: 60_000
    }
  ]
});
