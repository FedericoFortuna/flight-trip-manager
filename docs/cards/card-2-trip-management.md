# CARD 2 — Gestión de viajes y tramos

## Resultado

Implementados creación, consulta, edición y eliminación de viajes y tramos,
listas paginadas, presupuesto USD opcional, estados derivados y override del viaje.
El itinerario admite orden automático o manual completo, con control de versión
para evitar sobrescribir ediciones concurrentes. No se agregan dependencias Maven.

## Archivos y arquitectura

- `src/main/java/com/flighttripmanager/trips/domain/model`: agregado Trip,
  TripLeg, referencias de lugar y reglas puras de fechas, estados y orden.
- `trips/application/port/in/TripManagement.java`: contrato público del módulo.
- `trips/application/usecase/TripService.java`: transacciones, versiones y casos de uso.
- `trips/application/port/out`: TripStore y CatalogPlaces.
- `trips/infrastructure/persistence`: entidades JPA separadas, repositorios,
  adaptador y mapper MapStruct.
- `trips/infrastructure/catalog/CatalogPlacesAdapter.java`: consulta únicamente
  el contrato público CatalogLookup; no accede a repositorios de otro módulo.
- `trips/api`: controlador, DTO, mapper, lector estricto de PATCH y errores seguros.
- CatalogLookup y su implementación incorporan airportById, sin nueva ruta HTTP.
- README contiene ejemplos y contrato; ADR 005 documenta las decisiones.

Las rutas abreviadas anteriores pertenecen a `src/main/java/com/flighttripmanager`.

## Reglas funcionales

- Viaje con nombre y rango de fechas obligatorio; presupuesto nullable,
  no negativo y con hasta dos decimales, sin redondeo silencioso.
- Origen y destino referencian UUID de aeropuerto o location del catálogo.
  Nuevas referencias a aeropuertos requieren que estén activos. Las referencias
  históricas sin cambios se preservan aunque el aeropuerto se desactive.
- Un tramo PLANNED puede carecer de fecha. Fecha sin hora se conserva como fecha;
  no se inventa medianoche. Los instantes y su día asociado se interpretan en UTC.
  Las fechas conocidas deben estar dentro del intervalo del viaje.
- Orden automático por fecha, hora conocida antes de desconocida, creación y UUID;
  tramos sin fecha al final. Orden manual exige todos los IDs una vez; lista vacía
  restaura automático. Altas se agregan al final y bajas compactan posiciones.
- Límite de 500 tramos por viaje; listas con página de hasta 100 elementos.
- El estado se calcula al consultar usando Clock. El override del viaje tiene
  prioridad sin modificar los estados de sus tramos. BOOKED/UPCOMING declarados
  derivan estados temporales según salida y llegada. Sin llegada no se inventa
  una finalización; una llegada con sólo fecha se completa al terminar ese día.
- La cobertura de reserva se declara en los tramos en esta card. La integración
  con reservas y pasajeros reales corresponde a las siguientes cards.
- No se elimina un viaje que tenga tramos: deben quitarse explícitamente primero.

## Concurrencia y PATCH

Cada mutación de un viaje existente requiere su versión esperada y aumenta la
revisión del agregado, incluidas las mutaciones de tramos. Se bloquea la fila
padre, se compara la versión y se escribe en la misma transacción. Una versión
obsoleta responde 409. No se usa @Version JPA; la revisión se controla explícitamente
bajo el bloqueo del padre. Las lecturas usan REPEATABLE_READ y las escrituras
READ_COMMITTED.

PATCH distingue campo omitido de null explícito, rechaza propiedades desconocidas
y claves duplicadas, y mantiene precisión BigDecimal. Requiere version y al menos
un campo modificable. Los campos obligatorios no admiten null.

## Migración

`src/main/resources/db/migration/V4__create_trips_and_legs.sql` agrega:

- `trips.trips`: identidad, nombre, fechas, presupuesto, override, versión y timestamps.
- `trips.trip_legs`: identidad, padre, referencias de lugares, transporte, fechas,
  instantes, estado declarado, posición manual y timestamps.
- FK RESTRICT al padre y a aeropuertos/locations; no hay cascadas.
- CHECK de rangos, importes, enums, endpoints distintos, exactamente una referencia
  por endpoint y coherencia entre fechas e instantes UTC.
- UNIQUE `(trip_id, manual_order)` diferible para permutaciones atómicas.
- Índices `trips_created_id_idx` y `legs_trip_departure_idx`, además de PK/UNIQUE.

La coherencia completa del orden, el límite de tramos y las fechas dentro del viaje
se validan en el agregado bajo bloqueo. No se modifican migraciones anteriores.

## Endpoints y Swagger

Disponibles en `/swagger-ui/index.html` cuando la aplicación está iniciada.

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/v1/trips` | Crear viaje, 201 |
| GET | `/api/v1/trips` | Listar, filtrar por q y paginar |
| GET | `/api/v1/trips/{tripId}` | Consultar viaje |
| PATCH | `/api/v1/trips/{tripId}` | Editar con version en cuerpo |
| DELETE | `/api/v1/trips/{tripId}` | Eliminar vacío, version en query, 204 |
| POST | `/api/v1/trips/{tripId}/legs` | Crear tramo con version, 201 |
| GET | `/api/v1/trips/{tripId}/legs` | Listar itinerario paginado |
| PATCH | `/api/v1/trips/{tripId}/legs/{legId}` | Editar con version en cuerpo |
| DELETE | `/api/v1/trips/{tripId}/legs/{legId}` | Eliminar, version en query, 204 |
| PATCH | `/api/v1/trips/{tripId}/legs/reorder` | version y orderedLegIds; [] restaura automático |

Errores seguros con contrato ApiError: 400 por solicitud o referencia inválida,
404 por viaje/tramo inexistente y 409 por versión o conflicto de integridad.

## Verificación

Ejecutado `./mvnw.cmd --batch-mode --no-transfer-progress clean verify` el
24/09/2026; terminó a las 19:26:43 -03:00 con **BUILD SUCCESS**.

- **335 tests**, sin fallos, errores ni omitidos.
- Surefire: **187**, incluidos 51 de arquitectura, 23 de dominio de viajes,
  14 de lectura de PATCH y 4 de servicio de viajes.
- Failsafe: **148** con PostgreSQL real en Testcontainers: 12 BootstrapIT,
  40 CatalogImportIT, 66 CatalogIT y **30 TripsIT**.
- Cobertura global de líneas: **1481/1589 = 93,20 %**.
- Cobertura de líneas del módulo trips: **676/755 = 89,54 %**.
- Cobertura global de instrucciones: 96,05 %; de ramas: 76,55 %.
- Gate global de líneas >=80 % aprobado sin nuevas exclusiones ni omisiones.
- JAR verificado: contiene TripsController y V4; excluye configuración de reloj,
  fixtures y controladores auxiliares de pruebas.

Las pruebas cubren CRUD HTTP, exactitud monetaria, null/omisión, paginación,
referencias inválidas/inactivas, aislamiento de tramos entre viajes, fechas UTC,
reordenamiento y rollback, estados con reloj controlado, FK/CHECK y OpenAPI.
La prueba concurrente verifica dos ediciones sobre la misma versión: una se guarda
y la otra recibe 409. Las pruebas de catálogo y arquitectura siguen pasando.

Reportes locales: `target/surefire-reports`, `target/failsafe-reports` y
`target/site/jacoco/index.html`. Log: `.tools/card-2-verify.log`.

## Riesgos y reevaluación

- Se usa UTC; no se modela todavía una zona horaria propia del viaje.
- Las reservas declaradas no acreditan compra ni cobertura real por pasajero.
- El orden manual es completo; no admite fijar sólo algunas posiciones.
- Las mutaciones del mismo viaje se serializan. Medir antes de cambiar la
  estrategia de bloqueo o introducir ediciones parciales concurrentes.
- Se carga el agregado acotado a 500 tramos. Las listas usan una consulta agrupada
  de tramos por página; medir consumo antes de ampliar límites o agregar proyecciones.
- Escrituras SQL ajenas a la aplicación podrían eludir invariantes entre filas.
- Sigue vigente el alcance local del MVP sin autenticación. No hay integración
  con proveedores externos ni despliegue como parte de esta entrega.

## Entrega

- Rama: `feature/trip-management`.
- Commit sugerido: `feat(trips): add trip and leg management with versioned ordering`.
- No se crea commit ni se publica la rama como parte de esta card.
- Próxima card: **3 — pasajeros**.
