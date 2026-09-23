# CARD 0.1 — Errores HTTP y observabilidad

## Análisis previo

- Alcance: contrato HTTP uniforme, errores MVC/servlet, logging JSON con masking
  y contexto de petición. No introduce endpoints de negocio.
- Módulos: shared (API e infraestructura), bootstrap (documentación OpenAPI).
- Entidades y migraciones: ninguna; sin tablas, índices o constraints nuevos.
- Reglas: no reflejar valores rechazados, mensajes de excepciones, headers ni
  payloads. Mantener status y headers HTTP de Spring. Timestamp con Clock UTC.
- Tests: validación, errores de parsing/routing/media type, fallos inesperados,
  dispatch servlet, masking, JSON, MDC y regresión de integración con PostgreSQL.
- Deuda a evitar: errores de dominio genéricos sin casos reales y logging de cuerpos.

## Diseño

ApiError: code, message, details y timestamp. Advice adapta errores de Spring;
fallback servlet usa el mismo factory. Se mantienen headers como Allow. Un catch
de Exception está limitado a la frontera HTTP para devolver 500 seguro.

Logging mediante formatter de Spring Boot, sin nuevas librerías. Redacción completa
de campos etiquetados sensibles (PNR, booking reference, ticket, API key, tokens,
password, secret y Authorization). Se omiten mensajes/causas/stacktraces de Throwable
y campos estructurados/MDC fuera de una allowlist. No se serializan objetos arbitrarios.
Los logs de aplicación deben usar mensajes constantes y metadatos permitidos;
el masking textual no puede reconocer un secreto sin etiqueta. Nunca loggear cuerpos,
headers, URLs de proveedores con secretos o DTOs completos.

Cada petición síncrona recibe X-Request-Id generado internamente. Se registra método
y patrón de ruta declarado (no URL/query real), status y duración. El MDC se restaura
al terminar, incluso ante fallos. Soporte async requerirá una card si se introduce.

## Validación

`mvnw.cmd --batch-mode --no-transfer-progress clean verify` — BUILD SUCCESS (2026-09-23).

- 49 tests unitarios/MVC y 12 tests de integración: 61 aprobados, 0 fallos,
  0 errores y 0 omitidos. Integración con PostgreSQL 17.6 vía Testcontainers.
- Regresión de CARD 0 incluida: migración, JPA, salud, Swagger y Actuator.
- HTTP real: 404, 500, validación de parámetro, dispatch servlet, acceso directo
  a /error y respuesta 406 en JSON. Controller de prueba fuera del paquete de
  aplicación y exclusivamente en src/test, no se incorpora al JAR.
- Masking de mensajes parametrizados, JSON, credenciales y Authorization; exclusión
  de campos/objetos no permitidos; MDC restaurado incluso ante fallo de servlet.
- OpenAPI: esquema ApiError y respuestas genéricas sin sobrescribir contratos
  específicos. El endpoint /error permanece oculto en Swagger.
- JaCoCo sin exclusiones: 118/121 líneas (97,52%), 693/701 instrucciones (98,86%)
  y 47/48 ramas (97,92%). No se agregan tests artificiales del fallback de serialización.
- `git diff --check`: correcto. JAR ejecutable generado por verify.
- No se agregan migraciones, tablas, índices ni constraints.
- Arquitectura y gate de cobertura: CARD 0.2, todavía no implementados.

## Archivos principales

- `shared/api/error/ApiError.java`, `ApiErrorFactory.java`, `ApiExceptionHandler.java`
  y `ApiErrorController.java`: contrato y tratamiento uniforme de errores.
- `shared/infrastructure/logging/SensitiveDataMasker.java`,
  `SafeStructuredLogFormatter.java` y `RequestLoggingFilter.java`.
- `bootstrap/configuration/ApplicationConfiguration.java`: schema y errores OpenAPI.
- `src/main/resources/application.yml`: formatter para consola y archivos.
- `src/test/java/.../ApiErrorsTest.java`, `SafeLoggingTest.java`,
  `RequestLoggingFilterTest.java`, `BootstrapIT.java` y fixtures de testsupport.
- README actualizado con contrato, uso y límites del masking.

## Reevaluación y riesgos

La lógica transversal permanece en shared/api y shared/infrastructure, sin nuevas
dependencias de dominio ni infraestructura externa. Spring conserva la semántica
HTTP y se evita duplicar un handler por cada excepción del framework. Sólo se
agrega el catch genérico justificado en la frontera HTTP.

El masking es defensa adicional: un secreto sin etiqueta no puede reconocerse
automáticamente. Omitir stacktraces y mensajes de Throwable reduce capacidad de
diagnóstico; se conservan tipo de error y requestId. El filtro cubre peticiones
síncronas; no habilitar async sin adaptar propagación y finalización del contexto.
Los códigos específicos de dominio se añadirán con sus módulos; hoy no existen
excepciones de negocio ni respuestas que simulen entidades aún no implementadas.

Estado: CARD 0.1 implementada y validada.

## Entrega

- Rama: feature/api-errors-observability. Cambios de CARD 0 conservados sin commit.
- Commit sugerido: feat(shared): standardize API errors and redact structured logs.
- Próxima card: 0.2 — ArchUnit y gate de cobertura global.
