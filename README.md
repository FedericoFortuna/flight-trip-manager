# Flight Trip Manager

Backend de planificación, gestión y seguimiento de viajes aéreos. Monolito modular
hexagonal, sin frontend ni autenticación en el MVP local.

## Estado

CARD 0 implementa la infraestructura de arranque y CARD 0.1 agrega errores HTTP
uniformes y observabilidad. CARD 0.2 exige límites arquitectónicos y cobertura mínima.
CARD 1 incorpora el catálogo local de aeropuertos, aerolíneas y ubicaciones:
seis consultas `/api/v1`, entidades JPA separadas del dominio y migración V2.
CARD 1.1 agrega importación explícita por lotes JSON, con identidad de origen,
actualizaciones atómicas y protección frente a versiones antiguas.
CARD 2 agrega viajes y tramos, referencias al catálogo, estados calculados,
edición con control de versión y orden manual reversible.
CARD 3 incorpora pasajeros por viaje, listado paginado y edición parcial de nombres
y notas, integrada con la versión del viaje.
CARD 4 agrega segmentos de vuelo manuales, pasajeros por segmento, importes conocidos,
historial de número/estado/terminal/puerta e idempotencia opcional de altas.
CARD 5 agrega búsqueda one-way/round-trip, comparación de hasta siete fechas,
filtros, ranking determinístico, historial de criterios y adaptador Duffel de prueba.
Local/Compose usa ofertas sintéticas explícitas; Duffel requiere activación y token
de test. No hay carga automática de datos ni llamadas externas al arrancar.
Los contratos y paquetes internos se incorporan por cards, evitando clases vacías.

Validación de CARD 0 (2026-09-23): `clean verify` correcto (1 test unitario y 5 de
integración con PostgreSQL), imagen Docker construida y arranque de Compose
comprobado. Cobertura del código de arranque: 100% de líneas; no hay aún lógica
funcional. Detalle y limitaciones en la documentación de la card.

## Requisitos y versiones

- JDK 17 para desarrollo local.
- Maven 3.9.11 vía Maven Wrapper (no requiere Maven instalado).
- Docker con motor Linux y Docker Compose v2 para base de datos e integración.
- Spring Boot 3.5.16, Springdoc 2.8.17, PostgreSQL 17.6.
- JUnit 5, Mockito y Testcontainers con versiones gestionadas por Spring Boot.
- MapStruct 1.6.3 y Lombok preparados como annotation processors.

La primera compilación necesita Internet para descargar Maven y dependencias.
Docker también descarga imágenes si no están disponibles localmente.

## Arranque con Docker Compose

1. Copiar `.env.example` a `.env`.
2. Definir `DB_PASSWORD` con una contraseña local propia, no vacía.
3. Ejecutar:

```sh
docker compose up --build
```

Después de la primera construcción también puede usarse `docker compose up`.
Compose espera a que PostgreSQL esté disponible. El backend ejecuta Flyway antes
de inicializar JPA y usa un usuario Linux sin privilegios dentro del contenedor.
Los puertos publicados están restringidos a `127.0.0.1`.

- Salud del backend y base: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI: http://localhost:8080/v3/api-docs

Swagger documenta el catálogo y la gestión de viajes/tramos. Actuator no publica datos
de configuración ni detalles de conexión. `docker compose down` conserva los
datos en el volumen; `docker compose down -v` los elimina deliberadamente.
Cambiar credenciales de `.env` no modifica un usuario en un volumen ya creado.

## Desarrollo con Java local

Levantar PostgreSQL con `docker compose up -d postgres`. Luego exportar las
credenciales que se hayan elegido en `.env`. Spring Boot no carga `.env`
automáticamente: ese archivo es consumido por Compose.

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:DB_USERNAME = 'flight_trip_manager'
$env:DB_PASSWORD = Read-Host 'Contraseña local' -MaskInput
.\mvnw.cmd spring-boot:run
```

En Linux/macOS usar `sh ./mvnw spring-boot:run` y variables de entorno equivalentes.
Si cambió `DB_PORT` o `DB_NAME`, configurar también `DB_URL`.

| Variable | Uso |
| --- | --- |
| `DB_USERNAME` | Usuario PostgreSQL; obligatorio |
| `DB_PASSWORD` | Contraseña PostgreSQL; obligatoria y sin default |
| `DB_URL` | JDBC URL; obligatoria salvo URL local por defecto o Compose |
| `DB_POOL_SIZE` | Máximo de conexiones; default 10 |
| `SPRING_PROFILES_ACTIVE` | `local`, `test` o `prod`; sin perfil local implícito |
| `SERVER_PORT` | Puerto interno de Spring Boot; default 8080 |
| `DB_NAME` | Nombre de base en Compose; default `flight_trip_manager` |
| `DB_PORT` | Puerto publicado de PostgreSQL; default 5432 |
| `BACKEND_PORT` | Puerto publicado del backend; default 8080 |

Los secretos se suministran por entorno. `.env` está excluido de Git y del contexto
Docker. No configurar credenciales en YAML ni registrar headers o payloads sensibles.

## Arquitectura y base de datos

Un proyecto Maven, un proceso y una base. Módulos: `trips`, `flights`,
`flightsearch`, `tracking`, `connections`, `budgets`, `catalog`, `shared`.
Ver [arquitectura](docs/architecture.md) y [CARD 0](docs/cards/card-0-bootstrap.md).

Cada módulo funcional incorporará `domain`, `application`, `infrastructure` y
`api`. El dominio será Java puro. Los módulos se comunican por puertos y contratos;
no por entidades JPA ni repositorios ajenos. `bootstrap` configura y coordina.

Flyway ejecuta V1 (siete esquemas funcionales) y V2 (tablas `catalog.airports`,
`catalog.airlines` y `catalog.locations`), más V3 (identidad y fechas de origen
para importación), V4 (`trips.trips` y `trips.trip_legs` con FK al catálogo)
y V5 (`trips.passengers`, con FK restrictiva al viaje). V6 agrega segmentos,
asignaciones de pasajeros e historial en el schema flights y FK compuestas de pertenencia.
`shared` no posee tablas. El historial de
Flyway vive en `public`. Las próximas migraciones usan números globales crecientes.
Nunca modificar una migración aplicada: agregar otra.

Hibernate usa `ddl-auto=validate` en todos los perfiles, con Open Session in View
deshabilitado y timestamps JDBC en UTC. No se usa H2. Un `Clock` UTC es inyectable.
El logging de aplicación es JSON mediante el formatter seguro de Spring Boot,
tanto en consola como en archivos si se configuran.

`prod` mantiene Swagger deshabilitado y exige configuración externa de base.
Ese perfil no convierte todavía al MVP sin autenticación en un servicio público.

## Tests y cobertura

```powershell
# Unitarios; no necesita Docker
.\mvnw.cmd --batch-mode --no-transfer-progress test

# Compilación, unitarios, empaquetado e integración con PostgreSQL real
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

Surefire ejecuta `*Test`; Failsafe ejecuta `*IT` durante `verify`. Si falta Docker,
la integración falla: no se omite silenciosamente. Testcontainers utiliza una
base efímera independiente de Compose y de cualquier base personal.

Los tests verifican reloj UTC, migración y repetición sin cambios, arranque JPA,
salud de la base, disponibilidad de Swagger y ausencia de endpoints Actuator
sensibles. CARD 0.1 agrega pruebas de errores HTTP, dispatch servlet, negociación
de contenido, redacción de logs, MDC y esquema OpenAPI. Los endpoints de prueba
están fuera del paquete de aplicación y no se incluyen en el JAR.
Informes: `target/surefire-reports`, `target/failsafe-reports` y
`target/site/jacoco/index.html`. JaCoCo mide unitarios e integración.

`clean verify` exige cobertura global de líneas >=80% con JaCoCo, combinando tests
unitarios e integración y sin exclusiones configuradas. Maven falla si falta
`target/jacoco.exec`, si no encuentra tests unitarios/de integración o si ArchUnit
detecta una infracción. CARD 1 prueba invariantes del catálogo, consultas y errores
por HTTP real, mapeos, restricciones SQL y contratos OpenAPI.

ArchUnit analiza sólo bytecode de producción. Las reglas de dominio, capas,
contratos, controllers, entidades JPA y módulos se ejecutan con los unitarios.
Los fixtures positivos y negativos se compilan en un directorio temporal; no
entran al JAR ni al cálculo de cobertura. También cubren arrays y tipos de SDK externos.

Comprobación opcional de rechazo de los gates (PowerShell 7 en Windows, sin Docker):

```powershell
.\scripts\verify-quality-gates.ps1
```

El script copia el POM a un proyecto aislado bajo `.tools/quality-gates-*`, comprueba
que falla con cobertura insuficiente y con datos ausentes, y conserva los logs.
No modifica fuentes ni reportes del proyecto principal. Para aceptar una card usar
siempre `clean verify` sin flags que omitan tests/instrumentación; `test` y `package`
por sí solos no ejecutan el gate final. Los reportes viejos no constituyen evidencia.

## Proveedores y límites actuales

No se consume ninguna API externa en esta card ni se requieren API keys.
Duffel, AirLabs y OpenSky se incorporarán detrás de puertos, con mocks y pruebas
que no dependan de servicios reales. No se permite scraping ni ofertas de OTAs.
Caffeine, resiliencia y scheduler se añadirán cuando exista su primer consumidor.

Próxima card: 5 — búsqueda de vuelos. Los vuelos se registran manualmente; todavía no hay providers ni jobs.

## Catálogo local (CARD 1)

| Método | Ruta | Consulta |
| --- | --- | --- |
| GET | `/api/v1/airports` | Aeropuertos por código, nombre, ciudad o país |
| GET | `/api/v1/airports/{iataCode}` | Aeropuerto por IATA de 3 letras |
| GET | `/api/v1/airlines` | Aerolíneas por código, nombre o país |
| GET | `/api/v1/airlines/{iataCode}` | Aerolínea por IATA de 2 caracteres alfanuméricos |
| GET | `/api/v1/locations` | Ciudades/estaciones por nombre, ciudad o país |
| GET | `/api/v1/locations/{id}` | Ubicación por UUID |

Listados: `q` opcional (máximo 100 caracteres, se recortan espacios), `page=0`
(0..1000000) y `size=20` (1..100). Búsqueda de subcadena literal sin distinguir
mayúsculas; no elimina acentos. Orden fijo por nombre y UUID.
Respuesta: `items`, `page`, `size`, `totalElements`, `totalPages`.
Una página vacía devuelve 200 y conserva el total de coincidencias.

Aeropuertos/aerolíneas: `active=true` por defecto; `false` lista sólo inactivos.
El detalle incluye inactivos para referencias históricas y acepta IATA en minúscula.
Ubicaciones: filtro opcional `type=CITY|TRAIN_STATION|BUS_STATION`.
Los aeropuertos no son locations. País usa código de dos letras en mayúsculas.

Ejemplos: `GET /api/v1/airports?q=buenos&size=10` y
`GET /api/v1/locations?type=TRAIN_STATION&q=central`.
Recurso inexistente: 404 con `AIRPORT_NOT_FOUND`, `AIRLINE_NOT_FOUND` o
`LOCATION_NOT_FOUND`; parámetros inválidos: 400 con el contrato de error común.

La base nueva queda vacía hasta una importación explícita: no hay semillas ficticias
de producción ni sincronización implícita al consultar. Las fixtures viven
en `src/test/resources` y sólo se cargan en PostgreSQL efímero de Testcontainers.
Los timestamps desconocidos y coordenadas desconocidas se conservan como null.
Decisiones, migración y evidencia: [CARD 1](docs/cards/card-1-local-catalog.md).

## Importación y sincronización explícita (CARD 1.1)

`POST /api/v1/catalog/imports` acepta JSON con `source`, `observedAt` y listas
`airports`, `airlines`, `locations`. Las listas pueden omitirse, pero el lote
debe contener entre 1 y 500 registros en total. Cada registro requiere
`externalId` estable dentro de su origen y tipo.

La identidad es `tipo + source + externalId`; el servidor asigna UUID al crear
y lo conserva al actualizar. source se normaliza a minúsculas; externalId conserva
mayúsculas y se recortan espacios. IATA/ICAO/país se normalizan a mayúsculas.
Los campos desconocidos se rechazan para detectar errores de escritura.

observedAt representa la fecha real de observación/revisión de los datos:
no puede ser futura ni anterior a 1970 y admite hasta microsegundos.
lastSyncedAt lo asigna el reloj UTC del servidor. Repetir una versión idéntica
devuelve UNCHANGED sin modificar UUID ni timestamps. Una versión más nueva se
actualiza; una anterior o una repetida con contenido diferente se rechaza.

El lote es una única transacción: cualquier conflicto revierte todos sus cambios.
Una importación concurrente devuelve 409 CATALOG_IMPORT_BUSY; puede reintentarse
con el mismo lote. No se eliminan ni desactivan registros omitidos.
Los registros suministrados son completos: null u omisión de ICAO/coordenadas
borra esos datos opcionales; `active` es obligatorio en aeropuertos/aerolíneas.
Los códigos y nombres que ya pertenecen a otro origen no se fusionan automáticamente.

Respuesta 200: contadores `created`, `updated`, `unchanged` e `items` con tipo,
externalId, UUID y resultado. Errores: 400 para entrada inválida; 409 para
identidad en conflicto, versión antigua/diferente o importación concurrente.

Para comenzar hay un [archivo mínimo curado y sus fuentes](examples/catalog/README.md).
Desde la raíz del proyecto, con el backend levantado:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/catalog/imports' `
  -ContentType 'application/json; charset=utf-8' `
  -InFile './examples/catalog/argentina-starter.json'
```

La importación no requiere nuevas variables de entorno ni API keys.
Los adapters futuros pueden invocar `CatalogImport` con contratos internos;
no hay scheduler, descarga automática ni catálogo mundial incluido.
Ver [CARD 1.1](docs/cards/card-1.1-catalog-import-sync.md).

## Viajes y tramos (CARD 2)

| Método | Ruta | Uso |
| --- | --- | --- |
| POST / GET | `/api/v1/trips` | Crear / listar viajes |
| GET / PATCH / DELETE | `/api/v1/trips/{tripId}` | Consultar / editar / eliminar viaje vacío |
| POST / GET | `/api/v1/trips/{tripId}/legs` | Agregar / listar tramos |
| PATCH / DELETE | `/api/v1/trips/{tripId}/legs/{legId}` | Editar / eliminar tramo |
| PATCH | `/api/v1/trips/{tripId}/legs/reorder` | Orden manual completo o retorno a automático |

Ejemplo de creación de viaje:

```json
{
  "name": "Viaje de noviembre",
  "startDate": "2026-11-01",
  "endDate": "2026-11-20",
  "totalBudgetUsd": 2500.00
}
```

Nombre y ambas fechas son obligatorios. Presupuesto USD es opcional, no negativo,
con hasta dos decimales; no se redondea silenciosamente. Los tramos referencian
origen y destino mediante `{"kind":"AIRPORT|LOCATION","id":"UUID-del-catálogo"}`.
Se validan existencia y aeropuertos activos al asignar una referencia nueva.

Todas las mutaciones de un viaje existente requieren su `version`: en el cuerpo
de POST/PATCH de tramos, PATCH del viaje y reordenamiento; en query para DELETE.
Cada mutación incrementa la versión del viaje, incluso si sólo cambia un tramo.
Una versión desactualizada devuelve 409: volver a consultar y revisar los cambios.
Los tramos devuelven `tripVersion`; el viaje devuelve `version`.

PATCH conserva propiedades omitidas. null elimina presupuesto/override o una
fecha/hora opcional cuando las reglas lo permiten. Acepta JSON y merge-patch JSON.
Enviar version y al menos un campo; propiedades desconocidas o duplicadas se
rechazan en PATCH. Ejemplo: `{"version":0,"manualStatusOverride":"CANCELLED"}`.

Los tramos admiten `departureDate`/`arrivalDate` sin hora y, opcionalmente,
`departureDateTime`/`arrivalDateTime` como instantes con offset. Se almacenan
en UTC; la fecha asociada a un instante es su día UTC y, si se envían ambos,
deben coincidir. Una hora desconocida queda null. Para cambiar a sólo fecha,
limpiar explícitamente el instante. Sólo PLANNED admite salida sin fecha.
Las fechas conocidas deben caber dentro del viaje.

Orden automático: fecha, hora conocida primero, creación y UUID; fechas desconocidas
al final. Orden manual: `{"version":2,"orderedLegIds":["UUID-1","UUID-2"]}` debe
incluir todos los tramos una vez. Lista vacía restaura automático. Agregar un tramo
en orden manual lo coloca al final; eliminarlo compacta posiciones.
No existe `orderingMode`.

Los estados se calculan al consultar usando Clock UTC, sin scheduler. El viaje
expone status, derivedStatus y manualStatusOverride. Todos los tramos necesarios
reservados y viaje futuro deriva UPCOMING. En esta card los estados de reserva
de los tramos son declarados manualmente; se conectarán con reservas reales en
la card de vuelos. No se infiere una reserva por tener fecha.

Listados: page desde 0, size default 20 y máximo 100; viajes filtran q por nombre.
Máximo 500 tramos por viaje para mantener acotada la edición del agregado.
Un viaje con tramos o pasajeros no se elimina: primero quitarlos explícitamente. Las FK
protegen referencias al catálogo y no hay cascadas de borrado.
Detalles, política de estados y evidencia: [CARD 2](docs/cards/card-2-trip-management.md).

## Pasajeros por viaje

Los pasajeros pertenecen al módulo trips y se identifican por UUID, no por nombre.
Se permiten homónimos. Nombre y apellido son obligatorios, se recortan espacios
externos y admiten hasta 100 caracteres cada uno. Las notas son opcionales, de hasta
2000 caracteres; conservan saltos de línea y tabulaciones. No hay campos de DNI,
pasaporte ni documentación: evitar incluir esos datos en las notas.

| Método | Ruta | Uso |
| --- | --- | --- |
| POST | `/api/v1/trips/{tripId}/passengers` | Crear pasajero |
| GET | `/api/v1/trips/{tripId}/passengers` | Listar con page y size |
| PATCH | `/api/v1/trips/{tripId}/passengers/{passengerId}` | Editar campos presentes |
| DELETE | `/api/v1/trips/{tripId}/passengers/{passengerId}?version=1` | Eliminar explícitamente |

Ejemplo de alta para un viaje con versión 0:

```json
{
  "version": 0,
  "firstName": "María",
  "lastName": "García",
  "notes": "Preferencia de ventanilla"
}
```

POST devuelve 201 con el pasajero y tripVersion actualizado. PATCH requiere version
y al menos un campo modificable; omitir conserva el valor y notes:null lo borra.
Nombre y apellido no admiten null. No se puede cambiar tripId ni id mediante PATCH.
Se rechazan campos desconocidos, claves duplicadas y tipos incorrectos.

Las mutaciones requieren la versión actual del viaje, también después de editar
sus tramos, y la incrementan una vez. Una versión obsoleta responde 409. DELETE
devuelve 204; consultar el viaje para obtener su nueva versión.

GET ordena por creación y UUID ascendente; page empieza en 0, size por defecto 20
y máximo 100. Viaje inexistente devuelve 404; un pasajero de otro viaje tampoco
puede editarse o eliminarse mediante una ruta ajena. Un listado vacío devuelve
items vacío: la versión sigue disponible en GET del viaje.

No se borra un viaje con pasajeros. Las FK restringen eliminaciones y no hay
cascadas. CARD 4 permite asignarlos a segmentos; un pasajero vinculado no puede
eliminarse hasta quitar sus asignaciones de vuelo.
Decisiones y pruebas: [CARD 3](docs/cards/card-3-passenger-management.md).

## Segmentos de vuelo

Cada registro representa un segmento concreto dentro de un tramo FLIGHT. Puede
haber varios segmentos y distintos pasajeros por segmento. Las referencias a
aerolínea y aeropuertos usan UUID del catálogo. Nuevas referencias requieren
registros activos; las referencias históricas pueden conservarse si se desactivan.
El número de vuelo usa el código IATA o ICAO de la aerolínea indicada y un sufijo
numérico, con letra final opcional.

| Método | Ruta | Uso |
| --- | --- | --- |
| POST | `/api/v1/trips/{tripId}/legs/{legId}/flights` | Registrar segmento |
| GET | `/api/v1/trips/{tripId}/legs/{legId}/flights` | Listado paginado |
| GET | `/api/v1/flights/{flightId}` | Consultar detalles |
| PATCH | `/api/v1/flights/{flightId}` | Editar con versión del viaje |
| DELETE | `/api/v1/flights/{flightId}?version=3` | Eliminar segmento y sus datos dependientes |
| GET | `/api/v1/flights/{flightId}/history` | Historial paginado |

Ejemplo de alta: reemplazar los UUID por referencias reales y version por la revisión
actual del viaje. El número debe corresponder a la aerolínea elegida.

```json
{
  "version": 2,
  "data": {
    "airlineId": "<airline-uuid>",
    "flightNumber": "AR1132",
    "flightDate": "2026-10-01",
    "originAirportId": "<origin-airport-uuid>",
    "destinationAirportId": "<destination-airport-uuid>",
    "schedule": {
      "scheduledDeparture": "2026-10-01T22:00:00Z",
      "scheduledArrival": "2026-10-02T10:00:00Z"
    },
    "operation": {"status": "SCHEDULED"},
    "booking": {
      "bookingReference": "ABC123",
      "pricePaid": {"amount": 350.25, "currency": "EUR"},
      "ticketTotal": {"amount": 900, "currency": "EUR"}
    },
    "connectionProtection": "UNKNOWN",
    "passengers": [{"passengerId": "<passenger-uuid>", "seat": "12A", "baggage": "1 bag"}]
  }
}
```

flightDate es la fecha de salida **local del aeropuerto de origen**; si se conoce
scheduledDeparture, su día en esa zona debe coincidir. Los horarios absolutos se
persisten en UTC, con hasta microsegundos. Schedule es opcional: no se inventan
horas. originalScheduledDeparture/Arrival conservan lo conocido al registrar,
incluso si después se modifica o borra el horario actual.

POST acepta `Idempotency-Key` opcional con UUID globalmente único, que pasa a ser
el ID del vuelo. Un reintento con el mismo contenido normalizado devuelve el mismo
vuelo sin avanzar la versión. Distinto contenido o viaje con esa clave produce
409. La garantía dura mientras exista ese registro; sin clave, cada POST es una
nueva alta. Modificar o borrar el registro no conserva una respuesta histórica
de idempotencia. Las altas usan INSERT explícito para impedir que una clave
concurrente de otro viaje sobrescriba el registro mediante merge.

PATCH usa application/json: version más los campos de data que se quieran cambiar,
directamente en la raíz. **Los grupos enviados reemplazan el grupo completo**;
los grupos omitidos se conservan. Por ejemplo:

```json
{"version": 3, "flightNumber": "AR1134", "operation": {"status": "BOARDING", "departureGate": "14"}}
```

operation requiere status y reemplaza sus cuatro campos de terminal/puerta;
enviar su contenido completo si se quieren conservar. booking y schedule también
se reemplazan completos; null los limpia. passengers reemplaza todas las
asignaciones, [] o null las quita. No se pueden cambiar id, tripId ni tripLegId.
Una lista admite hasta 100 pasajeros distintos, todos del mismo viaje.

El precio del segmento (booking.pricePaid), el total del ticket (booking.ticketTotal)
y el precio de cada pasajero son observaciones de distinto alcance: **no sumarlos**.
No se distribuye un total entre escalas ni pasajeros. amountUsd queda null si no
hay conversión conocida; para USD coincide con amount. No hay conversiones externas.
El PNR puede repetirse y no identifica de forma única un ticket ni acredita protección.
connectionProtection expresa protección de la conexión de entrada al segmento:
SAME_TICKET, SEPARATE_TICKETS o UNKNOWN, declarada explícitamente.

Los cambios de número, estado, terminales y puertas generan historial. Las ediciones
manuales pueden corregir un estado anterior y quedan registradas; las futuras
actualizaciones de proveedores necesitarán su propia política temporal. Cancelar
un vuelo sólo cambia ese segmento. El provider es MANUAL y lastSyncedAt es null.

El registro no determina cobertura completa de una ruta por pasajero. Los estados
de Trip/TripLeg mantienen la política declarada en CARD 2 hasta definir esa regla,
sin deducir cobertura de la mera existencia de un segmento o PNR. Los vuelos tampoco
se reordenan ni se restringen a una ruta idéntica entre todos los pasajeros.

La eliminación de un vuelo quita explícitamente su historial y sus asignaciones,
sin borrar pasajeros ni otros vuelos. Las FK impiden borrar tramos/pasajeros
referenciados o cambiar a terrestre un tramo que todavía contiene vuelos.
Ambos listados usan page desde 0, size por defecto 20 y máximo 100.
Detalles y resultados: [CARD 4](docs/cards/card-4-flight-segments.md).

## Búsqueda de vuelos (CARD 5)

Los aeropuertos deben existir y estar activos en el catálogo. La fecha de salida
es local al origen, desde hoy hasta 365 días; ida y regreso se desplazan juntos
en alternativas ±1/2/3, preservando la duración del viaje. Multicity queda fuera.

```http
POST /api/v1/flight-search/search?page=0&size=20
Content-Type: application/json

{
  "origin": "EZE",
  "destination": "MAD",
  "departureDate": "2026-12-10",
  "returnDate": "2026-12-20",
  "flexDays": 3,
  "adults": 1,
  "childAges": [],
  "cabin": "ECONOMY",
  "filters": {
    "airlines": ["IB"],
    "maxStops": 1,
    "departureFrom": "08:00",
    "departureTo": "23:00",
    "maxDurationMinutes": 1200,
    "checkedBagRequired": true,
    "excludedAirports": []
  }
}
```

En mock quitar el filtro IB o usar ZZ, la aerolínea sintética. Los códigos EZE/MAD
son ejemplos: importarlos primero al catálogo. Omitir returnDate para sólo ida.
Hasta nueve pasajeros en total y un bebé menor de dos años por adulto.
El horario y duración máxima se aplican a cada sentido; la duración incluye conexiones.
El equipaje requerido debe estar incluido para todos los pasajeros y segmentos.

- POST devuelve searchId, variantes con estado, advertencias y resultados paginados.
- GET `/api/v1/flight-search/results/{searchId}` pagina sin llamar al proveedor.
- GET `/api/v1/flight-search/history` lista únicamente criterios guardados.
- page inicia en 0; size por defecto 20, máximo 100.

El resultado distingue synthetic/testMode, importe original, amountUsd nullable,
equipaje INCLUDED/NOT_INCLUDED/UNKNOWN y missingCosts. El precio es parcial: no se
inventan importes de asiento, equipaje adicional o traslado. Las monedas se ordenan
en grupos separados, USD primero; score menor significa mejor equilibrio dentro
de su moneda. penalties explica precio, duración, escalas, horario, equipaje y
datos faltantes. La fórmula y los pesos están en [CARD 5](docs/cards/card-5-flight-search.md).

Configuración: `FLIGHT_SEARCH_MODE=mock|disabled|duffel`. El perfil general usa
disabled; local y Compose usan mock. Para Duffel configurar `DUFFEL_ACCESS_TOKEN`
con un token de **test** y mode=duffel. No hay fallback silencioso a mock.
El cliente rechaza respuestas live, no compra ni crea órdenes. No se verificaron
credenciales reales durante el desarrollo; los tests usan un servidor HTTP local.

Los criterios equivalentes reutilizan resultados en memoria por 180 segundos,
con hasta 32 búsquedas almacenadas y dos búsquedas nuevas concurrentes por proceso.
Un reinicio/expulsión/TTL produce 410 al pedir resultados. Las ofertas vencidas
se quitan sin recalcular su ranking; una página posterior puede tener menos ofertas.
Las cantidades de cada variante describen la búsqueda original.
Se responde 200 con partial si faltan fechas/proveedores o hubo truncamiento;
si todos fallan, 503. Saturación local produce 429. Un resultado vacío válido es 200.
El historial también registra intentos nuevos sin resultados; los aciertos de
caché no agregan registros. No se persisten ofertas ni respuestas crudas.

## Errores HTTP y logs

Los errores MVC y el fallback del servlet usan `application/json`, incluso ante
un `Accept` no soportado. El status HTTP se conserva; por ejemplo, 400 para datos
inválidos, 404 para recursos inexistentes, 405 para métodos no permitidos, 406 para
formatos de respuesta no soportados y 500 para errores inesperados.

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": {"name": "Invalid value"},
  "timestamp": "2026-09-23T12:00:00Z"
}
```

`details` nunca incluye valores rechazados ni mensajes de excepciones. Los mensajes
de validación son deliberadamente genéricos; reglas específicas incorporarán mensajes
seguros cuando exista dominio. Los headers de protocolo, como `Allow`, se conservan.
OpenAPI incorpora `ApiError` y respuestas comunes 400/500 sin sobrescribir las
respuestas específicas de cada endpoint. `/error` es infraestructura y no se muestra
como operación pública en Swagger.

Cada petición recibe `X-Request-Id` generado internamente. Los logs de finalización
incluyen `requestId`, `module`, `operation`, `durationMs`, `status` y, cuando aplica,
`errorCode`. `operation` utiliza el patrón de ruta, sin valores de path ni query.
Los casos de uso podrán agregar `provider`, `entityId` y metadatos permitidos.

Se redactan completamente valores etiquetados PNR, booking reference, ticket,
API key, tokens, password, secret y Authorization con `[REDACTED]`. El formatter
omite campos estructurados/MDC no permitidos, objetos arbitrarios y mensajes o
stacktraces de excepciones; conserva el tipo de error para diagnóstico.
Esto reduce detalle diagnóstico deliberadamente. No reemplaza la regla de nunca
loggear cuerpos, headers, DTOs o secretos sin etiqueta. El MDC se restaura al
terminar cada petición síncrona; async requerirá adaptación explícita.

Decisiones y resultados: [CARD 0.1](docs/cards/card-0.1-errors-observability.md).

Reglas y gates: [CARD 0.2](docs/cards/card-0.2-architecture-quality-gates.md).
