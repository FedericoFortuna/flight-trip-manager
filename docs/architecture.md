# Decisiones de arquitectura

## ADR 001 — Bootstrap modular

Estado: aceptado para CARD 0.

Un único Maven artifact y Spring Boot application, Java 17. Se elige Spring Boot
3.5.16 con Springdoc 2.8.17, compatibles con Java 17 y JUnit 5. Boot gestiona las
versiones de JUnit, Mockito y Testcontainers, además del resto
de dependencias salvo versiones indicadas en el POM. Las actualizaciones se
realizan deliberadamente y con `clean verify`, no mediante rangos dinámicos.

Fuentes: https://docs.spring.io/spring-boot/3.5/system-requirements.html y
https://springdoc.org/v2/.

La prueba real de integración detectó que Spring Test de Boot 4 requiere APIs de
JUnit 6; forzar JUnit 5 producía NoSuchMethodError. La línea 3.5 conserva el stack
solicitado sin overrides incompatibles. Revisar su ventana de soporte antes de
un despliegue público; la actualización a Boot 4 requiere acordar el cambio a JUnit 6.

Cada módulo mantiene dominio sin frameworks, aplicación con puertos, infraestructura
y API HTTP. No se crean implementaciones funcionales ni repositorios en el bootstrap.
MapStruct y Lombok están disponibles, pero no se inventan mappers para justificar su uso.

## Dependencias permitidas

Los módulos funcionales exponen `application.contract` y `application.port.in`;
nunca dominio, repositorios, puertos de salida ni implementaciones. Los puertos
de salida pertenecen al consumidor y sus adapters traducen contratos ajenos.
`shared.domain` permite reutilizar valores comunes, excluyendo abstracciones de
repositorio. La API también puede referenciar el contrato HTTP `shared.api.error.ApiError`.
No se permite consumir `shared.infrastructure` ni servicios internos de shared.

| Consumidor | Módulos permitidos |
| --- | --- |
| catalog | shared |
| trips | catalog, shared |
| flights | trips, catalog, shared |
| flightsearch | trips, catalog, shared |
| budgets | trips, shared |
| tracking | flights, catalog, shared |
| connections | trips, flights, catalog, shared |
| shared | ninguno |

`bootstrap/workflows` podrá coordinar puertos de varios módulos dentro de una
transacción para invariantes críticas. No consulta repositorios ni contiene
políticas de negocio. Los eventos internos no sustituyen consistencia atómica
cuando guardar una reserva exige desactivar alternativas.

## Persistencia

Una base PostgreSQL con esquemas funcionales y secuencia Flyway global.
`shared` no es un almacén de entidades. Las entidades JPA se crean separadas del
dominio en sus respectivas cards; no habrá relaciones JPA entre módulos.
Las FK entre esquemas podrán reforzar integridad y se documentarán al introducirlas.

V1 crea los esquemas; V2 agrega las tres tablas del catálogo local; V3 agrega
identidad de origen y fechas de importación sin reemplazar UUID existentes.
`ddl-auto=validate` comprueba sus entidades JPA al arrancar.

## Límites de CARD 0

No auth, frontend, providers, scheduler, cachés o reglas de negocio. Los paquetes
raíz documentan propiedad; sus capas internas aparecen cuando se implementan.
Errores uniformes y masking: CARD 0.1. ArchUnit y gate global >=80%: CARD 0.2.

## ADR 002 — Controles ejecutables (CARD 0.2)

Estado: implementado. Reglas en `src/test/java/com/flighttripmanager/architecture`.
Referencia de ArchUnit: https://www.archunit.org/userguide/html/000_Index.html.

- Código bajo módulos declarados y capas domain/application/infrastructure/api.
- Dominio: JDK (sin java.net/java.sql), dominio propio y valores de shared.domain;
  sin Spring, JPA, Jackson, HTTP o SDKs. Normalización al tipo base para arrays.
- Aplicación: dominio/puertos propios y contratos permitidos; sin API, persistencia,
  HTTP o SDKs. Sólo se permiten anotaciones Spring de componentes/transacciones
  y SLF4J como dependencias técnicas adicionales. CARD 1 permite también MapStruct
  para mapeos de dominio a contratos; no habilita HTTP/JPA. Los contratos públicos
  siguen siendo puros y no pueden depender de MapStruct.
- API: sin infraestructura, repositorios ni implementaciones de application.
  Controllers deben estar en API y usar puertos de entrada/DTOs, no modelos de dominio.
- Modelos anotados JPA únicamente en infrastructure.persistence.entity.
- Matriz de módulos verificada y ciclos prohibidos, incluyendo bootstrap/shared.
- bootstrap.configuration es la raíz de ensamblado y puede conectar componentes;
  bootstrap.workflows sólo coordina contratos/puertos de entrada. Ningún módulo
  funcional puede depender de bootstrap.

No se congela deuda existente ni se habilitan reglas vacías globalmente. Se importa
el directorio que contiene la aplicación compilada y se verifica que la clase
principal esté presente. Las reglas aplicables a capas todavía ausentes se prueban
con fixtures compilados que demuestran tanto aceptación como rechazo.

Límites: ArchUnit verifica dependencias estáticas del bytecode, no reflexión por
strings, SQL construido dinámicamente ni la semántica interna de métodos. Eso
requiere revisión y tests funcionales en las cards correspondientes.

JaCoCo exige >=80% de líneas de producción en verify (BUNDLE, sin exclusiones
configuradas). Enforcer exige los datos de cobertura. Surefire/Failsafe exigen
tests presentes. La aceptación reproducible es `clean verify`, sin omitir tests;
el build Docker usa package porque la verificación completa ocurre antes.

## ADR 003 — Catálogo local de consulta (CARD 1)

El catálogo es dueño de Airport, Airline y Location. Las consultas HTTP invocan
`CatalogLookup`; los módulos consumidores sólo acceden a ese puerto y a sus
contratos públicos. `CatalogStore` es un puerto saliente privado del módulo,
implementado por Spring Data JPA. Las tres fronteras usan MapStruct:
JPA → dominio → contrato de aplicación → respuesta HTTP.

Los casos de uso ejecutan transacciones de sólo lectura; no exponen Page de Spring,
entidades JPA ni tipos de dominio a otros módulos. El dominio es Java puro y
valida identidad, textos, códigos, coordenadas y zona horaria.
LocationKind es el enum del contrato y se mapea al LocationType interno.

Se exige IATA en este MVP porque el detalle de aeropuertos/aerolíneas usa ese código;
ICAO y fecha de sincronización son opcionales. Aeropuertos requieren una zona
horaria reconocida por Java; coordenadas son opcionales como par. No se inventa
un timestamp de sincronización ni se usa UTC como sustituto de una zona desconocida.
Location soporta CITY/TRAIN_STATION/BUS_STATION, sin coordenadas ni pertenencia a Trip.

Los listados se acotan a 100 registros, con orden name/id y filtro literal.
La paginación por offset es suficiente para el catálogo local; no garantiza una
foto estable entre peticiones si los datos cambian. Una búsqueda de subcadena
puede recorrer la tabla: no se introduce un motor de búsqueda ni índices GIN
sin medir antes volumen y latencia.

Las tablas vacías son un estado válido. Carga, normalización de proveedores,
identidad externa y sincronización quedan en CARD 1.1. Antes de importar,
reevaluar códigos compartidos/reasignados y locations homónimas: la identidad
natural mínima actual no resuelve desambiguación geográfica mundial.

## ADR 004 — Importación atómica con identidad externa (CARD 1.1)

Un lote explícito invoca el puerto público CatalogImport. El caso de uso valida
todos los registros y ejecuta una transacción sobre CatalogImportStore. La entrada
HTTP y persistencia se mapean con MapStruct; no se agregan dependencias técnicas
a contratos ni dominio. Un adapter de proveedor futuro normalizará sus datos
hacia CatalogImportBatch, sin exponer DTOs externos.

Cada tipo mantiene una clave única source/externalId y una versión source_observed_at.
El UUID pertenece al catálogo y se conserva al cambiar los atributos, incluso IATA.
No se asume que compartir código o nombre implica compartir identidad entre
orígenes. Los registros V2 sin origen quedan intactos y los conflictos requieren
reconciliación explícita; esta card no implementa adopción ni fusión automática.

El cliente declara observedAt, el servidor asigna lastSyncedAt. Fechas anteriores
son rechazadas; una fecha idéntica exige contenido normalizado idéntico. Una fecha
más nueva actualiza también metadatos aunque los atributos permanezcan iguales.
Las fechas se limitan a microsegundos para evitar divergencias de precisión con
PostgreSQL. Las coordenadas se normalizan a seis decimales sin redondeo silencioso.

Un advisory lock transaccional PostgreSQL serializa las importaciones, incluso
entre instancias. El intento es no bloqueante: si está ocupado se devuelve 409.
Las restricciones UNIQUE/CHECK siguen protegiendo integridad. El lock no coordina
escrituras SQL manuales; todas las escrituras de aplicación deben pasar por el puerto.
Guardar cada registro con flush permite detectar conflictos antes del commit y
revertir el lote completo con un error seguro.

Los lotes tienen 1..500 registros; se procesa el catálogo local sin infraestructura
de jobs, cachés, colas ni consumo de cuota externa. El límite es de registros
lógicos, no de bytes HTTP. El endpoint conserva el alcance local sin autenticación
del MVP. Una futura publicación requiere límites de transporte y control de acceso.

Las locations homónimas conservan la restricción natural de CARD 1; ahora se
rechazan como conflicto, en vez de fusionarse. La desambiguación geográfica y los
códigos compartidos/reasignados entre identidades se resolverán cuando se conecte
un dataset concreto. La carga manual mínima documentada permite comenzar sin
presentar datos ficticios como catálogo de producción.

## ADR 005 — Agregado de viaje, fechas y concurrencia (CARD 2)

Trip es dueño de sus TripLeg. Dominio Java puro, modelos JPA separados y MapStruct
en fronteras. TripManagement es el puerto público; TripStore y CatalogPlaces son
puertos salientes del módulo. CatalogPlacesAdapter consume únicamente CatalogLookup
y sus contratos; se agrega airportById al puerto de catálogo, sin nuevo endpoint HTTP.
No hay repositorios ni entidades JPA cruzados. FK explícitas garantizan existencia
de aeropuertos/locations y restringen eliminaciones.

Cada viaje requiere nombre, startDate y endDate. Un tramo puede estar sin fechas
sólo como PLANNED. Fecha sin hora se conserva como LocalDate y no se convierte
en medianoche. Cuando se conoce un instante, se conserva Instant UTC y su día UTC;
ambos deben coincidir si se suministran juntos. Esta decisión inicial no modela
una zona horaria propia del viaje. Las fechas conocidas deben caber en su intervalo.
Los importes usan BigDecimal/numeric(19,2); se rechaza precisión monetaria adicional.

Orden manual completo: todas las posiciones 0..n-1 o todas null. No hay orderingMode.
Reordenar exige todos los IDs exactamente una vez; lista vacía restaura automático.
El orden automático compara fecha, hora conocida antes de desconocida, createdAt
y UUID. Sin fecha queda al final. El agregado limita el itinerario a 500 tramos;
los endpoints de listas son paginados y los viajes se leen por página con una
consulta de tramos agrupada, evitando una consulta por viaje.

Las mutaciones bloquean la fila del viaje, comprueban la versión esperada y avanzan
una revisión explícita del agregado. El bloqueo y la comparación se realizan en
una transacción; así una edición de tramo también invalida versiones del viaje.
No se depende de @Version JPA: la revisión es parte del contrato y se controla
bajo el lock del padre. Las lecturas usan REPEATABLE_READ para ensamblar viaje
y tramos desde una instantánea coherente. Las escrituras usan READ_COMMITTED.

La unicidad (trip_id, manual_order) es diferible para permitir permutaciones
atómicas. La consistencia global de posiciones y límites de fechas entre tablas
se valida en el agregado bajo lock; CHECK/UNIQUE/FK refuerzan invariantes de filas.
DELETE de un viaje con tramos se rechaza; no se crean cascadas. Futuras referencias
de vuelos deben seguir usando FK RESTRICT y comportamiento explícito.

Estado calculado al leer, usando Clock UTC, sin persistir un estado derivado que
pueda quedar obsoleto por el paso del tiempo. PLANNING si no hay tramos; CANCELLED
si todos están cancelados; COMPLETED si todos los no cancelados terminaron;
IN_PROGRESS si alguno está en progreso; PARTIALLY_BOOKED si parte está cubierta.
Si todos los no cancelados están cubiertos: UPCOMING antes del inicio del viaje,
IN_PROGRESS a partir del inicio. Sin cobertura, PLANNING.

BOOKED/UPCOMING declarados en un tramo derivan UPCOMING antes de salida e
IN_PROGRESS desde salida. Con llegada exacta, COMPLETED al alcanzar ese instante;
con sólo fecha de llegada, al terminar ese día. Sin llegada no se inventa fin.
Otros estados explícitos del tramo se preservan. El override del viaje tiene
prioridad sobre su estado efectivo, pero no cambia ni cancela sus tramos.
BOOKED del viaje sigue disponible como override; no sustituye UPCOMING derivado.

En CARD 2 la cobertura de reserva es declarada en los tramos. La consistencia con
pasajeros y reservas reales pertenece a las cards siguientes. No hay dependencia
trips→flights ni modelos vacíos de reserva para anticiparla.

## ADR 006 — Pasajeros pertenecientes al viaje (CARD 3)

Passenger pertenece a trips, junto con Trip y TripLeg. Tiene identidad UUID,
tripId inmutable, nombre, apellido y notas opcionales. No hay catálogo global de
personas, documentos personales ni unicidad por nombre. Dominio y persistencia
siguen separados, con MapStruct en sus fronteras y PassengerManagement como puerto
público de aplicación. La asociación con vuelos se implementará en CARD 4.

Los pasajeros se consultan mediante un repositorio paginado separado, sin cargar
la colección entera en Trip ni relaciones JPA eager. Conservan la misma frontera
de consistencia del viaje: cada mutación toma el bloqueo del padre, verifica la
versión y avanza su revisión en una única transacción. saveHeader permite actualizar
esa revisión sin reescribir tramos. Las lecturas usan REPEATABLE_READ para que la
versión devuelta y los pasajeros correspondan a la misma instantánea.

La fila padre también coordina la carrera entre crear pasajero y eliminar viaje.
TripService rechaza explícitamente viajes con pasajeros, reforzado por FK RESTRICT
en V5. No se borran pasajeros en cascada. No hay referencias de vuelos todavía;
su futura eliminación deberá conservar esa política explícita.

La API permite sólo nombre, apellido y notas, además de la versión esperada.
PATCH diferencia omisión de null, rechaza claves desconocidas/duplicadas y conserva
campos omitidos. El lector JSON compartido del módulo también rechaza contenido
posterior al objeto. Las validaciones y errores no exponen valores personales.
Las notas son texto libre: la ausencia de campos de documentación no detecta ni
elimina automáticamente datos sensibles que un usuario escriba en ellas.

No se agregan dependencias, módulos, cachés, eventos ni infraestructura.
Las operaciones aún leen los tramos acotados del viaje para obtener su estado;
si ese costo se vuelve significativo se podrá agregar una proyección de revisión
del padre, manteniendo el bloqueo y sin relajar su consistencia.

## ADR 007 — Segmentos manuales, referencias y cambios (CARD 4)

flights mantiene su propio dominio, persistencia, contratos y API. Consume
TripFlightAccess y CatalogLookup mediante adapters y puertos salientes propios.
No importa entidades ni repositorios de trips/catalog. Se agrega airlineById al
contrato de catálogo; las FK usan identidades estables aunque cambien los códigos.

TripFlightAccess coordina el bloqueo del padre y valida pertenencia de pasajeros
en una consulta agrupada. Su transacción es MANDATORY: participa en la de flights
y no puede confirmar parcialmente una revisión. Todas las mutaciones siguen
usando la versión del viaje. Para editar/eliminar un vuelo se consulta primero
una proyección de su padre, se bloquea el viaje y recién entonces se carga la
entidad; evita reutilizar una entidad JPA obsoleta cargada antes del bloqueo.
Lecturas de vuelo, página y revisión comparten REPEATABLE_READ.

V6 usa FK compuestas para enlazar vuelo con tramo del mismo viaje y tipo FLIGHT,
y asignaciones con vuelo y pasajero del mismo viaje. RESTRICT protege altas,
bajas y cambios de tipo incluso fuera de Java. Son constraints entre schemas,
sin asociaciones JPA ni acceso cruzado a repositorios.

La relación de pasajeros admite asiento, equipaje, ticket e importe propios.
Los campos de booking a nivel segmento no se copian automáticamente a cada pasajero.
Hasta 100 asignaciones por vuelo; cada listado hace una consulta agrupada para
cargar las asignaciones de la página, evitando N+1. Modificar un vuelo reemplaza
explícitamente sus asignaciones en la misma transacción.

El PNR no es clave única y no se crea Booking. Importe de segmento, total de ticket
y desglose de pasajero se preservan por separado, con moneda original y conversión
USD nullable. El usuario confirmó no repartir ni sumar automáticamente estos importes;
su consolidación pertenece a presupuesto. No se infiere protección de conexión del PNR.

flightDate representa el día local del aeropuerto de salida, mientras que Trip
conserva su intervalo de planificación por fecha y los instantes se almacenan en UTC.
En esta card no se fuerza que la fecha local de cada segmento coincida con la
fecha UTC del tramo ni se valida completitud de rutas por pasajero. Las revisiones
de fechas del viaje tampoco modifican automáticamente los vuelos.

Se preserva el horario programado conocido al registrar. Horarios estimados y
reales son valores actuales; historial contiene número, estado, terminales y puertas.
Las correcciones manuales son explícitas y auditadas sin una matriz de proveedor.
No se activa polling ni se aplica una regla de “último dato gana” entre providers.

POST admite una clave UUID opcional de idempotencia que actúa como identidad
del vuelo. Bajo bloqueo del padre se compara el contenido normalizado y se permite
replay sin nueva revisión. Altas usan EntityManager.persist, no merge: la PK resuelve
colisiones concurrentes de una clave entre viajes sin sobrescribir datos. Borrar
un vuelo borra explícitamente su historial y enlaces; la idempotencia no sobrevive
al borrado. No se agregan tombstones ni infraestructura de deduplicación general.

PATCH reemplaza grupos proporcionados y preserva los omitidos. Sólo acepta JSON,
sin anunciar semántica JSON Merge Patch recursiva. Requiere status cuando se
reemplaza operation para impedir un cambio de estado implícito al editar una puerta.

## Decisiones funcionales por cerrar antes de sus cards

- Cobertura de reserva por pasajeros y trayectos.
- Protección de conexión explícita, sin inferir garantía a partir del PNR.
- Precio compartido sin Booking ni doble contabilización.
- Alcance de alternativas round-trip y siete variantes de fechas.
- Moneda USD nullable cuando no haya conversión fiable.
- Identidad de operación aérea para compartir polling entre segmentos registrados.
- Matriz de transiciones operativas y políticas de cierre del tracking.
