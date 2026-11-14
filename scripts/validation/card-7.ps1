param([string]$BaseUrl = 'http://127.0.0.1:18087')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$runDir = Join-Path $projectRoot ('.tools/card-7-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $runDir -Force | Out-Null
$script:requests = 0
$script:checks = 0
$utf8 = [System.Text.UTF8Encoding]::new($false)
function Check($condition, [string]$message) {
    if (-not $condition) { throw "Verification failed: $message" }
    $script:checks++
}
function Request([string]$method, [string]$path, $body, [int]$expected) {
    $script:requests++
    $responseFile = Join-Path $runDir ("response-" + $script:requests + '.json')
    $arguments = @('-sS', '--max-time', '20', '-X', $method, '-o', $responseFile, '-w', '%{http_code}', ($BaseUrl + $path))
    if ($null -ne $body) {
        $bodyFile = Join-Path $runDir ("request-" + $script:requests + '.json')
        $payload = if ($body -is [string]) { $body } else { $body | ConvertTo-Json -Depth 15 -Compress }
        [IO.File]::WriteAllText($bodyFile, $payload, $utf8)
        $arguments += @('-H', 'Content-Type: application/json', '--data-binary', ('@' + $bodyFile))
    }
    $status = & curl.exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "curl failed for $method $path" }
    $raw = [IO.File]::ReadAllText($responseFile)
    Check ([int]$status -eq $expected) "$method $path expected $expected, received $status : $raw"
    if (-not [string]::IsNullOrWhiteSpace($raw)) { return $raw | ConvertFrom-Json }
}
function Sql([string]$sql) {
    $output = $sql | & docker compose -p flight-trip-card7 -f (Join-Path $PSScriptRoot 'card-7.compose.yml') exec -T postgres psql -U card7 -d card7 -v ON_ERROR_STOP=1 -At
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL verification failed" }
    return ($output -join "`n")
}
function NewTrip {
    Request POST '/api/v1/trips' @{ name='Card 7 curl validation'; startDate='2027-02-01'; endDate='2027-03-01' } 201
}
function NewLeg([string]$tripId) {
    Request POST "/api/v1/trips/$tripId/legs" @{
        version=0;origin=@{kind='AIRPORT';id='00000000-0000-0000-0000-000000000001'}
        destination=@{kind='AIRPORT';id='00000000-0000-0000-0000-000000000002'}
        transportType='TRAIN';departureDate='2027-02-10'
    } 201
}
Sql @'
INSERT INTO catalog.airports(id,iata_code,name,city,country,timezone,active)
VALUES ('00000000-0000-0000-0000-000000000001','AAA','Alpha','Alpha','AR','UTC',true),
('00000000-0000-0000-0000-000000000002','AAB','Beta','Beta','AR','UTC',true)
ON CONFLICT (id) DO NOTHING;
'@ | Out-Null
$trip = NewTrip
$tripId = $trip.id
$leg = NewLeg $tripId
$legId = $leg.id
$path = "/api/v1/trips/$tripId"
$summary = Request PUT "$path/budget" @{version=1;totalBudgetUsd=1000;categories=@{TRAINS=600;FLIGHTS=300}} 200
Check ($summary.total.remainingUsd -eq 1000) 'Empty ledger has full assigned cap'
Request PUT "$path/legs/$legId/budget" @{version=2;amountUsd=500} 200 | Out-Null
$trainBody = @{version=3;legId=$legId;category='TRAINS';description='Train ticket';referenceKey='train-ticket';estimated=@{amount=200;currency='USD'}}
$train = Request POST "$path/expenses" $trainBody 201
$summary = Request GET "$path/budget" $null 200
Check ($summary.total.forecast.knownUsd -eq 200 -and $summary.total.actual.knownUsd -eq 0) 'Estimated only'
$trainBody.version=4
$trainBody.actual=@{amount=150;currency='USD'}
Request PUT "$path/expenses/$($train.id)" $trainBody 200 | Out-Null
$summary = Request GET "$path/budget" $null 200
Check ($summary.total.estimated.knownUsd -eq 200 -and $summary.total.actual.knownUsd -eq 150 -and $summary.total.forecast.knownUsd -eq 150) 'Actual replaces estimate, never adds to it'
$sharedBody=@{version=5;category='FLIGHTS';description='Shared round-trip total';referenceKey='shared-ticket';actual=@{amount=300;currency='USD'}}
$shared=Request POST "$path/expenses" $sharedBody 201
$carBody=@{version=6;category='CAR';description='Car transport estimate';referenceKey='car-cost';estimated=@{amount=100;currency='EUR'}}
$car=Request POST "$path/expenses" $carBody 201
$summary=Request GET "$path/budget" $null 200
Check ($null -eq $summary.total.remainingUsd -and $summary.total.forecast.missingUsdCount -eq 1 -and $summary.total.forecast.knownUsd -eq 450) 'Unknown FX keeps remaining null and subtotal explicit'
Check ($summary.unallocated.actual.knownUsd -eq 300) 'Shared ticket counted once at trip level'
$carBody.version=7
$carBody.estimated=@{amount=100;currency='EUR';amountUsd=110;conversionNote='Manually confirmed conversion'}
Request PUT "$path/expenses/$($car.id)" $carBody 200 | Out-Null
$summary=Request GET "$path/budget" $null 200
Check ($summary.total.forecast.knownUsd -eq 560 -and $summary.total.remainingUsd -eq 440) 'Known conversion completes forecast'
$trainBody.version=8
$trainBody.actual=@{amount=150;currency='EUR'}
Request PUT "$path/expenses/$($train.id)" $trainBody 200 | Out-Null
$summary=Request GET "$path/budget" $null 200
Check ($summary.total.forecast.knownUsd -eq 410 -and $null -eq $summary.total.remainingUsd) 'Actual with unknown FX must not fall back to known estimated USD'
$trainBody.version=9
$trainBody.actual=@{amount=150;currency='EUR';amountUsd=165;conversionNote='Card statement'}
Request PUT "$path/expenses/$($train.id)" $trainBody 200 | Out-Null
$summary=Request GET "$path/budget" $null 200
Check ($summary.total.forecast.knownUsd -eq 575 -and $summary.total.remainingUsd -eq 425 -and $summary.total.actual.knownUsd -eq 465) 'Final totals'
Check ($summary.total.forecast.originalAmounts.EUR -eq 250 -and $summary.total.forecast.originalAmounts.USD -eq 300) 'Original currencies remain separate'
$legBudget=Request GET "$path/legs/$legId/budget" $null 200
Check ($legBudget.balance.forecast.knownUsd -eq 165 -and $legBudget.balance.remainingUsd -eq 335) 'Leg excludes unallocated shared ticket'
$sharedBody.version=10
$duplicate=Request POST "$path/expenses" $sharedBody 409
Check ($duplicate.code -eq 'BUDGET_DUPLICATE_REFERENCE') 'Explicit accounting key prevents duplicate'
$other=NewTrip
$otherLeg=NewLeg $other.id
$bad=@{version=10;legId=$otherLeg.id;category='TRAINS';description='Wrong trip';actual=@{amount=1;currency='USD'}}
Request POST "$path/expenses" $bad 400 | Out-Null
$bad.legId=$legId;$bad.category='BUSES'
Request POST "$path/expenses" $bad 400 | Out-Null
$trainBody.version=9
Request PUT "$path/expenses/$($train.id)" $trainBody 409 | Out-Null
Request GET "/api/v1/trips/$($other.id)/expenses/$($train.id)" $null 404 | Out-Null
Request PUT "$path/budget" @{version=10;totalBudgetUsd=1000;categories=@{HOTELS=1}} 400 | Out-Null
Request PUT "$path/budget" @{version=10;totalBudgetUsd=-1;categories=@{}} 400 | Out-Null
Request PUT "$path/budget" @{version=10;totalBudgetUsd=0.001;categories=@{}} 400 | Out-Null
Request GET "$path/expenses?size=101" $null 400 | Out-Null
Request GET "$path/budget/legs?page=-1" $null 400 | Out-Null
foreach ($body in @('{}','null','[]','{"version":10,"version":10}','{"version":"10"}',
    '{"version":10,"category":"CAR","description":"Bad","actual":{"amount":"3","currency":"USD"}}',
    '{"version":10,"category":"CAR","description":"Bad","actual":{"amount":3,"currency":"USD","amountUsd":4}}',
    '{"version":10,"category":"CAR","description":"Bad","actual":{"amount":3,"currency":"EUR","amountUsd":4}}',
    '{"version":10,"category":"CAR","description":"Bad","actual":{"amount":1e1000000,"currency":"USD"}}',
    '{"version":10,"category":"CAR","description":"Bad","actual":{"amount":1.00001,"currency":"USD"}}',
    '{"version":10,"category":"CAR","description":"Bad","extra":true,"actual":{"amount":1,"currency":"USD"}}')) {
    Request POST "$path/expenses" $body 400 | Out-Null
}
$summary=Request GET "$path/budget" $null 200
Check ($summary.tripVersion -eq 10 -and $summary.total.remainingUsd -eq 425) 'Rejected mutations leave version and totals unchanged'
$page=Request GET "$path/expenses?page=1&size=2" $null 200
Check ($page.totalElements -eq 3 -and $page.items.Count -eq 1) 'Expense pagination'
$legs=Request GET "$path/budget/legs?size=1" $null 200
Check ($legs.totalElements -eq 1) 'Leg pagination'
Request DELETE "$path/legs/${legId}?version=10" $null 409 | Out-Null
# Two actual HTTP writes in separate curl processes with the same parent revision.
$processes=@()
foreach ($index in 1..2) {
    $inputFile=Join-Path $runDir "race-$index-request.json"
    [IO.File]::WriteAllText($inputFile,(@{version=10;category='CAR';description="Concurrent $index";referenceKey="race-$index";actual=@{amount=1;currency='USD'}} | ConvertTo-Json -Depth 6 -Compress),$utf8)
    $outputFile=Join-Path $runDir "race-$index-response.json"
    $statusFile=Join-Path $runDir "race-$index-status.txt"
    $args=@('-sS','--max-time','20','-X','POST','-H','"Content-Type: application/json"','--data-binary',('@"'+$inputFile+'"'),'-o',('"'+$outputFile+'"'),'-w','"%{http_code}"',('"'+$BaseUrl+$path+'/expenses"'))
    $processes+=Start-Process -FilePath curl.exe -ArgumentList $args -PassThru -WindowStyle Hidden -RedirectStandardOutput $statusFile -RedirectStandardError (Join-Path $runDir "race-$index-error.txt")
}
$processes | ForEach-Object { $_.WaitForExit(); Check ($_.ExitCode -eq 0) 'Concurrent curl completed' }
$statuses=1..2 | ForEach-Object {[int]([IO.File]::ReadAllText((Join-Path $runDir "race-$_-status.txt")))}
Check ((($statuses | Sort-Object) -join ',') -eq '201,409') 'One concurrent revision winner'
$winnerIndex=if($statuses[0] -eq 201){1}else{2}
$winner=Get-Content -Raw (Join-Path $runDir "race-$winnerIndex-response.json") | ConvertFrom-Json
Request DELETE "$path/expenses/$($winner.id)?version=11" $null 204 | Out-Null
Request PATCH "$path/legs/$legId" @{version=12;status='BOOKED'} 200 | Out-Null
$summary=Request GET "$path/budget" $null 200
Check ($summary.total.forecast.knownUsd -eq 575) 'Reservation confirmation does not duplicate explicit costs'
Request PATCH "$path/legs/$legId" @{version=13;status='CANCELLED'} 200 | Out-Null
$summary=Request GET "$path/budget" $null 200
Check ($summary.total.forecast.knownUsd -eq 575) 'Cancellation does not invent refund'
Request PATCH $path @{version=14;totalBudgetUsd=500} 200 | Out-Null
$summary=Request GET "$path/budget" $null 200
Check ($summary.total.remainingUsd -eq -75 -and $summary.warnings -contains 'OVER_BUDGET') 'Existing Trip budget remains single source of truth'
Check ($summary.warnings -contains 'CATEGORY_LIMITS_EXCEED_TOTAL') 'Independent allocations exceeding cap are visible'
Request PUT "$path/budget" @{version=15;totalBudgetUsd=1000;categories=@{TRAINS=600;FLIGHTS=300}} 200 | Out-Null
# Clear/delete path on the secondary trip.
$otherPath="/api/v1/trips/$($other.id)"
Request PUT "$otherPath/legs/$($otherLeg.id)/budget" @{version=1;amountUsd=50} 200 | Out-Null
Request DELETE "$otherPath/legs/$($otherLeg.id)?version=2" $null 409 | Out-Null
Request PUT "$otherPath/legs/$($otherLeg.id)/budget" @{version=2;amountUsd=$null} 200 | Out-Null
Request DELETE "$otherPath/legs/$($otherLeg.id)?version=3" $null 204 | Out-Null
$temporary=Request POST "$otherPath/expenses" @{version=4;category='CAR';description='Delete flow';actual=@{amount=5;currency='USD'}} 201
Request DELETE "${otherPath}?version=5" $null 409 | Out-Null
Request DELETE "$otherPath/expenses/$($temporary.id)?version=5" $null 204 | Out-Null
Request PUT "$otherPath/budget" @{version=6;totalBudgetUsd=$null;categories=@{}} 200 | Out-Null
Request DELETE "${otherPath}?version=7" $null 204 | Out-Null
$swagger=Request GET '/v3/api-docs' $null 200
Check ($null -ne $swagger.paths.'/api/v1/trips/{tripId}/budget') 'Swagger budget route'
Check ($null -ne $swagger.components.schemas.BudgetDto -and $null -ne $swagger.components.schemas.ExpenseRequest) 'Swagger public schemas'
$sqlEvidence=Sql "SELECT json_build_object('migrations',(SELECT count(*) FROM public.flyway_schema_history WHERE success),'expenses',(SELECT count(*) FROM budgets.transport_expenses WHERE trip_id='$tripId'),'version',(SELECT version FROM trips.trips WHERE id='$tripId'),'actual_original_eur',(SELECT actual_amount FROM budgets.transport_expenses WHERE id='$($train.id)'),'forecast_usd',(SELECT sum(CASE WHEN actual_amount IS NOT NULL THEN actual_amount_usd ELSE estimated_amount_usd END) FROM budgets.transport_expenses WHERE trip_id='$tripId'),'limits',(SELECT count(*) FROM budgets.budget_limits WHERE trip_id='$tripId'));"
$db=$sqlEvidence | ConvertFrom-Json
Check ($db.migrations -eq 9 -and $db.expenses -eq 3 -and $db.version -eq 16 -and $db.actual_original_eur -eq 150 -and $db.forecast_usd -eq 575 -and $db.limits -eq 3) 'Independent PostgreSQL verification'
[IO.File]::WriteAllText((Join-Path $runDir 'database.json'),$sqlEvidence,$utf8)
$report=@{requests=$script:requests+2;checks=$script:checks;tripId=$tripId;legId=$legId;trainExpenseId=$train.id;runDir=$runDir;database=$db}
$report | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $projectRoot '.tools/card-7-result.json')
$report | ConvertTo-Json -Depth 8
