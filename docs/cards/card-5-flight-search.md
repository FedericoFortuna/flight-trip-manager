# CARD 5 — Búsqueda y comparación de vuelos

## Resultado y decisiones

Búsqueda one-way/round-trip mediante puerto independiente, Duffel test y mock
explícito. Hasta siete alternativas con ida y regreso desplazados juntos, según
confirmación del usuario, conservando la estancia. Se prioriza fecha original,
luego -1/+1, -2/+2, -3/+3. Fechas fuera de hoy..365 días se omiten con advertencia.

Filtros de aerolínea comercial de todos los segmentos, máximo de escalas,
ventana de salida local (admite cruce de medianoche), equipaje facturado para
todos los pasajeros/segmentos, origen/destino exactos y aeropuertos excluidos,
incluidas escalas técnicas. Duración máxima y escalas se aplican por sentido.
Duración usa instantes UTC e incluye espera de conexiones. Horas locales ambiguas
o inexistentes por DST se descartan con advertencia; no se inventa un offset.

No hay scraping, OTAs, compra, Booking, guardado de alternativas ni multicity.
Las ofertas mantienen identidad de proveedor sin deduplicación. Precios incluyen
el total informado para todos los pasajeros/sentidos; no se dividen por segmento.
amountUsd sólo coincide con amount cuando currency=USD; otra moneda queda null.
Asiento y traslado son costos faltantes; equipaje también si no consta incluido.
UNKNOWN es distinto de NOT_INCLUDED. No se presupuestan extras desconocidos.

## Ranking balance-v1

Menor score es mejor. Agrupación por moneda, USD primero y otras monedas por
código. No existe un orden económico global entre monedas sin conversión fiable.
Para cada moneda se normalizan precio y duración total entre mínimo y máximo
de los resultados filtrados; si máximo=mínimo, el componente es cero.

score = 40 × precio normalizado + 25 × duración normalizada + 15 × escalas
+ 10 × sentidos con salida antes de 06:00 o desde 22:00
+ 10 si equipaje no está incluido + 5 × categorías de costo faltantes.

Pesos configurables mediante flight-search.price-weight, duration-weight,
stops-weight, schedule-weight, baggage-weight y missing-costs-weight (0..1000;
al menos uno positivo). Spring admite sus equivalentes de variables de entorno.
Cada resultado expone penalties; score tiene cuatro decimales. Empates por
importe, proveedor e identidad de oferta. No se usan IA ni costos inferidos.
Cambiar pesos requiere reiniciar; el caché es local al proceso.

## Arquitectura y archivos

- flightsearch/domain/model: SearchCriteria, SearchFilters, SearchSegment,
  SearchSlice, SearchOffer, SearchRanking y enums/validaciones.
- flightsearch/application: FlightSearch, SearchService, contratos, mapper y
  puertos FlightSearchProvider, SearchAirports, SearchHistoryStore, SearchSnapshots.
- flightsearch/infrastructure/providers: DuffelSearchProvider, DuffelOfferReader,
  DuffelHttp, ProviderCallGuard, BoundedBodySubscriber y MockSearchProvider.
- flightsearch/infrastructure/catalog: adaptador del contrato público CatalogLookup.
- flightsearch/infrastructure/cache: CaffeineSearchSnapshots, exclusión de cargas
  duplicadas y límite de búsquedas distintas en ejecución.
- flightsearch/infrastructure/persistence: entidad, repositorio y adaptador de historial.
- flightsearch/api: controller, DTOs, MapStruct, lectura JSON estricta y ApiError.
- application.yml/application-local.yml, .env.example y compose.yaml: modos y límites.
- pom.xml: Caffeine, versión administrada por Spring Boot; no servicio nuevo.
- SearchDomainTest, DuffelProviderTest, SearchServiceTest, SearchConfigurationTest,
  FlightSearchIT y ajuste de cantidad de migraciones en BootstrapIT.
- README y ADR 008 describen contrato, operación y decisiones.

Todas las rutas Java anteriores parten de src/main/java/com/flighttripmanager.
No se mantienen transacciones de base de datos abiertas durante llamadas HTTP.
El historial usa una transacción corta antes de comenzar las consultas.

## Migración

V7__create_search_criteria_history.sql agrega flightsearch.search_history:
UUID PK, created_at timestamptz y criteria JSONB obligatorio, con CHECK de objeto
y presencia de origin/destination/departureDate/flexDays/adults.
Índice search_history_created_id_idx(created_at DESC,id) para historial paginado.
No se agregan tablas de ofertas, payloads, FK a viajes ni modificaciones a V1..V6.
El historial conserva códigos consultados aunque luego cambie el catálogo.

## API

| Método | Ruta | Uso |
| --- | --- | --- |
| POST | /api/v1/flight-search/search | Buscar, comparar y obtener primera página |
| GET | /api/v1/flight-search/results/{searchId} | Paginar resultado efímero sin API externa |
| GET | /api/v1/flight-search/history | Consultar criterios históricos |

Se agregan los dos GET para recuperar páginas sin repetir consumo externo y
consultar el historial requerido. page 0..1000000, size 1..100 (default 20).
Swagger describe contratos, límites, 400/410/429/503 y ApiError.
JSON rechaza campos extra, claves duplicadas, datos sobrantes, fechas en arrays y
coerciones de tipos. Hasta nueve pasajeros; edades 0..17; un bebé por adulto.

Una variante puede ser SUCCESS, SKIPPED o UNAVAILABLE. 200 partial significa
datos incompletos, truncados o con ofertas descartadas; synthetic/testMode no
implican por sí solos un fallo de búsqueda. Cero coincidencias es éxito vacío.
Todos los proveedores fallidos producen 503 y mantienen el criterio intentado.
No hay historial adicional al reutilizar caché. Resultados expirados/expulsados
o tras reinicio producen 410; ofertas individuales vencidas se retiran manteniendo
score original. Conteos por variante reflejan el momento de búsqueda.

## Proveedor, cuota y configuración

Modo general disabled; local/Compose mock. Duffel requiere mode=duffel y token
de test por DUFFEL_ACCESS_TOKEN. El host HTTPS es fijo, no sigue redirects y usa
Duffel-Version v2. Se rechazan envelopes live y ofertas incompletas/invalidas.
Mock está rotulado synthetic=true, testMode=true, aerolínea ZZ; no es fallback.

Defaults configurables:

| Configuración | Default |
| --- | --- |
| SEARCH_RESULT_TTL_SECONDS / SEARCH_CACHE_ENTRIES | 180 / 32 |
| SEARCH_CONCURRENCY / SEARCH_DEADLINE_SECONDS | 2 / 45 |
| SEARCH_MAX_OFFERS_PER_VARIANT | 100 |
| SEARCH_CONNECT_TIMEOUT_MS / SEARCH_REQUEST_TIMEOUT_MS | 1500 / 8000 |
| SEARCH_SUPPLIER_TIMEOUT_MS | 5000 |
| SEARCH_MAX_ATTEMPTS / SEARCH_RETRY_DELAY_MS / SEARCH_MAX_RETRY_DELAY_MS | 2 / 100 / 1000 |
| SEARCH_REQUESTS_PER_WINDOW / SEARCH_WINDOW_SECONDS | 30 / 60 |
| SEARCH_CIRCUIT_THRESHOLD / SEARCH_CIRCUIT_SECONDS | 3 / 30 |
| SEARCH_MAX_RESPONSE_BYTES | 4194304 |

Cada intento HTTP consume cupo. Backoff exponencial acotado; 408/503 y fallos
de transporte admiten reintento dentro de plazo/circuito/cupo. 429 respeta
Retry-After/ratelimit-reset; esperas largas suspenden consultas, sin dormir
indefinidamente. 401/403 bloquean temporalmente nuevas llamadas. 400/422/500/502
no se reintentan ciegamente. Tres fallos abren circuito durante 30 segundos.
Se limita también recepción completa del body, no sólo espera de headers.
El parser examina hasta 500 ofertas por respuesta; excedentes advierten límite.
Retiene hasta 100 coincidencias por variante/proveedor; el ranking cubre ese
conjunto acotado, no promete el mejor precio de todo el mercado.

## Validación

Ejecutado `./mvnw.cmd --batch-mode --no-transfer-progress clean verify` el
28/09/2026, finalizado a las 21:24:38 -03:00: **BUILD SUCCESS**, 1 min 35 s.

- **525 tests**, cero fallos, errores u omitidos.
- **275 Surefire**, incluidos 51 de arquitectura y 48 nuevos de búsqueda:
  DuffelProviderTest 31, SearchDomainTest 9, SearchServiceTest 6,
  SearchConfigurationTest 2.
- **250 Failsafe** con PostgreSQL 17.6 real; 26 nuevos en FlightSearchIT.
- Cobertura global de líneas: **3647/3886 = 93,85 %**.
- Cobertura de flightsearch: **876/921 = 95,11 %**.
- Gate >=80 % aprobado sin exclusiones añadidas ni tests omitidos.
- Migraciones V1..V7 aplicadas y validadas por Flyway; Hibernate valida el esquema.
- JAR incluye controller/V7 y no contiene clases de prueba.
- `git diff --check` sin errores de whitespace.

El primer intento de integración falló por Docker indisponible (error 500).
Con autorización del usuario se recuperó el motor mediante reinicio, sin borrar
datos/volúmenes; la ejecución final anterior pasó completa.
Reportes: target/surefire-reports, target/failsafe-reports y target/site/jacoco.
Log local: .tools/card-5-verify.log. No hubo despliegue ni uso de base de producción.

Tests sin credenciales ni llamadas externas: HTTP local verifica contrato Duffel,
horarios, flags, precios, equipaje, respuestas inválidas, reintentos, cuota, circuito,
timeouts y límite de body. Dominio/servicio verifican filtros, ranking, flexibilidad,
resultados parciales, vencimiento, caché concurrente e historial de intentos.
FlightSearchIT usa PostgreSQL real para migración, JSONB, HTTP, historial y Swagger.

No se verificó una cuenta real de Duffel. Referencias oficiales consultadas:
[offer requests](https://duffel.com/docs/api/offer-requests),
[offers](https://duffel.com/docs/api/offers),
[test mode](https://duffel.com/docs/api/overview/test-mode) y
[response handling](https://duffel.com/docs/api/overview/response-handling).

## Riesgos y reevaluación

- Caché, cupo y circuito son por proceso, se reinician con él y no coordinan réplicas.
- El historial crece sin política automática de purga; definir retención antes de producción.
- Sigue siendo un MVP local sin autenticación. No exponer historial ni consumo API
  a usuarios externos sin identidad, autorización y cuota por usuario.
- Configurar siempre token de test. La respuesta live se rechaza después de la
  llamada; el backend no puede asegurar el modo de un token desconocido antes de usarlo.
- El timeout de proveedor puede devolver inventario parcial sin detalle por aerolínea.
- Búsquedas secuenciales y deadline pueden dejar alternativas sin consultar; ampliar
  paralelismo sólo midiendo latencia/cuota. Se conserva estado por fecha.
- Precios son temporales; no garantizan disponibilidad ni presupuesto completo.
  No se incluyen extras que el proveedor no entregue como parte del total.
- Antes de múltiples proveedores, revisar identidad de operación, equivalencias y
  conversión monetaria; no fusionar sólo por ruta o número.
- CARD 6 debe guardar un snapshot explícito elegido por el usuario, con máximo tres
  opciones por tramo; no hacer durable automáticamente el caché de búsquedas.

## Entrega

Rama: feature/flight-search.
Commit sugerido: feat(flightsearch): add airline search date comparison and ranking.
No se crea commit ni se publica la rama en esta entrega.
Próxima card: 6 — alternativas guardadas y snapshots de precio.
