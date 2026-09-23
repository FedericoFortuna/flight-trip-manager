#Requires -Version 7.0
# Optional negative acceptance tests. Run from PowerShell with JDK 17 available.
# Uses an isolated, ignored fixture project; does not alter production sources or reports.
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$probeRoot = Join-Path $repositoryRoot ('.tools/quality-gates-' + [guid]::NewGuid().ToString('N'))
$mainDirectory = Join-Path $probeRoot 'src/main/java/probe'
$testDirectory = Join-Path $probeRoot 'src/test/java/probe'
New-Item -ItemType Directory -Path $mainDirectory, $testDirectory -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $repositoryRoot 'pom.xml') -Destination (Join-Path $probeRoot 'pom.xml')

$source = [System.Collections.Generic.List[string]]::new()
$source.Add('package probe;')
$source.Add('public class Probe {')
$source.Add('  public static void main(String[] args) {}')
$source.Add('  public int covered() { return 1; }')
foreach ($index in 1..50) {
    $source.Add("  public int uncovered$index() { return $index; }")
}
$source.Add('}')
$source | Set-Content -LiteralPath (Join-Path $mainDirectory 'Probe.java') -Encoding utf8
foreach ($testName in 'ProbeTest', 'ProbeIT') {
    @"
package probe;
public class $testName {
  @org.junit.jupiter.api.Test
  void generatesRealCoverageData() {
    org.junit.jupiter.api.Assertions.assertEquals(1, new Probe().covered());
  }
}
"@ | Set-Content -LiteralPath (Join-Path $testDirectory "$testName.java") -Encoding utf8
}

Push-Location $repositoryRoot
try {
    $wrapper = if ($IsWindows) { Join-Path $repositoryRoot 'mvnw.cmd' } else { Join-Path $repositoryRoot 'mvnw' }
    $probePom = Join-Path $probeRoot 'pom.xml'
    $lowCoverageLog = Join-Path $probeRoot 'low-coverage.log'
    & $wrapper --batch-mode --no-transfer-progress -f $probePom clean verify *> $lowCoverageLog
    $lowCoverageExit = $LASTEXITCODE
    $lowCoverageOutput = Get-Content -LiteralPath $lowCoverageLog -Raw
    if ($lowCoverageExit -eq 0 -or $lowCoverageOutput -notmatch 'lines covered ratio is .*expected minimum is 0\.80') {
        throw "Low coverage was not rejected by the expected rule. Inspect $lowCoverageLog"
    }

    $missingCoverageLog = Join-Path $probeRoot 'missing-coverage.log'
    & $wrapper --batch-mode --no-transfer-progress -f $probePom clean verify '-Djacoco.skip=true' *> $missingCoverageLog
    $missingCoverageExit = $LASTEXITCODE
    $missingCoverageOutput = Get-Content -LiteralPath $missingCoverageLog -Raw
    if ($missingCoverageExit -eq 0 -or $missingCoverageOutput -notmatch 'RequireFilesExist' -or
            $missingCoverageOutput -notmatch 'jacoco\.exec') {
        throw "Missing coverage was not rejected by Enforcer. Inspect $missingCoverageLog"
    }
    Write-Output 'PASS: coverage below 80% rejected; missing coverage data rejected.'
    Write-Output "Evidence: $probeRoot"
} finally {
    Pop-Location
}
