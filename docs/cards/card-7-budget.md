# CARD 7 — Presupuesto y gastos de transporte

## Resultado

Presupuesto total, por categoría y por tramo. Gastos manuales con estimación y
real separados, moneda original, conversión USD opcional y saldo explícitamente
incompleto si faltan conversiones. La proyección selecciona real o estimado por
registro, sin sumarlos entre sí. Módulos afectados: budgets y contrato público de trips.

La preferencia del usuario desde esta card es evitar tests unitarios y validar
con Docker, curl y base de datos. No se agregaron ni ejecutaron tests unitarios.

## Decisiones

- Trip.totalBudgetUsd continúa siendo la única fuente del límite total. El PUT
  de presupuesto y el PATCH existente del viaje actualizan el mismo valor.
- PUT total/categorías es reemplazo: categories={} limpia esas asignaciones y
  totalBudgetUsd=null deja el total sin límite. PUT de tramo con amountUsd=null
  elimina su límite. Límites no negativos, hasta dos decimales, sin redondeo.
- Categorías cerradas: FLIGHTS, TRAINS, BUSES, CAR, OTHER_TRANSPORT. No se agregan
  hoteles, gastronomía ni actividades.
- Categoría y tramo son dos vistas del presupuesto. No se suman al límite total.
  Superarlo es una advertencia de planificación, no un error que impida editar.
- Cada gasto puede tener estimated y actual. Se conserva la estimación para
  comparación; forecast usa actual si está presente, incluso cero, o estimated.
- Un real en moneda extranjera sin conversión prevalece sobre el estimado conocido:
  forecast se marca incompleto y no sustituye su valor por la estimación.
- Importes no negativos, hasta cuatro decimales. USD coincide con el importe
  original; para otras monedas amountUsd es null salvo conversión explícita con
  conversionNote. Se conserva cada moneda original. No se inventan tipos de cambio.
- Gasto con legId requiere pertenencia al viaje y categoría compatible con el
  transporte del tramo. Gastos compartidos sin reparto conocido tienen legId null.
- Un total de ticket compartido se registra una sola vez. No se importan/suman
  automáticamente precios de segmentos, asignaciones por pasajero ni saved options.
  Son alcances distintos y no hay una identidad Booking que habilite esa deducción.
- referenceKey opcional es una clave contable explícita, única por viaje. Un
  duplicado devuelve 409; no se usa PNR para inferir identidad. Sin clave no hay
  garantía de deduplicación semántica. Ante respuesta perdida, consultar el listado
  antes de crear de nuevo; no hay replay idempotente de POST.
- accountingScope=MANUAL_LEDGER_ONLY y EXPLICIT_TRANSPORT_EXPENSES_ONLY hacen visible
  que los totales representan los gastos registrados, no la cobertura completa
  del viaje. Un presupuesto vacío no demuestra que no existan costos.
- Cancelar un tramo/vuelo no elimina costos ni implica reembolso. Confirmar BOOKED
  no vuelve a sumar gastos. Registrar o corregir un importe real es explícito.

## Cálculo y consistencia

Cada balance expone estimated, actual y forecast, con knownUsd, missingUsdCount y
originalAmounts agrupados por moneda. remainingUsd = límite - forecast.knownUsd
solamente si existe límite y missingUsdCount=0. remainingAfterActualUsd aplica
la misma regla a actual. Los saldos negativos se conservan y el total emite
OVER_BUDGET. También se informan excesos de asignaciones de categoría o tramo.

GET calcula los agregados actuales con SQL GROUP BY. No se guarda un total
derivado susceptible de desactualizarse, no se recalcula ante cada cambio de nombre
o nota y no hay consumo externo. Al consultar después de una operación relevante
se observa el registro actualizado. Las lecturas compuestas usan REPEATABLE_READ.

Escrituras: bloqueo pesimista del viaje, validación de version y avance de una
revisión en la misma transacción que límites/gastos. Referencias y tipos se validan
con el contrato público de trips. Las FK refuerzan pertenencia y evitan eliminar
tramos/viajes con datos contables asociados. Para cambiar el transporte de un tramo
con gastos, desasociar/reclasificar explícitamente los gastos primero.

## Archivos principales

Desde src/main/java/com/flighttripmanager:

- budgets/domain/model: BudgetMoney, TransportExpense, TransportCategory,
  SpendingAggregate, BudgetBalance y BudgetRuleViolation.
- budgets/application: BudgetManagement, BudgetService, BudgetTrips/BudgetStore,
  contratos de gastos/límites/resúmenes y BudgetMapper.
- budgets/infrastructure/persistence: entidades separadas, embeddable monetario,
  repositorios, mapper y JpaBudgetStore; agregación SQL sin cargar todo el registro.
- budgets/infrastructure/trips/BudgetTripsAdapter.java.
- budgets/api: BudgetController, DTOs, BudgetApiMapper, BudgetJsonReader y
  BudgetExceptionHandler. JSON estricto y errores sin valores rechazados.
- trips/application: TripBudgetAccess, BudgetTripContext y TripBudgetService.
- README y ADR 010 documentan políticas, fórmulas y validación.

Otros archivos:

- src/main/resources/db/migration/V9__create_transport_budgets.sql.
- scripts/validation/card-7.compose.yml: PostgreSQL/API aislados.
- scripts/validation/card-7.ps1: recorrido funcional usando curl y contraste SQL.
- scripts/validation/card-7-constraints.sql: intentos inválidos en transacción revertida.
- BootstrapIT: se actualiza solamente la expectativa de nueve migraciones;
  la suite no se ejecutó durante esta card.

## Base de datos

V9 agrega:

- budgets.budget_limits: límite CATEGORY o LEG, con XOR de campos, importe
  numeric(19,2), FK a viaje y FK compuesta al tramo del mismo viaje.
- UNIQUE(trip_id,category) y UNIQUE(trip_id,leg_id); índice budget_limits_scope_idx.
- UNIQUE(trip_id,id) en trips.trip_legs para la nueva FK de pertenencia.
- budgets.transport_expenses: categoría, descripción, reference_key, pertenencia,
  campos estimados/reales numeric(23,4), monedas, conversiones y fechas.
- FK compuesta de gasto a trip/leg/transport_type, restrictiva.
- expense_reference_key_uq evita repetir claves contables dentro del viaje.
- CHECK de categorías de transporte, coherencia de tipo, al menos un importe,
  pares moneda/monto, importes no negativos y no NaN, consistencia USD y
  nota obligatoria cuando hay conversión de otra moneda.
- Índices transport_expenses_page_idx y transport_expenses_leg_idx.
- No se modifican V1..V8 ni se duplican totales en otra tabla.

## API y Swagger

| Método | Ruta |
| --- | --- |
| GET / PUT | /api/v1/trips/{tripId}/budget |
| GET / PUT | /api/v1/trips/{tripId}/legs/{legId}/budget |
| GET | /api/v1/trips/{tripId}/budget/legs |
| POST / GET | /api/v1/trips/{tripId}/expenses |
| GET / PUT / DELETE | /api/v1/trips/{tripId}/expenses/{id} |

Se conservan los tres endpoints base y se agregan consultas/gastos para administrar
el registro manual y paginar listas. PUT de gasto reemplaza todos los campos.
POST devuelve 201 y Location; DELETE 204. Mutaciones requieren version, en query
para DELETE. page 0..1000000; size 1..100, default 20. Gastos ordenados por creación
descendente y UUID; tramos de presupuesto por UUID.

Errores ApiError: 400 entrada/referencia de tramo inválida, 404 viaje/gasto ausente,
409 versión o integridad/clave duplicada. Swagger describe entradas y respuestas.
Se rechazan campos extra, claves JSON duplicadas, conversiones sin nota, números
como texto, precisión excesiva y exponentes fuera del rango permitido.

## Verificación funcional

Compilación: Maven package con -Dmaven.test.skip=true, sin compilar ni ejecutar
fuentes de prueba. Docker ejecutó el JAR con Java 17 y PostgreSQL 17.6.
Flyway aplicó V1..V9 y Hibernate validó el esquema.
Validación realizada el 14/11/2026. BUILD SUCCESS a las 19:51:13 -03:00 (11,220 s).

- **65 llamadas HTTP con curl y 87 verificaciones correctas**.
- **7 comprobaciones directas de constraints PostgreSQL**, todas rechazaron los
  datos inválidos y la transacción final se revirtió.
- Se verificaron real vs estimado, total compartido sin reparto, EUR original,
  USD parcial, conversión explícita, límites por tramo/categoría, saldo negativo,
  actualización por PATCH de Trip, paginación, Swagger, JSON estricto, propiedad
  entre viajes, borrado restrictivo y eliminación/limpieza explícita.
- Dos procesos curl simultáneos con la misma revisión produjeron 201 y 409:
  una sola escritura válida. El gasto temporal se eliminó después.
- Confirmar BOOKED y cancelar el tramo no duplicaron ni eliminaron gastos.
- PostgreSQL confirmó tres gastos, tres límites, revisión 16, importe real
  original EUR 150 y forecast USD 575; con límite USD 1000, saldo USD 425.
- No se ejecutaron JUnit, Testcontainers ni las pruebas de arquitectura.
  La matriz de módulos se revisó en código; no se afirma que su suite haya pasado.
- No hay una medición nueva de cobertura ni ejecución de clean verify.
  Los gates históricos se conservan sin deshabilitarlos.
- JAR comprobado: incluye BudgetController y V9; no incluye clases de prueba.
  git diff --check sin errores de whitespace.

Reportes locales (ignorados por Git): .tools/card-7-build.log,
.tools/card-7-http.log, .tools/card-7-result.json, .tools/card-7-sql.log.
Cada petición/respuesta queda en la carpeta runDir indicada en el JSON de resultado.
No se consultaron APIs externas ni se modificaron bases de producción.
Al finalizar se retiraron exclusivamente los contenedores y la red temporales
flight-trip-card7; la evidencia permanece en los archivos locales indicados.

Reproducir en PowerShell desde la raíz:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress '-Dmaven.test.skip=true' package
docker compose -p flight-trip-card7 -f scripts/validation/card-7.compose.yml up -d
curl.exe -fsS --retry 20 --retry-connrefused --retry-delay 1 http://127.0.0.1:18087/actuator/health
& ./scripts/validation/card-7.ps1
$evidence = Get-Content -Raw .tools/card-7-result.json | ConvertFrom-Json
Get-Content -Raw scripts/validation/card-7-constraints.sql |
  docker compose -p flight-trip-card7 -f scripts/validation/card-7.compose.yml exec -T postgres psql -U card7 -d card7 -v ON_ERROR_STOP=1 -v "tripId=$($evidence.tripId)" -v "expenseId=$($evidence.trainExpenseId)"
docker compose -p flight-trip-card7 -f scripts/validation/card-7.compose.yml down
```

La base de validación es temporal (tmpfs) y usa credenciales públicas exclusivas
de ese entorno. Sólo se publica la API en 127.0.0.1:18087; PostgreSQL no se expone.
No reutilizar ese Compose como entorno persistente.

## Riesgos y reevaluación

- Registro manual: el usuario decide qué gasto contabilizar y evita duplicar un
  ticket al cargar también sus segmentos. La clave explícita ayuda, no identifica
  semánticamente reservas ni valida comprobantes.
- Cotizaciones, cobros parciales, reembolsos y reparto entre varios tramos no se
  automatizan. Una corrección del neto real es explícita; no hay libro de pagos
  ni historial contable inmutable.
- Confirmación/cierre de reserva no permite inferir dinero. Incorporar en el futuro
  una acción explícita de selección/importación requerirá identidad y alcance del
  costo, mediante contratos/workflows, antes de sumar automáticamente.
- Conversión USD proporcionada manualmente, con nota; no verificada con un proveedor.
- MVP local sin autenticación: faltan permisos por propietario antes de exponerlo.
- Los límites por tramo y categorías pueden exceder el total; se muestran como
  advertencia, no como una partición matemática del presupuesto.
- Agregación SQL evita memoria proporcional a gastos, pero el costo de recorrer
  importes crece con el registro. Medir antes de agregar caché/proyecciones.
- Se conserva el bloqueo de viaje y su límite actual de 500 tramos; no se agregan
  dependencias Maven, colas ni servicios. No hay consumo de cuota externa.
- Pruebas funcionales no equivalen a cobertura exhaustiva: se respetó la petición
  de no dedicar trabajo a tests unitarios.

## Entrega

Rama: feature/trip-budget.
Commit sugerido: feat(budgets): add transport budgets and explicit expense accounting.
Sin commit ni push.
Próxima card: 8 — tracking, mediante proveedores independientes y distinguiendo
posición real de estimada.
