# CARD 1.1 — Importación y sincronización explícita del catálogo

Estado: implementada y validada el 2026-09-24.

## Resumen técnico

Se implementa POST /api/v1/catalog/imports para cargar lotes JSON de aeropuertos,
aerolíneas y ubicaciones. El caso de uso CatalogImportService recibe contratos
internos mediante CatalogImport; CatalogImportStore encapsula la persistencia.
Los mapeos HTTP/aplicación/dominio/JPA usan MapStruct. Se modifica únicamente
el módulo catalog, sus tests, migración y documentación.

Cada lote de 1..500 registros se valida y aplica en una transacción. La identidad
es tipo + source + externalId. El servidor asigna UUID, lo conserva al actualizar
y no asume que un código IATA o nombre compartido identifica el mismo registro.
Los registros omitidos permanecen intactos; no hay borrado ni desactivación implícita.

## Archivos principales

- `src/main/java/com/flighttripmanager/catalog/application/port/in/CatalogImport.java`:
  entrada reutilizable por HTTP y futuros adapters de fuentes externas.
- `catalog/application/usecase/CatalogImportService.java`: validación, normalización,
  preservación de identidad, control de versión y transacción.
- `catalog/application/contract/CatalogImportBatch.java` y contratos relacionados:
  entrada/salida sin dependencias de frameworks.
- `catalog/application/mapper/CatalogImportDomainMapper.java`: construcción de dominio.
- `catalog/infrastructure/persistence/adapter/JpaCatalogImportAdapter.java`:
  bloqueo, lecturas por origen, persistencia y traducción de conflictos.
- `catalog/infrastructure/persistence/mapper/CatalogImportPersistenceMapper.java`:
  mapeo de valores y metadatos hacia JPA.
- `catalog/api/controller/CatalogImportController.java`: nuevo endpoint.
- `catalog/api/request/CatalogImportRequest.java`, mapper y response:
  contrato HTTP; rechaza campos desconocidos.
- `src/main/resources/db/migration/V3__add_catalog_import_identity.sql`.
- `src/test/java/com/flighttripmanager/catalog/CatalogImportServiceTest.java`.
- `src/test/java/com/flighttripmanager/integration/CatalogImportIT.java`.
- `examples/catalog/argentina-starter.json` y su README: carga mínima explícita.

Las rutas abreviadas catalog/... parten de src/main/java/com/flighttripmanager/.
README y ADR 004 documentan operación, decisiones y límites.

## Base de datos

V3 agrega source varchar(64), external_id varchar(100) y source_observed_at timestamptz
a las tres tablas existentes. Agrega last_synced_at timestamptz a locations;
airports/airlines ya lo tenían. No crea tablas ni modifica V1/V2.

Cada tabla incorpora UNIQUE(source, external_id), con su índice B-tree, y un
CHECK de metadatos completos: origen canónico, identificador no vacío sin caracteres
de control, fechas coherentes y last_synced_at obligatorio para registros importados.
Se conservan PK UUID, restricciones IATA/ICAO e identidad natural de Location.

V3 preserva filas existentes y deja su origen desconocido en null. La API no se
apropia automáticamente de estas filas; una coincidencia exige reconciliación
explícita fuera de esta card. No hay cascadas ni relaciones entre módulos.

Un advisory lock PostgreSQL transaccional no bloqueante serializa importaciones
entre conexiones/instancias. Se libera en commit o rollback. Si está ocupado,
la respuesta es 409 CATALOG_IMPORT_BUSY y se puede reintentar el mismo lote.
Las escrituras SQL manuales no participan de este protocolo.

## Versiones, normalización y errores

- source: namespace de 1..64 caracteres ASCII permitido, normalizado a minúsculas.
- externalId: 1..100 caracteres, recorte de espacios, sensible a mayúsculas;
  único por tipo/origen. No derivarlo automáticamente del nombre.
- IATA/ICAO/país en mayúsculas; textos recortados. Dominio valida longitudes,
  códigos, pares/rangos de coordenadas y timezone reconocida por Java.
- Coordenadas de hasta seis decimales efectivos; no se redondean datos inválidos.
- observedAt: instante real de observación/revisión, entre 1970 y el reloj actual,
  hasta microsegundos. Se persiste como source_observed_at.
- lastSyncedAt: reloj UTC del servidor truncado a microsegundos.
- Versión idéntica y valores normalizados iguales: UNCHANGED sin escrituras.
- Versión anterior: 409 CATALOG_STALE_DATA; misma versión con otros valores:
  409 CATALOG_VERSION_CONFLICT.
- Versión posterior: UPDATED, incluso cuando sólo avanza la fecha de observación.
- Identidad/códigos en conflicto: 409 CATALOG_IDENTITY_CONFLICT.
- Datos inválidos: 400 CATALOG_INVALID_BATCH o el error MVC estándar cuando
  falla la deserialización/validación HTTP. No se devuelven valores rechazados
  ni mensajes SQL al cliente.

El lote es completo para cada registro suministrado: omitir/null ICAO o coordenadas
borra esos atributos opcionales. active es obligatorio para Airport/Airline.
El endpoint no descarga datos, no hace scraping ni consulta proveedores.

## Swagger

Nuevo POST /api/v1/catalog/imports, application/json.
Request: source, observedAt, airports, airlines, locations.
Respuesta 200: created, updated, unchanged, items[{kind,externalId,id,status}].
Se documentan 400/409/500 con ApiError. Las seis consultas GET anteriores mantienen
sus rutas y respuestas.

## Tests y cobertura

Aceptación: `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify`.
Resultado final (2026-09-24): **BUILD SUCCESS**, 264 tests, 0 fallos,
0 errores y 0 omitidos.

- Surefire: 146 tests; incluye 28 nuevos del importador y 51 de arquitectura.
- Failsafe: 118 tests con PostgreSQL real; incluye 40 de importación,
  66 de consultas del catálogo y 12 de bootstrap/errores.
- Cobertura global: 802/831 líneas, **96,51%**; instrucciones **97,86%**;
  ramas **80,36%**. CatalogImportService: 81/81 líneas.
- Gate >=80% aprobado sin nuevas exclusiones ni omisión de tests.
- Verificado el JAR: incluye V3 y CatalogImportController; no incluye
  fixtures, controladores auxiliares de test ni el archivo de ejemplo.
- `git diff --check`: correcto.

La cobertura de líneas no representa cobertura de todas las combinaciones posibles.

Las pruebas nuevas cubren reloj inyectable, normalización, validación previa a
persistencia, idempotencia, actualizaciones y UUID estables, versiones antiguas,
conflictos, reversión de inserciones/actualizaciones previas, bloqueo entre
conexiones, registros legados, constraints SQL, OpenAPI, migración V2→V3 y
el archivo de carga documentado. Los tests de arquitectura existentes siguen
vigentes sin relajar reglas.

La integración utiliza PostgreSQL real en Testcontainers. No se requieren
cuentas, API keys ni disponibilidad de proveedores externos. El primer intento
de cierre encontró Docker apagado; se inició antes de repetir la aceptación.
Reportes: target/surefire-reports, target/failsafe-reports, target/site/jacoco;
log local: .tools/card-1.1-verify.log.

## Riesgos y reevaluación

- Importación explícita y local, sin autenticación según el alcance del MVP.
  El límite de 500 registros no es un límite de bytes del cuerpo HTTP.
- La fuente declara observedAt y externalId: la aplicación no verifica su
  autenticidad ni detecta que un proveedor haya reutilizado una identidad.
- Sólo una importación simultánea; inserciones y flush por registro son adecuados
  para estos lotes pequeños. Medir antes de introducir procesamiento asíncrono,
  importaciones masivas, batch SQL o jobs.
- UUID estable al cambiar IATA; códigos compartidos y reasignación entre identidades
  distintas siguen provocando conflicto. No hay resolución/fusión automática.
- Locations homónimas siguen sujetas a la identidad natural mínima de CARD 1.
  Requieren desambiguación antes de importar un catálogo geográfico mundial.
- Se conservan los últimos metadatos de origen; no hay historial de importaciones
  ni auditoría completa por lote en esta card.
- No se configuró un proveedor externo ni una sincronización programada. La
  abstracción de entrada permite agregarlos cuando se elija una fuente concreta.
- El archivo inicial contiene sólo tres registros curados con fuentes documentadas;
  no representa cobertura mundial ni estado operativo en tiempo real.

Se reutilizan Spring/JPA/PostgreSQL existentes. No hay nuevas dependencias Maven,
colas, cachés o infraestructura adicional; dominio y contratos permanecen puros.

## Entrega

- Rama: `feature/catalog-import-sync`.
- Commit sugerido: `feat(catalog): add atomic source-aware catalog imports`.
- No se crea commit ni se publica la rama como parte de esta card.
- Próxima card: **2 — gestión de viajes y tramos**, usando los UUID del catálogo
  para referencias globales. La integración de proveedores concretos puede
  incorporarse luego sin cambiar el contrato de los consumidores del catálogo.
