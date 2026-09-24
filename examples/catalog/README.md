# Carga inicial mínima, curada manualmente

`argentina-starter.json` contiene tres registros reales para probar el flujo:
un aeropuerto, una aerolínea y una ciudad. No es un catálogo completo ni una
integración automática con proveedores. El archivo no se carga al arrancar ni
se incluye en el JAR; su importación es explícita.

Las claves externalId pertenecen al origen local `manual-curated-ar`, no son
identificadores oficiales de un proveedor. observedAt registra la revisión manual
de este lote (2026-09-24 00:54:07 UTC, 23 de septiembre en Buenos Aires).
El servidor asigna lastSyncedAt cuando acepta una versión nueva.

## Referencias

- Ezeiza, denominación Ministro Pistarini, localidad y código EZE:
  [ORSNA / Argentina.gob.ar](https://www.argentina.gob.ar/noticias/se-reglamenta-el-uso-flexible-de-mostradores-de-check-demanda-de-las-aerolineas-en-el).
- Código SAEZ y denominación del aeropuerto:
  [AIP Argentina, ANAC/EANA](https://ais.anac.gob.ar/descarga/aip-68011bf3df145).
- Aerolíneas Argentinas, códigos AR/ARG y país:
  [directorio de miembros de IATA](https://www.iata.org/en/about/members/airline-list/aerolineas-argentinas/13/).
- Zona America/Argentina/Buenos_Aires para Buenos Aires:
  [tabla de zonas de IANA](https://data.iana.org/time-zones/tzdb/zone1970.tab).

active=true es la habilitación de estos registros en el catálogo local, no una
afirmación del estado operativo de un vuelo ni una consulta en tiempo real.
Las coordenadas se dejan en null: no se copiaron las coordenadas representativas
de una zona horaria como si fueran las del aeropuerto.

## Importación

Con el backend local levantado, desde la raíz del proyecto:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/catalog/imports' `
  -ContentType 'application/json; charset=utf-8' `
  -InFile './examples/catalog/argentina-starter.json'
```

También se puede pegar el JSON en Swagger. Primera carga en una base vacía:
created=3, updated=0, unchanged=0. Repetición: created=0, updated=0, unchanged=3,
con los mismos UUID y timestamps. Consultar luego /api/v1/airports/EZE,
/api/v1/airlines/AR y /api/v1/locations?q=Ezeiza.

Al revisar datos posteriormente, conservar source/externalId y actualizar
observedAt a la fecha real de la nueva revisión. Un cambio con el mismo
observedAt se rechaza con 409. No avanzar la fecha sólo para forzar un reintento.
Si esos códigos ya están asignados a otro origen, la API devuelve 409 y no los
fusiona automáticamente.
