# CARD 4 — Segmentos de vuelo y pasajeros

## Resultado

Implementado el registro manual de FlightSegment dentro de un tramo FLIGHT,
con consulta, edición, eliminación, pasajeros por segmento y precios conocidos.
Se conserva historial de número de vuelo, estado, terminales y puertas.
La cancelación modifica exclusivamente el segmento elegido.

Módulos afectados: flights incorpora el dominio y sus capas; trips expone
TripFlightAccess para coordinar pertenencia y revisión; catalog agrega airlineById.
No hay repositorios cruzados, entidades JPA expuestas ni dependencias Maven nuevas.

## Decisiones funcionales

- Un viaje puede tener pasajeros en vuelos diferentes. Las asignaciones son
  explícitas, únicas por vuelo/pasajero y admiten asiento, equipaje, ticket e importe
  propios. No se asume que todos los pasajeros participan en todos los vuelos.
- Aerolínea y aeropuertos usan UUID del catálogo. Los códigos pueden cambiar sin
  perder referencias. Nuevas referencias requieren registros activos; las históricas
  sin cambios pueden conservarse cuando se desactivan.
- flightDate es fecha local del aeropuerto de origen. Si existe salida programada,
  su fecha en esa zona debe coincidir. Los horarios absolutos usan Instant UTC y
  precisión de microsegundos. Horarios desconocidos permanecen null.
- Se conserva el horario programado conocido al registrar, separado del actual.
  No se inventa un horario original si al registrar era desconocido.
- PNR y ticket se almacenan como datos del segmento; las asignaciones permiten
  tickets individuales. PNR puede repetirse y no constituye identidad de reserva.
  No se crea entidad Booking ni se infiere protección a partir del PNR.
- connectionProtection declara la protección de la conexión de entrada:
  SAME_TICKET, SEPARATE_TICKETS o UNKNOWN.
- El usuario confirmó conservar importe de segmento, total del ticket y desglose
  por pasajero sin repartirlos ni sumarlos automáticamente. Son alcances distintos.
  amountUsd queda null cuando no hay conversión conocida; para USD equivale al
  importe original. No se consulta un proveedor de cambio.
- El estado operativo admite correcciones manuales registradas en historial.
  La política temporal para datos externos pertenece a tracking, no a estas
  correcciones explícitas. provider es MANUAL y lastSyncedAt permanece null.
- El registro de segmentos no acredita automáticamente cobertura completa de
  una ruta por pasajero. Trip/TripLeg mantienen la política declarada de CARD 2
  hasta definir dicha regla de completitud.

## Arquitectura, concurrencia y eliminación

Las mutaciones bloquean el viaje, comprueban su versión e incrementan la revisión
en la misma transacción que vuelo, asignaciones e historial. Los puertos de trips
participan con propagación MANDATORY: no confirman revisiones independientemente.

Para editar/eliminar, primero se consulta una proyección escalar de la pertenencia
del vuelo. La entidad se carga después de bloquear el padre, evitando usar una
entidad JPA almacenada en caché antes del bloqueo. Las lecturas usan REPEATABLE_READ.

POST acepta Idempotency-Key UUID opcional, usado como ID del vuelo. Contenido
normalizado equivalente devuelve el registro existente sin otra revisión; cambios
de contenido o viaje con la misma clave producen 409. La garantía dura mientras
exista el vuelo y no conserva respuestas previas a sus modificaciones. Sin clave,
cada alta es independiente. Se usa INSERT explícito, no merge, para que una colisión
concurrente de UUID entre viajes no sobrescriba un registro.

DELETE quita explícitamente enlaces de pasajeros e historial de ese vuelo antes
de borrarlo, todo en una transacción. No borra pasajeros, tramos ni otros segmentos.
Las FK impiden eliminar pasajeros/tramos referenciados o convertir a terrestre
un tramo que aún tiene vuelos. No se introducen cascadas de base de datos.

## Archivos principales

Rutas desde la raíz del proyecto:

- `src/main/java/com/flighttripmanager/flights/domain/model`: FlightSegment,
  FlightSchedule, FlightOperation, FlightBooking, FlightPassenger, FlightMoney,
  FlightHistory y enums internos.
- `src/main/java/com/flighttripmanager/flights/application/port/in/FlightManagement.java`.
- `src/main/java/com/flighttripmanager/flights/application/usecase/FlightService.java`.
- `src/main/java/com/flighttripmanager/flights/application/port/out`: FlightStore,
  FlightTrips y FlightCatalog.
- `src/main/java/com/flighttripmanager/flights/infrastructure/persistence`:
  entidades, embeddables, repositorios y mapper separados del dominio.
- `src/main/java/com/flighttripmanager/flights/infrastructure/trips/FlightTripsAdapter.java`.
- `src/main/java/com/flighttripmanager/flights/infrastructure/catalog/FlightCatalogAdapter.java`.
- `src/main/java/com/flighttripmanager/flights/api/controller/FlightsController.java`.
- `src/main/java/com/flighttripmanager/flights/api/mapper/FlightJsonReader.java`:
  JSON estricto, precisión decimal y presencia de campos.
- `src/main/java/com/flighttripmanager/trips/application/port/in/TripFlightAccess.java`
  y su implementación TripFlightService.
- CatalogLookup/CatalogStore y sus implementaciones agregan airlineById.
- README incluye contratos y ejemplos; ADR 007 documenta decisiones.

MapStruct conecta DTO, contratos, dominio y persistencia. Los value objects de
booking y asignaciones redactan su toString para no exponer ticket/PNR accidentalmente.

## Base de datos

Migración `src/main/resources/db/migration/V6__create_flight_segments.sql`:

- `flights.flight_segments`: identidad, pertenencia, catálogo, horarios, estado,
  información operativa, importes y datos de reserva.
- `flights.flight_passengers`: pertenencia al viaje/vuelo/pasajero, asiento,
  equipaje, ticket e importe individual.
- `flights.flight_history`: campo, valor anterior/nuevo, revisión, origen y fecha.
- FK compuesta al tramo del mismo viaje y tipo FLIGHT.
- FK compuestas de asignaciones al vuelo y al pasajero del mismo viaje.
- FK restrictivas a aerolínea y aeropuertos.
- UNIQUE de pasajero por vuelo y de cambio por vuelo/revisión/campo.
- CHECK de enums, pares de horarios, importes no negativos, consistencia monetaria
  USD, fechas, origen distinto de destino y valores de historial diferentes.
- Índices `flights_leg_date_id_idx`, `flight_passengers_trip_passenger_idx` y
  `flight_history_revision_idx`, más PK/UNIQUE.
- UNIQUE adicionales de identidad compuesta en trips.trip_legs y trips.passengers,
  necesarios para las FK de pertenencia.

Las migraciones anteriores permanecen intactas. BootstrapIT verifica seis migraciones.
El historial se borra al eliminar explícitamente el vuelo; no es un archivo permanente
de auditoría de recursos eliminados.

## API y Swagger

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/v1/trips/{tripId}/legs/{legId}/flights` | 201 y Location |
| GET | `/api/v1/trips/{tripId}/legs/{legId}/flights` | Página de vuelos |
| GET | `/api/v1/flights/{flightId}` | Vuelo con tripVersion |
| PATCH | `/api/v1/flights/{flightId}` | Vuelo actualizado |
| DELETE | `/api/v1/flights/{flightId}` | 204 |
| GET | `/api/v1/flights/{flightId}/history` | Página de cambios |

Las dos consultas paginadas se propusieron antes de implementarlas para recuperar
segmentos e historial sin listas ilimitadas. page inicia en 0; size por defecto 20
y máximo 100. Hasta 100 asignaciones por vuelo, cargadas de forma agrupada por página.

POST recibe version y data. PATCH recibe version y los campos modificados de data
en la raíz. Los grupos schedule/operation/booking presentes reemplazan el grupo
completo; los omitidos se conservan. operation requiere status. Null limpia schedule,
booking o passengers; [] también quita todas las asignaciones.

No se anuncia JSON Merge Patch recursivo: PATCH acepta application/json. Se rechazan
campos desconocidos, claves duplicadas, datos sobrantes, coerción numérica a enums
y fechas en arrays. DELETE recibe version en query. Los errores usan ApiError:
400 por entrada/referencia inválida, 404 por recurso inexistente, 409 por versión
o integridad. No incluyen PNR, ticket ni valores rechazados.

## Tests y resultados

Ejecutado `./mvnw.cmd --batch-mode --no-transfer-progress clean verify` el
28/09/2026, finalizado a las 01:33:55 -03:00: **BUILD SUCCESS**, 1 min 19 s.

- **451 tests**, cero fallos, errores u omitidos.
- **227 Surefire**, incluidos los **51 de arquitectura** y **27 nuevos de dominio**
  en FlightDomainTest.
- **224 Failsafe** con PostgreSQL real en Testcontainers: BootstrapIT 12,
  CatalogImportIT 40, CatalogIT 66, PassengersIT 38, TripsIT 30 y **FlightsIT 38**.
- Cobertura global de líneas: **2771/2965 = 93,46 %**.
- Cobertura del módulo flights: **1079/1161 = 92,94 %** de líneas.
- Instrucciones globales: 95,74 %; ramas: 74,58 %.
- Gate global de líneas >=80 % aprobado sin nuevas exclusiones ni omisiones.
- JAR comprobado: incluye FlightsController y V6; excluye FlightsIT,
  FlightDomainTest y configuración de reloj de pruebas.
- `git diff --check` sin errores de whitespace.

Las pruebas nuevas cubren normalización, precios exactos y conversión desconocida,
estados internos, historial selectivo, límites de asignaciones, fecha local de
salida, catálogo activo/inactivo, CRUD HTTP, reemplazo de grupos, null y omisión,
paginación, OpenAPI, FK/CHECK y rollback de conflictos.

La concurrencia se verifica con HTTP y PostgreSQL: reintentos con la misma clave
en un viaje producen un único vuelo/revisión; claves coincidentes entre viajes
producen una alta y un conflicto sin sobrescritura; dos ediciones con la misma
versión tienen exactamente un ganador. También se comprueba que cancelar un
segmento no cancela otro con el mismo PNR.

La revisión detectó y corrigió dos casos: altas con UUID asignado no deben usar
merge al competir entre viajes, y Jackson requiere configuración explícita para
rechazar números/booleanos en campos de texto. La prueba de tipos JSON provocó un
fallo antes de esa corrección; la ejecución final indicada arriba incluye el arreglo.

Reportes locales: `target/surefire-reports`, `target/failsafe-reports`,
`target/site/jacoco/index.html`. Log: `.tools/card-4-verify.log`.
Las pruebas usan Docker/PostgreSQL y no consultan APIs externas. No se desplegó
la aplicación ni se modificaron datos de una base de producción.

## Riesgos y reevaluación

- Sigue vigente el MVP local sin autenticación. Los detalles de ticket y PNR están
  disponibles para edición local; no se imprimen cuerpos ni datos sensibles en logs.
- No hay compra, confirmación externa, sincronización, lookup ni polling en esta card.
- La cobertura de reservas por pasajero y ruta necesita una regla explícita antes
  de automatizar estados de viaje/tramo y cierre de alternativas.
- Los importes de distinto alcance no deben sumarse; presupuesto deberá resolver
  totales compartidos sin usar PNR como identidad ni inventar desgloses.
- La planificación de fechas de Trip no se propaga a vuelos ni valida su completitud.
  La fecha local de salida puede diferir de la fecha UTC del tramo.
- No se resuelve operación compartida entre códigos comerciales distintos ni se
  fusionan registros sólo porque tengan número, PNR o ruta coincidentes.
- Idempotencia existe mientras exista el registro; no se guardan tombstones.
- El historial documenta cambios operativos, no cada variación de estimación ni
  una auditoría inmutable después de DELETE.
- El padre aún carga sus tramos acotados para obtener/bloquear su revisión.
  Las asignaciones se consultan en lote; no hay N+1 por vuelo.
- Cambiar un vuelo reemplaza sus asignaciones, acotadas a 100. Medir antes de
  optimizar diferencias por pasajero o ampliar límites.
- Las correcciones manuales pueden retroceder estado. Las reglas de datos externos
  y su historial temporal deben incorporarse explícitamente con tracking.

Se reutiliza la infraestructura existente. No hay nuevas colas, cachés, servicios,
dependencias Maven ni consumo de cuota externa.

## Entrega

- Rama: `feature/flight-segments`.
- Commit sugerido: `feat(flights): add manual segments passenger assignments and history`.
- No se crea commit ni se publica la rama como parte de esta card.
- Próxima card: **5 — búsqueda de vuelos**, mediante un puerto independiente y
  proveedores permitidos. La regla pendiente de cobertura debe cerrarse antes de
  automatizar confirmación de reservas y desactivación de alternativas.
