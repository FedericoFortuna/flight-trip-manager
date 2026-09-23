# CARD 0.2 — Arquitectura y gates de calidad

## Análisis previo

- Alcance: ArchUnit, restricciones por capa/módulo, fixtures negativos y cobertura obligatoria.
- Módulos: tests y build; sin cambios en comportamiento HTTP ni entidades.
- Migración: ninguna; sin tablas, índices o constraints nuevos.
- Reglas: dominio puro, contratos públicos, matriz acíclica, controllers sin persistencia,
  y workflows sin acceso a implementaciones. Calidad global >=80% de líneas.
- Tests: bytecode real de producción, fixtures compilados aislados y pruebas negativas
  del gate Maven. No inventar clases de dominio en producción para ejercitar las reglas.
- Riesgos: evitar reglas vacías que parezcan proteger capas todavía inexistentes,
  excepciones amplias para shared y exclusiones que inflen cobertura.

## Implementación

ArchUnit sólo en test, ejecutado por JUnit 5/Surefire. Importación del directorio de
producción obtenido de la clase principal, excluyendo tests y fixtures por construcción.
Las condiciones recorren todas las clases importadas; no se desactiva globalmente
el fallo por selección vacía. Una aserción exige que esté presente la aplicación.

JaCoCo en verify mide LINE/COVEREDRATIO a nivel BUNDLE con mínimo literal 0.80, sin
exclusiones. Incluye unitarios e integración. Enforcer exige jacoco.exec para evitar
un éxito por omisión de datos. Surefire y Failsafe fallan si no encuentran tests.

## Validación

`mvnw.cmd --batch-mode --no-transfer-progress clean verify`: BUILD SUCCESS (2026-09-23).

- 112 tests aprobados, 0 fallos, 0 errores, 0 omitidos: 100 en Surefire y 12 de
  integración con PostgreSQL real en Failsafe.
- Arquitectura: 10 reglas sobre producción, 31 escenarios ilegales rechazados y
  10 verificaciones de fixtures válidos (51 tests de arquitectura en total).
- Se comprobaron anotaciones Spring/JPA en dominio, HTTP/Jackson/SDK externos,
  persistencia desde application/API, filtración de modelos por contratos,
  repositorios cruzados (incluidos arrays), dependencia inversa, ciclos,
  acceso a internos de shared, workflows y clases fuera de módulo/capa.
- Los fixtures válidos incluyen DTOs con arrays, valores de dominio compartidos,
  implementaciones propias de puertos, controllers por puerto y modelos JPA separados.
- JaCoCo: 118/121 líneas (97,52%), 693/701 instrucciones (98,86%) y 47/48 ramas
  (97,92%). Los tests de arquitectura no añaden líneas de producción ni alteran
  artificialmente estos porcentajes.
- `scripts/verify-quality-gates.ps1`: PASS. El mismo POM copiado a un proyecto
  temporal rechazó una cobertura de 0.03 frente al mínimo 0.80; con JaCoCo
  deshabilitado, Enforcer rechazó la ausencia de jacoco.exec.
- Las pruebas negativas usaron `.tools/quality-gates-42ca876d8ae7406997d2ec2e89728dcd`;
  conservan low-coverage.log y missing-coverage.log sin alterar el reporte principal.
- `git diff --check`: correcto. JAR generado por verify.

## Archivos principales

- `pom.xml`: ArchUnit en test, cobertura mínima, datos requeridos y tests no vacíos.
- `src/test/java/com/flighttripmanager/architecture/ArchitectureRules.java`:
  política ejecutable de paquetes, capas, contratos, entidades y módulos.
- `ArchitectureTest.java`: importación exclusiva del bytecode de producción.
- `ArchitectureRulesTest.java`: compilación de fixtures válidos/ilegales aislados.
- `scripts/verify-quality-gates.ps1`: aceptación negativa reproducible (PowerShell 7).
- `README.md` y `docs/architecture.md`: comandos, límites y ADR 002.

## Base de datos y Swagger

Sin migraciones, tablas, índices o constraints nuevos. Sin endpoints agregados o
modificados; Swagger conserva el comportamiento validado en CARD 0.1.

## Reevaluación y límites

No se agrega código funcional ni infraestructura. Se utiliza ArchUnit core con
JUnit 5 existente; no hace falta otro motor de testing ni framework de modularidad.
El importador no analiza fixtures como producción y ninguna exclusión de cobertura
se agregó. Los módulos aún vacíos no se dan por verificados funcionalmente:
los fixtures prueban el comportamiento de las reglas que los protegerán.

ArchUnit sólo detecta dependencias estáticas: reflexión por strings, SQL dinámico
y semántica de negocio requieren revisión/tests adicionales. bootstrap.configuration
es la raíz de ensamblado; workflows no reciben esa excepción. shared sólo expone
valores de dominio, contratos de aplicación y ApiError para consumidores API.

La aceptación es `clean verify` sin flags de omisión. `test`/`package` o reportes
viejos no prueban el cumplimiento completo. Docker no ejecuta integración durante
el build de imagen; ésta se verifica previamente con Maven y Testcontainers.

Estado: CARD 0.2 implementada y validada.

## Entrega

- Rama: feature/architecture-quality-gates.
- Commit sugerido: test(architecture): enforce module boundaries and coverage gate.
- Próxima card: 1 — catálogo local, primer consumidor funcional de las reglas.
