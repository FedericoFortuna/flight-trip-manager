# Flight Trip Manager

Backend de planificación, gestión y seguimiento de viajes aéreos. Monolito modular
hexagonal, sin frontend ni autenticación en el MVP local.

## Estado

CARD 0 implementa la infraestructura de arranque y CARD 0.1 agrega errores HTTP
uniformes y observabilidad. CARD 0.2 exige límites arquitectónicos y cobertura mínima.
CARD 1 incorpora el catálogo local de aeropuertos, aerolíneas y ubicaciones:
seis consultas `/api/v1`, entidades JPA separadas del dominio y migración V2.
No hay integraciones externas activas ni carga inicial de datos.
Los contratos y paquetes internos se incorporan por cards, evitando clases vacías.

Validación de CARD 0 (2026-09-23): `clean verify` correcto (1 test unitario y 5 de
integración con PostgreSQL), imagen Docker construida y arranque de Compose
comprobado. Cobertura del código de arranque: 100% de líneas; no hay aún lógica
funcional. Detalle y limitaciones en la documentación de la card.

## Requisitos y versiones

- JDK 17 para desarrollo local.
- Maven 3.9.11 vía Maven Wrapper (no requiere Maven instalado).
- Docker con motor Linux y Docker Compose v2 para base de datos e integración.
- Spring Boot 3.5.16, Springdoc 2.8.17, PostgreSQL 17.6.
- JUnit 5, Mockito y Testcontainers con versiones gestionadas por Spring Boot.
- MapStruct 1.6.3 y Lombok preparados como annotation processors.

La primera compilación necesita Internet para descargar Maven y dependencias.
Docker también descarga imágenes si no están disponibles localmente.

## Arranque con Docker Compose

1. Copiar `.env.example` a `.env`.
2. Definir `DB_PASSWORD` con una contraseña local propia, no vacía.
3. Ejecutar:

```sh
docker compose up --build
```

Después de la primera construcción también puede usarse `docker compose up`.
Compose espera a que PostgreSQL esté disponible. El backend ejecuta Flyway antes
de inicializar JPA y usa un usuario Linux sin privilegios dentro del contenedor.
Los puertos publicados están restringidos a `127.0.0.1`.

- Salud del backend y base: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI: http://localhost:8080/v3/api-docs

Swagger documenta las seis consultas del catálogo. Actuator no publica datos
de configuración ni detalles de conexión. `docker compose down` conserva los
datos en el volumen; `docker compose down -v` los elimina deliberadamente.
Cambiar credenciales de `.env` no modifica un usuario en un volumen ya creado.

## Desarrollo con Java local

Levantar PostgreSQL con `docker compose up -d postgres`. Luego exportar las
credenciales que se hayan elegido en `.env`. Spring Boot no carga `.env`
automáticamente: ese archivo es consumido por Compose.

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:DB_USERNAME = 'flight_trip_manager'
$env:DB_PASSWORD = Read-Host 'Contraseña local' -MaskInput
.\mvnw.cmd spring-boot:run
```

En Linux/macOS usar `sh ./mvnw spring-boot:run` y variables de entorno equivalentes.
Si cambió `DB_PORT` o `DB_NAME`, configurar también `DB_URL`.

| Variable | Uso |
| --- | --- |
| `DB_USERNAME` | Usuario PostgreSQL; obligatorio |
| `DB_PASSWORD` | Contraseña PostgreSQL; obligatoria y sin default |
| `DB_URL` | JDBC URL; obligatoria salvo URL local por defecto o Compose |
| `DB_POOL_SIZE` | Máximo de conexiones; default 10 |
| `SPRING_PROFILES_ACTIVE` | `local`, `test` o `prod`; sin perfil local implícito |
| `SERVER_PORT` | Puerto interno de Spring Boot; default 8080 |
| `DB_NAME` | Nombre de base en Compose; default `flight_trip_manager` |
| `DB_PORT` | Puerto publicado de PostgreSQL; default 5432 |
| `BACKEND_PORT` | Puerto publicado del backend; default 8080 |

Los secretos se suministran por entorno. `.env` está excluido de Git y del contexto
Docker. No configurar credenciales en YAML ni registrar headers o payloads sensibles.

## Arquitectura y base de datos

Un proyecto Maven, un proceso y una base. Módulos: `trips`, `flights`,
`flightsearch`, `tracking`, `connections`, `budgets`, `catalog`, `shared`.
Ver [arquitectura](docs/architecture.md) y [CARD 0](docs/cards/card-0-bootstrap.md).

Cada módulo funcional incorporará `domain`, `application`, `infrastructure` y
`api`. El dominio será Java puro. Los módulos se comunican por puertos y contratos;
no por entidades JPA ni repositorios ajenos. `bootstrap` configura y coordina.

Flyway ejecuta V1 (siete esquemas funcionales) y V2 (tablas `catalog.airports`,
`catalog.airlines` y `catalog.locations`). `shared` no posee tablas. El historial de
Flyway vive en `public`. Las próximas migraciones usan números globales crecientes.
Nunca modificar una migración aplicada: agregar otra.

Hibernate usa `ddl-auto=validate` en todos los perfiles, con Open Session in View
deshabilitado y timestamps JDBC en UTC. No se usa H2. Un `Clock` UTC es inyectable.
El logging de aplicación es JSON mediante el formatter seguro de Spring Boot,
tanto en consola como en archivos si se configuran.

`prod` mantiene Swagger deshabilitado y exige configuración externa de base.
Ese perfil no convierte todavía al MVP sin autenticación en un servicio público.

## Tests y cobertura

```powershell
# Unitarios; no necesita Docker
.\mvnw.cmd --batch-mode --no-transfer-progress test

# Compilación, unitarios, empaquetado e integración con PostgreSQL real
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

Surefire ejecuta `*Test`; Failsafe ejecuta `*IT` durante `verify`. Si falta Docker,
la integración falla: no se omite silenciosamente. Testcontainers utiliza una
base efímera independiente de Compose y de cualquier base personal.

Los tests verifican reloj UTC, migración y repetición sin cambios, arranque JPA,
salud de la base, disponibilidad de Swagger y ausencia de endpoints Actuator
sensibles. CARD 0.1 agrega pruebas de errores HTTP, dispatch servlet, negociación
de contenido, redacción de logs, MDC y esquema OpenAPI. Los endpoints de prueba
están fuera del paquete de aplicación y no se incluyen en el JAR.
Informes: `target/surefire-reports`, `target/failsafe-reports` y
`target/site/jacoco/index.html`. JaCoCo mide unitarios e integración.

`clean verify` exige cobertura global de líneas >=80% con JaCoCo, combinando tests
unitarios e integración y sin exclusiones configuradas. Maven falla si falta
`target/jacoco.exec`, si no encuentra tests unitarios/de integración o si ArchUnit
detecta una infracción. CARD 1 prueba invariantes del catálogo, consultas y errores
por HTTP real, mapeos, restricciones SQL y contratos OpenAPI.

ArchUnit analiza sólo bytecode de producción. Las reglas de dominio, capas,
contratos, controllers, entidades JPA y módulos se ejecutan con los unitarios.
Los fixtures positivos y negativos se compilan en un directorio temporal; no
entran al JAR ni al cálculo de cobertura. También cubren arrays y tipos de SDK externos.

Comprobación opcional de rechazo de los gates (PowerShell 7 en Windows, sin Docker):

```powershell
.\scripts\verify-quality-gates.ps1
```

El script copia el POM a un proyecto aislado bajo `.tools/quality-gates-*`, comprueba
que falla con cobertura insuficiente y con datos ausentes, y conserva los logs.
No modifica fuentes ni reportes del proyecto principal. Para aceptar una card usar
siempre `clean verify` sin flags que omitan tests/instrumentación; `test` y `package`
por sí solos no ejecutan el gate final. Los reportes viejos no constituyen evidencia.

## Proveedores y límites actuales

No se consume ninguna API externa en esta card ni se requieren API keys.
Duffel, AirLabs y OpenSky se incorporarán detrás de puertos, con mocks y pruebas
que no dependan de servicios reales. No se permite scraping ni ofertas de OTAs.
Caffeine, resiliencia y scheduler se añadirán cuando exista su primer consumidor.

Próxima card: 1.1 — carga/sincronización del catálogo. No hay jobs ni endpoints
para gestionar viajes todavía.

## Catálogo local (CARD 1)

| Método | Ruta | Consulta |
| --- | --- | --- |
| GET | `/api/v1/airports` | Aeropuertos por código, nombre, ciudad o país |
| GET | `/api/v1/airports/{iataCode}` | Aeropuerto por IATA de 3 letras |
| GET | `/api/v1/airlines` | Aerolíneas por código, nombre o país |
| GET | `/api/v1/airlines/{iataCode}` | Aerolínea por IATA de 2 caracteres alfanuméricos |
| GET | `/api/v1/locations` | Ciudades/estaciones por nombre, ciudad o país |
| GET | `/api/v1/locations/{id}` | Ubicación por UUID |

Listados: `q` opcional (máximo 100 caracteres, se recortan espacios), `page=0`
(0..1000000) y `size=20` (1..100). Búsqueda de subcadena literal sin distinguir
mayúsculas; no elimina acentos. Orden fijo por nombre y UUID.
Respuesta: `items`, `page`, `size`, `totalElements`, `totalPages`.
Una página vacía devuelve 200 y conserva el total de coincidencias.

Aeropuertos/aerolíneas: `active=true` por defecto; `false` lista sólo inactivos.
El detalle incluye inactivos para referencias históricas y acepta IATA en minúscula.
Ubicaciones: filtro opcional `type=CITY|TRAIN_STATION|BUS_STATION`.
Los aeropuertos no son locations. País usa código de dos letras en mayúsculas.

Ejemplos: `GET /api/v1/airports?q=buenos&size=10` y
`GET /api/v1/locations?type=TRAIN_STATION&q=central`.
Recurso inexistente: 404 con `AIRPORT_NOT_FOUND`, `AIRLINE_NOT_FOUND` o
`LOCATION_NOT_FOUND`; parámetros inválidos: 400 con el contrato de error común.

La base nueva queda vacía deliberadamente: no hay semillas ficticias de producción,
POST, PUT, DELETE ni sincronización implícita al consultar. Las fixtures viven
en `src/test/resources` y sólo se cargan en PostgreSQL efímero de Testcontainers.
Los timestamps desconocidos y coordenadas desconocidas se conservan como null.
Decisiones, migración y evidencia: [CARD 1](docs/cards/card-1-local-catalog.md).

## Errores HTTP y logs

Los errores MVC y el fallback del servlet usan `application/json`, incluso ante
un `Accept` no soportado. El status HTTP se conserva; por ejemplo, 400 para datos
inválidos, 404 para recursos inexistentes, 405 para métodos no permitidos, 406 para
formatos de respuesta no soportados y 500 para errores inesperados.

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": {"name": "Invalid value"},
  "timestamp": "2026-09-23T12:00:00Z"
}
```

`details` nunca incluye valores rechazados ni mensajes de excepciones. Los mensajes
de validación son deliberadamente genéricos; reglas específicas incorporarán mensajes
seguros cuando exista dominio. Los headers de protocolo, como `Allow`, se conservan.
OpenAPI incorpora `ApiError` y respuestas comunes 400/500 sin sobrescribir las
respuestas específicas de cada endpoint. `/error` es infraestructura y no se muestra
como operación pública en Swagger.

Cada petición recibe `X-Request-Id` generado internamente. Los logs de finalización
incluyen `requestId`, `module`, `operation`, `durationMs`, `status` y, cuando aplica,
`errorCode`. `operation` utiliza el patrón de ruta, sin valores de path ni query.
Los casos de uso podrán agregar `provider`, `entityId` y metadatos permitidos.

Se redactan completamente valores etiquetados PNR, booking reference, ticket,
API key, tokens, password, secret y Authorization con `[REDACTED]`. El formatter
omite campos estructurados/MDC no permitidos, objetos arbitrarios y mensajes o
stacktraces de excepciones; conserva el tipo de error para diagnóstico.
Esto reduce detalle diagnóstico deliberadamente. No reemplaza la regla de nunca
loggear cuerpos, headers, DTOs o secretos sin etiqueta. El MDC se restaura al
terminar cada petición síncrona; async requerirá adaptación explícita.

Decisiones y resultados: [CARD 0.1](docs/cards/card-0.1-errors-observability.md).

Reglas y gates: [CARD 0.2](docs/cards/card-0.2-architecture-quality-gates.md).
