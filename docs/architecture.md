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

## Decisiones funcionales por cerrar antes de sus cards

- Fecha conocida sin hora y orden manual completo/automático.
- Precedencia de estados y significado derivado de BOOKED frente a UPCOMING.
- Cobertura de reserva por pasajeros y trayectos.
- Protección de conexión explícita, sin inferir garantía a partir del PNR.
- Precio compartido sin Booking ni doble contabilización.
- Alcance de alternativas round-trip y siete variantes de fechas.
- Moneda USD nullable cuando no haya conversión fiable.
- Identidad de operación aérea para compartir polling entre segmentos registrados.
- Matriz de transiciones operativas y políticas de cierre del tracking.
