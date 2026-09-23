# CARD 1 — Catálogo local

Estado: implementada y validada el 2026-09-23.

## Resumen técnico

Primer módulo funcional: catalog. Consultas locales paginadas de Airport, Airline
y Location; entidades de dominio Java puro separadas de JPA, MapStruct en las
fronteras, contratos públicos y puertos de entrada/salida. No se modifican otros
módulos de negocio ni el contrato de errores compartido.

Location representa ciudades, estaciones de tren y terminales de bus globales.
Airport permanece separado. La carga inicial y sincronización corresponden a
CARD 1.1; no hay escrituras HTTP, providers, scheduler, caché ni datos ficticios
en producción.

## Archivos principales

- `src/main/java/com/flighttripmanager/catalog/domain/model/`: modelos e invariantes.
- `catalog/application/port/in/CatalogLookup.java`: contrato de entrada para API
  y futuros consumidores de otros módulos.
- `catalog/application/usecase/CatalogQueryService.java`: consultas transaccionales
  de sólo lectura, normalización IATA y errores de recursos inexistentes.
- `catalog/application/contract/`: DTOs, filtro y paginación independientes de Spring.
- `catalog/infrastructure/persistence/`: entidades, repositorios, adapter y mapper.
- `catalog/api/controller/CatalogController.java`: seis consultas documentadas.
- `catalog/api/error/CatalogExceptionHandler.java`: 404 con ApiError y Clock UTC.
- `src/main/resources/db/migration/V2__create_catalog_tables.sql`: DDL.
- `src/test/java/com/flighttripmanager/catalog/`: tests de dominio/casos de uso.
- `src/test/java/com/flighttripmanager/integration/CatalogIT.java`: PostgreSQL y HTTP.
- `src/test/resources/catalog-fixtures.sql`: datos ficticios sólo para tests.
- `ArchitectureRules.java` y `ArchitectureRulesTest.java`: habilitan MapStruct
  en application y prueban esa dependencia; contratos y dominio siguen puros.
- README y ADR 003 en `docs/architecture.md`: operación y decisiones.

Las rutas abreviadas `catalog/...` parten de
`src/main/java/com/flighttripmanager/`.

## Base de datos

V2 agrega tres tablas dentro del esquema catalog; V1 permanece intacta:

| Tabla | Datos y restricciones |
| --- | --- |
| airports | UUID PK; IATA obligatorio de 3 letras y único; ICAO opcional de 4 letras y único; nombre, ciudad, país y timezone obligatorios; coordenadas numeric(9,6) opcionales como par y acotadas; active obligatorio; last_synced_at timestamptz opcional |
| airlines | UUID PK; IATA obligatorio alfanumérico de 2 caracteres y único; ICAO opcional de 3 letras y único; nombre y país obligatorios; active obligatorio; last_synced_at timestamptz opcional |
| locations | UUID PK; tipo CITY/TRAIN_STATION/BUS_STATION; nombre, ciudad y país obligatorios; identidad única por tipo, lower(nombre), lower(ciudad), país |

Códigos canónicos en mayúsculas; país de dos letras. Textos no vacíos, con límites
de longitud y sin espacios ASCII externos. Java también rechaza texto sin
normalizar y valida la zona mediante ZoneId. PostgreSQL refuerza formato, nulabilidad,
unicidad y coordenadas; no valida nombres de zonas horarias contra la tzdb de Java.

Índices B-tree: PK/UNIQUE, airports_active_name_id_idx,
airlines_active_name_id_idx, locations_identity_idx y locations_name_id_idx.
No hay relaciones entre módulos, FK ni cascadas en esta card.
Hibernate valida las tablas al arrancar; Flyway valida V1/V2 y una segunda
ejecución no aplica cambios.

## Tests y resultado

Comando ejecutado:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

Resultado: BUILD SUCCESS, 196 tests, 0 fallos, 0 errores, 0 omitidos.

- Surefire: 118 tests, incluidos 18 nuevos de dominio/contratos/casos de uso y
  51 de arquitectura (producción y fixtures positivos/negativos).
- Failsafe: 78 tests con PostgreSQL 17.6 real en Testcontainers; 66 nuevos del
  catálogo y los 12 anteriores de bootstrap/errores.
- CatalogIT cubre búsquedas por campos, mayúsculas y espacios, caracteres
  literales %/_, paginación y desempate UUID, inactivos, datos desconocidos,
  timestamps UTC, tipos de Location, 400/404/405, contratos OpenAPI y 18 casos
  de violaciones de constraints.
- La prueba OpenAPI detectó la ausencia inicial de respuestas 200 en los detalles;
  se documentaron explícitamente y se verifican sus esquemas de respuesta.
- Verificado el JAR: contiene V2 y CatalogController; no contiene fixtures SQL
  ni controladores auxiliares de test.
- `git diff --check`: correcto.

Log local: `.tools/card-1-verify.log`. Reportes en target/surefire-reports,
target/failsafe-reports y target/site/jacoco.

## Cobertura

- Global: 415/430 líneas, **96,51%**; instrucciones **98,13%**; ramas **89,68%**.
- Catalog: 297/309 líneas, **96,12%**.
- Dominio catalog: 36/36 líneas; casos de uso: 17/17 líneas.
- Gate >=80% aprobado, sin nuevas exclusiones ni tests omitidos.

La cobertura de líneas no equivale a probar todas las combinaciones de datos.
Las ramas no cubiertas incluyen protecciones de los mappers generados.

## Swagger y comportamiento HTTP

| Método | Ruta |
| --- | --- |
| GET | /api/v1/airports |
| GET | /api/v1/airports/{iataCode} |
| GET | /api/v1/airlines |
| GET | /api/v1/airlines/{iataCode} |
| GET | /api/v1/locations |
| GET | /api/v1/locations/{id} |

Listados: q opcional hasta 100 caracteres, page desde 0 hasta 1000000,
size desde 1 hasta 100 (default 20). Orden name/id; búsqueda de subcadena
literal, sin distinguir mayúsculas, conservando acentos. Aeropuertos/aerolíneas
filtran active=true por defecto; false devuelve sólo inactivos. Location permite
filtrar type. Detalles por IATA aceptan minúsculas e incluyen inactivos.

Página: items, page, size, totalElements, totalPages. Sin coincidencias: 200 y
lista vacía. Códigos inexistentes: 404 AIRPORT_NOT_FOUND/AIRLINE_NOT_FOUND;
UUID inexistente: 404 LOCATION_NOT_FOUND. Parámetros inválidos: 400.
OpenAPI declara respuestas 200/400/500 y 404 en detalles.

## Riesgos y reevaluación

- Base vacía hasta la carga explícita de CARD 1.1; la consulta nunca consume cuota
  externa ni hace sincronización implícita.
- IATA es obligatorio y único en el modelo actual. Reconsiderar códigos compartidos,
  reasignación histórica y registros sin IATA antes de importar datos de proveedores.
- Identidad de Location por nombre/ciudad/país es mínima: los homónimos reales
  requerirán una identidad externa o desambiguación geográfica. No asumir que una
  coincidencia textual basta para fusionar datos de proveedores.
- La búsqueda de subcadena puede recorrer tablas completas. Se eligieron índices
  de identidad/orden sin agregar extensiones PostgreSQL o infraestructura de búsqueda;
  medir antes de optimizar. Paginación por offset no mantiene una foto entre consultas.
- No hay validación contra un catálogo ISO de países ni control de frescura de datos;
  lastSyncedAt null indica desconocido. No inventar una timezone para importar
  un aeropuerto cuyo dato falta.
- API local sin autenticación, igual que las cards anteriores.

Se mantuvo un único puerto de consultas cohesivo y un adapter de persistencia,
sin CRUD genérico ni interfaces vacías. MapStruct no habilita dependencias de
persistencia/HTTP en application. No se introdujeron dependencias Maven nuevas.

## Entrega

- Rama: `feature/local-catalog`.
- Commit sugerido: `feat(catalog): add local airport airline and location queries`.
- No se creó commit ni se publicó la rama.
- Próxima card: **1.1 — carga/sincronización del catálogo**, para disponer de datos
  reales reutilizables antes de implementar viajes y referencias a aeropuertos.
