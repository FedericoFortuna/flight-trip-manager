# CARD 3 — Pasajeros por viaje

## Resultado y decisiones

Implementados alta, listado paginado, edición parcial y eliminación de pasajeros
asociados a un Trip. Passenger pertenece al módulo trips; no se incorpora un módulo
adicional ni un catálogo global de personas. El dominio permanece puro, separado
de JPA y de DTO HTTP, con MapStruct en las fronteras.

Cada pasajero tiene UUID, tripId, firstName, lastName, notes opcionales y timestamps
UTC. Los nombres se recortan, requieren contenido y admiten hasta 100 caracteres;
no se fuerza un alfabeto ni unicidad por nombre. Las notas admiten hasta 2000
caracteres, saltos de línea y tabulaciones. Se rechazan otros caracteres de control.
No hay campos de DNI, pasaporte ni documentación.

Las mutaciones requieren la versión actual del viaje y la incrementan una vez.
Se bloquea el padre y se escribe pasajero y revisión en una transacción. La versión
se comparte con las ediciones del viaje y sus tramos. saveHeader evita reescribir
tramos al cambiar un pasajero. Los listados usan REPEATABLE_READ para que la revisión
y la página correspondan a la misma instantánea.

PATCH conserva campos omitidos, permite notes:null y rechaza null en nombres.
No se puede cambiar identidad o viaje. El lector estricto rechaza claves desconocidas,
duplicadas, tipos incorrectos y contenido posterior al objeto JSON. Esta última
validación también refuerza el lector compartido de PATCH de viajes y tramos.

Un viaje con pasajeros no puede eliminarse. Deben quitarse explícitamente; las FK
refuerzan esta política. No hay cascadas. La asociación con FlightSegment queda
para CARD 4, sin asumir que todos los pasajeros viajan en todos los vuelos.

## Archivos principales

Rutas desde la raíz del proyecto:

- `src/main/java/com/flighttripmanager/trips/domain/model/Passenger.java`.
- `src/main/java/com/flighttripmanager/trips/application/port/in/PassengerManagement.java`.
- `src/main/java/com/flighttripmanager/trips/application/usecase/PassengerService.java`.
- `src/main/java/com/flighttripmanager/trips/application/port/out/PassengerStore.java`.
- `src/main/java/com/flighttripmanager/trips/infrastructure/persistence/adapter/JpaPassengerAdapter.java`.
- `src/main/java/com/flighttripmanager/trips/infrastructure/persistence/entity/PassengerJpaEntity.java`.
- `src/main/java/com/flighttripmanager/trips/infrastructure/persistence/repository/PassengerJpaRepository.java`.
- `src/main/java/com/flighttripmanager/trips/api/controller/PassengersController.java`.
- Contratos, DTO y mappers de pasajeros en las mismas capas del módulo.
- TripService verifica pasajeros antes de borrar; TripStore/JpaTripAdapter permiten
  guardar sólo la cabecera. TripsExceptionHandler reconoce PASSENGER_NOT_FOUND.
- README contiene ejemplos; ADR 006 registra las decisiones.

## Base de datos

Migración `src/main/resources/db/migration/V5__create_passengers.sql`:

- Tabla `trips.passengers` con UUID como PK.
- FK `trip_id → trips.trips.id` con ON DELETE RESTRICT.
- Nombre y apellido NOT NULL, límites varchar(100) y CHECK de contenido y controles.
- Notas nullable varchar(2000), con CHECK de caracteres de control.
- Timestamps NOT NULL y CHECK updated_at >= created_at.
- Índice `passengers_trip_created_id_idx (trip_id, created_at, id)`, alineado con
  la consulta paginada y el orden determinista.

No se modifican migraciones anteriores. BootstrapIT espera cinco migraciones.
No se agregan dependencias Maven, infraestructura ni llamadas externas.

## API y Swagger

Documentados en Swagger con request, response y errores relevantes:

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/v1/trips/{tripId}/passengers` | 201, pasajero y tripVersion |
| GET | `/api/v1/trips/{tripId}/passengers` | 200, página |
| PATCH | `/api/v1/trips/{tripId}/passengers/{passengerId}` | 200, pasajero actualizado y tripVersion |
| DELETE | `/api/v1/trips/{tripId}/passengers/{passengerId}` | 204 |

POST/PATCH reciben version en el cuerpo; DELETE en query. GET admite page desde 0
y size de 1 a 100, por defecto 20; orden por createdAt e id ascendentes.
PATCH admite application/json y application/merge-patch+json.

400 para entrada inválida; 404 para viaje inexistente o pasajero fuera de ese viaje;
409 para versión obsoleta o conflicto de integridad. ApiError conserva el formato
uniforme sin incluir valores personales rechazados.

## Tests y resultados

Ejecutado `./mvnw.cmd --batch-mode --no-transfer-progress clean verify` el
26/09/2026, finalizado a las 18:13:00 -03:00: **BUILD SUCCESS**, 1 min 46 s.

- **386 tests**, cero fallos, errores u omitidos.
- **200 Surefire**, incluidos 51 tests de arquitectura.
- Nuevos unitarios: PassengerTest **9**, PassengerServiceTest **4**.
- **186 Failsafe** con PostgreSQL real en Testcontainers.
- Nuevos de integración: PassengersIT **38**.
- Cobertura global de líneas: **1661/1776 = 93,52 %**.
- Cobertura del módulo trips: **856/942 = 90,87 %** de líneas.
- Instrucciones globales: 96,03 %; ramas: 77,35 %.
- Gate global de líneas >=80 % aprobado, sin exclusiones ni tests omitidos nuevos.

Se verifican CRUD, nombres internacionales y homónimos, límites, notas con null,
omisión de campos, aislamiento entre viajes, paginación, entrada JSON estricta,
versiones obsoletas, rollback de validaciones, reloj que retrocede, restricciones
SQL y contrato OpenAPI. Dos altas concurrentes con una misma versión producen
exactamente un 201 y un 409. Crear un pasajero mientras se elimina su viaje conserva
la integridad: sólo una operación puede ganar.

Los tests anteriores de viajes, catálogo y arquitectura siguen pasando. El JAR
contiene PassengersController y V5; no contiene PassengerTest, PassengersIT ni la
configuración de reloj de pruebas. Docker estaba apagado al comenzar y se inició
para ejecutar las pruebas; no se desplegó la aplicación.

Reportes locales: `target/surefire-reports`, `target/failsafe-reports`,
`target/site/jacoco/index.html`. Log: `.tools/card-3-verify.log`.

## Riesgos y reevaluación

- El MVP sigue siendo local y sin autenticación. El aislamiento por tripId garantiza
  pertenencia del recurso, pero no constituye autorización por usuario.
- Notes es texto libre: no se detectan documentos personales escritos allí.
- La asociación con vuelos y las reglas para eliminar pasajeros ya vinculados
  requieren implementación explícita en CARD 4.
- La revisión compartida serializa cambios del mismo viaje; una edición de pasajero
  puede invalidar una edición pendiente de tramo. Es una decisión de consistencia.
- Consultar/bloquear el padre carga sus tramos, acotados a 500. Si fuera costoso,
  agregar una proyección de revisión preservando el protocolo de bloqueo. El listado
  de pasajeros sí se pagina en base de datos y no carga la colección completa.
- Un listado vacío no lleva tripVersion: la revisión está disponible en GET del
  viaje. DELETE devuelve 204 y el cliente debe volver a consultar la revisión.

Se reutilizan transacciones, revisión, errores, paginación y mapeo existentes.
No hay dependencias cruzadas entre repositorios de módulos, consultas externas,
colas, cachés ni modelos de vuelos anticipados.

## Entrega

- Rama: `feature/passenger-management`.
- Commit sugerido: `feat(trips): add trip passenger management`.
- No se crea commit ni se publica la rama como parte de esta card.
- Próxima card: **4 — FlightSegment y asociación de pasajeros por vuelo**;
  ya existen viajes, tramos, pasajeros y catálogo para respaldarla.
