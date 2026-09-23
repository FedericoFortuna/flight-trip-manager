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

## Dependencias futuras permitidas

Todas se limitan a contratos de aplicación públicos; no al dominio o persistencia
de otro módulo. Los puertos de salida pertenecen al consumidor y los adapters
traducen contratos ajenos.

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

V1 sólo crea esquemas; por eso `ddl-auto=validate` aún no valida tablas de negocio.
No se introducen tablas ficticias para ejercitar Hibernate.

## Límites de CARD 0

No auth, frontend, providers, scheduler, cachés o reglas de negocio. Los paquetes
raíz documentan propiedad; sus capas internas aparecen cuando se implementan.
Errores uniformes y masking: CARD 0.1. ArchUnit y gate global >=80%: CARD 0.2.

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
