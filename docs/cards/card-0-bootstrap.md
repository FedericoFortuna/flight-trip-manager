# CARD 0 — Bootstrap ejecutable

## Análisis previo

- Alcance: Maven/Java 17, Spring Boot, perfiles, PostgreSQL, Flyway, Compose y Swagger.
- Módulos: configuración transversal y declaración de propiedad de los ocho módulos.
- Entidades existentes: ninguna. No introducir entidades funcionales.
- Reglas: dominio independiente; secretos por entorno; esquema sólo por Flyway;
  tests de persistencia con PostgreSQL real; sin llamadas externas.
- Migración: V1 crea siete esquemas; no tablas, índices o FK funcionales.
- Tests: reloj UTC y prueba de integración de migración, arranque, salud y documentación.
- Riesgos: Docker necesario para integración; bootstrap no verifica aún modelos JPA;
  no auth en MVP local. Error handling, masking y ArchUnit tienen cards siguientes.

## Implementación

Spring Boot 3.5.16, Java 17, Maven Wrapper 3.9.11, PostgreSQL 17.6, Springdoc 2.8.17,
Lombok/MapStruct y JUnit 5 gestionado por Boot. Actuator expone sólo salud. Flyway valida su
historial y Hibernate valida el esquema. Sin generación automática ni H2.
Logging JSON nativo; reloj UTC inyectable; Docker runtime sin root.

## Archivos principales

- `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`.
- `Dockerfile`, `compose.yaml`, `.env.example`.
- `src/main/resources/application*.yml` y `db/migration/V1__create_module_schemas.sql`.
- `FlightTripManagerApplication`, `ApplicationConfiguration`, `BootstrapIT`.
- `README.md` y `docs/architecture.md`.

## Verificación — 2026-09-23

- `mvnw.cmd --batch-mode --no-transfer-progress clean verify`: BUILD SUCCESS.
- Unitarios: 1 aprobado; integración: 5 aprobados; 0 fallos, errores u omitidos.
- PostgreSQL 17.6 real mediante Testcontainers; el test arranca a través del `main`
  real y comprueba Flyway, repetición sin cambios, JPA, salud, Swagger y Actuator.
- JAR ejecutable generado. JaCoCo combinado: 9/9 líneas (100%) y 27/27 instrucciones
  (100%), sin exclusiones; sólo representa las dos clases de arranque existentes.
- Se corrigió la incompatibilidad detectada entre Spring Test de Boot 4 y el override
  a JUnit 5 adoptando Boot 3.5.16 y sus dependencias gestionadas.
- `docker compose config --quiet`: correcto, usando variables efímeras de validación.
- `docker compose ... up --build -d`: construcción y arranque correctos con proyecto
  aislado `flight-trip-manager-card0`, puertos 18080/15432 y credenciales efímeras.
- Smoke test HTTP: salud `UP`, OpenAPI con título esperado y Swagger HTTP 200.
- PostgreSQL confirma V1 aplicada correctamente; backend ejecutándose con UID 999,
  sin privilegios root. El entorno temporal se retira al terminar la validación.
- Dockerfile simplificado: compilación Maven directa sin `dependency:go-offline`,
  cuyo paso previo se demoraba resolviendo dependencias innecesarias para el build.
- No hay tests de arquitectura aún (CARD 0.2).

Estado: CARD 0 validada. El informe JaCoCo no representa cobertura de un producto
funcional: sólo existe configuración de arranque. El gate de cobertura y ArchUnit
siguen en CARD 0.2; errores HTTP uniformes y masking en CARD 0.1. La ventana de
soporte de Boot 3.5 debe revisarse antes de despliegues públicos (ver ADR 001).

## Swagger

`/swagger-ui/index.html` y `/v3/api-docs`, habilitados en local/test y deshabilitados
en prod. No se agregan endpoints de negocio. Salud técnica: `/actuator/health`.

## Reevaluación

Un proceso y una base resuelven el alcance actual. Sin frameworks adicionales de
modularidad, infraestructura distribuida ni proveedores sin consumidores.
Las capas internas se crean cuando tengan contenido, y las pruebas no inventan
dominio para subir cobertura.

## Entrega

- Rama: `feature/bootstrap`.
- Commit sugerido: `chore(bootstrap): initialize modular Spring Boot backend`.
- Próxima card: 0.1 — errores HTTP y observabilidad; luego 0.2 — reglas de arquitectura.
