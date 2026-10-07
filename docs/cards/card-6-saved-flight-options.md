# CARD 6 — Alternativas guardadas e historial de precios

## Resultado

SavedFlightOption conserva una oferta seleccionada de CARD 5 y sus criterios.
FlightPriceSnapshot conserva cada observación de precio. Máximo tres opciones
retenidas por TripLeg, incluidas inactivas, sin fusionar proveedores diferentes.
La oferta inicial sigue disponible al vencer el caché y no se reemplaza con
observaciones posteriores. Cada precio conserva su identidad de búsqueda/oferta.

El usuario confirmó una reserva explícita a nivel de tramo. Se utiliza PATCH
del tramo con status BOOKED, sin inferir cobertura por PNR o por la existencia
de un segmento aislado. Registrar vuelos manuales sigue siendo independiente.

## Reglas y límites

- Se admiten alternativas en FLIGHT con estado declarado PLANNED, SEARCHING
  o COMPARING. Los demás estados desactivan opciones y bloquean altas/observaciones.
- El tramo debe tener ambos extremos AIRPORT. La ruta de ida debe coincidir
  con sus aeropuertos activos. No se infiere aeropuerto de una ubicación/ciudad.
- Fechas flexibles no modifican automáticamente la fecha planificada del tramo.
- Round-trip se conserva íntegro en el tramo de ida; no se crean enlaces al regreso,
  no se reparte el total y no debe sumarse dos veces en presupuesto.
- La fuente para guardar/anexar debe estar en caché y no haber vencido.
  Tras guardado, el criterio y oferta quedan desacoplados del historial temporal.
- El cliente envía searchId/provider/providerOfferId/version; precios y segmentos
  se toman del servidor. No hay edición manual de precios en estos endpoints.
- Replay de la misma selección en el mismo tramo no crea opción/revisión nueva.
  Replay de una observación existente tampoco duplica precio ni avanza revisión.
  No es un mecanismo de reactivación; después de eliminar se pierde esa garantía.
- Otra observación requiere mismo proveedor, propietario de la oferta, segmentos
  completos (incluidos horarios), pasajeros, cabina, equipaje y modo test/synthetic.
  Puede cambiar importe, moneda e identificador de oferta. Se conserva el nuevo
  monto aunque coincida con el anterior; es una observación, no sólo un cambio.
- Historial ordenado por observedAt descendente y UUID para desempatar.
  No implica conversión ni comparación económica entre monedas diferentes.
- Los importes basePrice, baggagePrice y seatPrice permanecen null porque CARD 5
  no recibe un desglose fiable. totalPrice/originalAmount reflejan el total parcial
  informado; amountUsd sólo conocido para USD. No se agregan costos desconocidos.
- synthetic/testMode y missingCosts se preservan en cada precio y oferta.
- Reabrir el tramo no reactiva opciones. Cambiar origen/destino desactiva con
  ROUTE_CHANGED. Confirmación/cierre usa LEG_CLOSED; el historial se conserva.
- Eliminar opción elimina explícitamente sus precios y libera posición.
  No borra vuelos, pasajeros ni modifica confirmación de reserva.

## Arquitectura y concurrencia

Dominio puro en flightsearch; DTOs, contratos y modelos JPA separados. MapStruct
conecta las capas. El mapper de opciones recibe SearchMapper como contexto para
reutilizar conversiones sin inyección Spring en el mapper generado de aplicación.
JSONB contiene contratos normalizados, nunca respuestas crudas de Duffel.

Todas las mutaciones bloquean primero el viaje, validan su versión y persisten
cambios/revisión juntos. La referencia previa de pertenencia es una proyección
escalar; la opción se carga después del bloqueo para evitar una entidad obsoleta.
El límite de tres se refuerza con slots únicos 1..3 en PostgreSQL.
Lecturas compuestas usan REPEATABLE_READ.

TripService comunica cierre/ruta modificada mediante LegAlternativesLifecycle;
su adaptador publica LegAlternativesClosed (contrato público). El listener de
flightsearch es síncrono y MANDATORY: cierre y versión se confirman o revierten
juntos. No se introducen ciclos de módulos, repositorios cruzados ni mensajería.
El guardado y confirmación compiten por el mismo bloqueo, sin ventana donde una
reserva confirmada pueda incorporar una opción activa mediante la API.

## Archivos principales

Desde src/main/java/com/flighttripmanager:

- flightsearch/domain/model: SavedFlightOption, FlightPriceSnapshot.
- flightsearch/application/port/in/SavedOptions.java y usecase/SavedOptionService.java.
- flightsearch/application/port/out: SavedOptionStore, SavedOptionTrips;
  SearchHistoryStore incorpora lectura de criterio por id.
- flightsearch/application/contract: SaveOption, SavedOptionView, PriceSnapshotData,
  PriceObservation, SavedOptionException.
- flightsearch/application/mapper: SavedOptionMapper; SearchMapper incorpora lectura de OfferData.
- flightsearch/infrastructure/persistence: entidades SavedOptionJpaEntity y
  FlightPriceJpaEntity, repositorios, mapper y JpaSavedOptions.
- flightsearch/infrastructure/trips: SavedOptionTripsAdapter y SavedOptionLifecycleListener.
- flightsearch/api: SavedOptionsController, DTOs, mapper, JSON reader y exception handler.
- trips: TripOptionAccess, OptionTripContext, LegAlternativesLifecycle,
  LegAlternativesClosed, LegAlternativesPublisher; ajustes en TripFlightService,
  TripService y TripLeg para cierre y consulta de contexto.
- Pruebas: SavedOptionsIT, SavedOptionDomainTest, ajuste de TripServiceTest y BootstrapIT.
- README y ADR 009 documentan operación, decisiones y límites.

## Base de datos

Migración V8__create_saved_flight_options.sql, sin modificar V1..V7:

- flightsearch.saved_flight_options: PK UUID, trip/leg, tipo FLIGHT, slot,
  criterios/oferta JSONB, activo/motivo y fechas de creación/modificación.
- FK compuesta a trips.trip_legs(trip_id,id,transport_type), restrictiva.
- CHECK slot 1..3 y UNIQUE(trip_id,leg_id,slot): máximo tres retenidas.
- CHECK de JSONB objeto, estado/motivo y updatedAt >= createdAt.
- flightsearch.flight_price_snapshots: PK UUID, FK restrictiva a opción,
  fuente de observación, importes numeric(23,4), moneda, proveedor, observedAt,
  flags test/synthetic y faltantes JSONB array.
- UNIQUE(opción,búsqueda,oferta) impide repetir una observación.
- CHECK de importes no negativos/finitos, total=original y coherencia USD.
- Índice flight_price_history_idx(opción,observed_at DESC,id) para paginación.
- Sin FK al historial de búsquedas: retención futura de criterios no puede borrar
  ni invalidar opciones explícitamente guardadas.
- Se conserva fecha observada del proveedor; PostgreSQL almacena microsegundos.

## API

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | /api/v1/trips/{tripId}/legs/{legId}/saved-options | 201, opción y Location |
| GET | /api/v1/trips/{tripId}/legs/{legId}/saved-options | Página por slot, incluidas inactivas |
| GET | /api/v1/saved-flight-options/{id} | Opción con tripVersion |
| DELETE | /api/v1/saved-flight-options/{id}?version=N | 204 |
| POST | /api/v1/saved-flight-options/{id}/price-observations | Precio, tripVersion y replayed |
| GET | /api/v1/saved-flight-options/{id}/price-history | Página de observaciones |

Se incorporan consultas y eliminación para recuperar copias/historial y gestionar
el cupo. La observación explícita permite poblar histórico sin polling externo.
No se crea endpoint adicional de compra: confirmación usa PATCH del tramo existente.
Listados page 0..1000000, size 1..100, default 20.

ApiError: 400 entrada/selección incompatible; 404 recurso ausente; 409 versión,
cupo o estado cerrado/inactivo; 410 oferta/búsqueda expirada o no disponible.
Body JSON estricto: rechaza campos extra, duplicados, tipos coercionados y datos
sobrantes. No expone entidades JPA, excepciones internas ni precios suministrados
por el cliente como si fueran observaciones del proveedor.

## Verificación

Ejecutado `./mvnw.cmd --batch-mode --no-transfer-progress clean verify` el
07/10/2026, finalizado a las 20:48:09 -03:00: **BUILD SUCCESS**, 2 min 15 s.

- **556 tests**, cero fallos, errores u omitidos.
- **279 Surefire**, incluidos 51 de arquitectura y 4 nuevos de SavedOptionDomainTest.
- **277 Failsafe** con PostgreSQL 17.6 real: 27 nuevos de SavedOptionsIT,
  más BootstrapIT 12, CatalogImportIT 40, CatalogIT 66, FlightSearchIT 26,
  FlightsIT 38, PassengersIT 38 y TripsIT 30.
- Cobertura global de líneas: **4258/4516 = 94,29 %**.
- Cobertura de flightsearch: **1465/1533 = 95,56 %**.
- Gate global >=80 % aprobado sin nuevas exclusiones ni tests omitidos.
- Flyway aplica/valida V1..V8 y Hibernate valida el esquema.
- JAR incluye SavedOptionsController y V8; excluye SavedOptionsIT y SavedOptionDomainTest.
- `git diff --check` sin errores de whitespace.

Las pruebas verifican copia durable tras expirar/eliminar la fuente, conservación
de ida/vuelta sin reparto, precio parcial y moneda, historial paginado, replay,
límite de tres, slots/FK/CHECK, eliminación, rutas incompatibles, estados abiertos
y cerrados, desactivación por ruta, no reactivación implícita, JSON estricto y Swagger.
Las carreras reales HTTP/PostgreSQL cubren guardado duplicado, altas distintas
con versión coincidente y confirmación de reserva contra guardado.

Se corrigieron durante validación: inyección del mapper generado incompatible
con las reglas de arquitectura, precisión consistente de importes/fechas al
releer snapshots y declaración explícita de respuestas exitosas en Swagger.
La ejecución final anterior incluye esas correcciones.

Reportes locales: target/surefire-reports, target/failsafe-reports y target/site/jacoco.
Log: .tools/card-6-verify.log. Sin despliegue ni cambios en bases de producción.
No se utilizaron APIs externas para las pruebas.

## Riesgos y reevaluación

- MVP local sin autenticación: falta autorización por propietario antes de uso compartido.
- BOOKED es declaración del usuario, no compra externa ni validación de ticket.
  La cobertura automática por pasajero y ruta sigue pendiente.
- Agrupar ida/vuelta en una opción no resuelve presupuesto compartido; CARD 7
  debe evitar doble contabilización y respetar importes de alcance diferente.
- No se actualizan precios en segundo plano. Búsquedas nuevas son explícitas,
  sujetas a límites de CARD 5; guardar/anexar no consume APIs.
- Los precios test/mock no son comprables. No se realizó compra ni llamada real
  a Duffel durante esta card.
- Cambios de horario/equipaje/cabina no se mezclan como el mismo producto:
  guardar otra opción o eliminar una anterior. Revisar identidad antes de automatizar.
- Historial crece por observaciones explícitas, con consultas paginadas; definir
  política de retención antes de gran volumen. Eliminar opción borra historial.
- Criterios/ofertas JSONB son contratos durables: cambios incompatibles de esos
  contratos requieren migración de datos o versionado del formato.
- Los pasajeros de la búsqueda se conservan como cantidades/edades, sin inferir
  asignaciones a pasajeros del viaje; presupuesto no debe asumir esa equivalencia.
- El cierre síncrono supone el multicaster estándar de Spring; no volverlo asíncrono
  sin rediseñar la garantía transaccional y sus pruebas.
- Las invariantes de cierre requieren mutar tramos por la API, no SQL externo.
  Restricciones de base protegen cupo/pertenencia, sin triggers entre módulos.
- No hay reactivación automática ni eliminación en cascada de opciones al borrar viajes.

La reevaluación mantiene un monolito modular con puertos públicos y el mecanismo
transaccional existente. No se agregan dependencias Maven, servicios, colas,
polling ni consumo adicional de cuota. El listener síncrono permite reutilizar
PATCH del tramo sin un segundo endpoint de confirmación ni dependencia circular.
El acceso a opciones está acotado a tres; el historial usa consultas paginadas
e índice compuesto. Se conserva el costo existente de cargar/bloquear el agregado
de viaje, acotado por sus límites actuales; medir antes de descomponer ese bloqueo.

## Entrega

Rama: feature/saved-flight-options.
Commit sugerido: feat(flightsearch): add saved options and observed price history.
Sin commit ni push como parte de la card.
Próxima card: 7 — presupuesto, importes reales/estimados y prevención de doble conteo.
