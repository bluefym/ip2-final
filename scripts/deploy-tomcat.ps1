param(
    [string]$TomcatHome,
    [string]$AppName = "academiq",
    [string]$WarPath,
    [string]$MavenPath,
    [string]$EnvFile,
    [switch]$SkipBuild,
    [switch]$NoRestart,
    [switch]$NoEnvLoad,
    [switch]$OpenBrowser,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[ACADEMIQ DEPLOY] $Message" -ForegroundColor Cyan
}

function Resolve-TomcatHome {
    param([string]$ExplicitTomcatHome)

    if ($ExplicitTomcatHome -and (Test-Path $ExplicitTomcatHome)) {
        return (Resolve-Path $ExplicitTomcatHome).Path
    }

    if ($env:CATALINA_HOME -and (Test-Path $env:CATALINA_HOME)) {
        return (Resolve-Path $env:CATALINA_HOME).Path
    }

    if ($env:TOMCAT_HOME -and (Test-Path $env:TOMCAT_HOME)) {
        return (Resolve-Path $env:TOMCAT_HOME).Path
    }

    $candidates = @(
        "C:\Program Files\Apache Software Foundation\Tomcat 9.0",
        "C:\Program Files (x86)\Apache Software Foundation\Tomcat 9.0"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw "Tomcat 9 home not found. Pass -TomcatHome or set CATALINA_HOME."
}

function Resolve-Maven {
    param([string]$ExplicitMavenPath, [string]$RepoRoot)

    if ($ExplicitMavenPath -and (Test-Path $ExplicitMavenPath)) {
        return (Resolve-Path $ExplicitMavenPath).Path
    }

    $localMaven = Join-Path $RepoRoot ".tools\apache-maven-3.9.9\bin\mvn.cmd"
    if (Test-Path $localMaven) {
        return (Resolve-Path $localMaven).Path
    }

    $globalMaven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($globalMaven) {
        return $globalMaven.Source
    }

    $globalMavenAlt = Get-Command mvn -ErrorAction SilentlyContinue
    if ($globalMavenAlt) {
        return $globalMavenAlt.Source
    }

    throw "Maven not found. Install Maven or pass -MavenPath."
}

function Load-EnvFile {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        Write-Step "No .env file found at $Path. Skipping env load."
        return @()
    }

    $loadedKeys = @()
    $lines = Get-Content -Path $Path

    foreach ($line in $lines) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }

        $idx = $trimmed.IndexOf("=")
        if ($idx -lt 1) {
            continue
        }

        $key = $trimmed.Substring(0, $idx).Trim()
        $value = $trimmed.Substring($idx + 1).Trim()

        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        if ($key) {
            Set-Item -Path ("Env:{0}" -f $key) -Value $value
            [Environment]::SetEnvironmentVariable($key, $value, "Process")
            $loadedKeys += $key
        }
    }

    return $loadedKeys
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendTomcatPath = Join-Path $repoRoot "backend-tomcat"
$resolvedEnvFile = if ($EnvFile) { $EnvFile } else { Join-Path $repoRoot ".env" }

if (-not (Test-Path $backendTomcatPath)) {
    throw "backend-tomcat folder not found at: $backendTomcatPath"
}

$resolvedTomcatHome = Resolve-TomcatHome -ExplicitTomcatHome $TomcatHome
$webappsPath = Join-Path $resolvedTomcatHome "webapps"
$shutdownBat = Join-Path $resolvedTomcatHome "bin\shutdown.bat"
$startupBat = Join-Path $resolvedTomcatHome "bin\startup.bat"

if (-not (Test-Path $webappsPath)) {
    throw "Tomcat webapps folder not found: $webappsPath"
}

if (-not $NoEnvLoad) {
    Write-Step "Loading environment variables from: $resolvedEnvFile"
    $keys = Load-EnvFile -Path $resolvedEnvFile
    if ($keys.Count -gt 0) {
        Write-Step ("Loaded env keys: {0}" -f ($keys -join ", "))
    }
}
else {
    Write-Step "Skipping .env loading as requested."
}

if (-not $SkipBuild) {
    $resolvedMaven = Resolve-Maven -ExplicitMavenPath $MavenPath -RepoRoot $repoRoot
    Write-Step "Building WAR using Maven..."

    if (-not $DryRun) {
        Push-Location $backendTomcatPath
        try {
            & $resolvedMaven -DskipTests clean package
        }
        finally {
            Pop-Location
        }
    }
    else {
        Write-Step "DRY RUN: Skipped Maven build command."
    }
}
else {
    Write-Step "Skipping build as requested."
}

$defaultWarPath = Join-Path $backendTomcatPath "target\academiq-tomcat9-1.0.0.war"
$resolvedWarPath = if ($WarPath) { $WarPath } else { $defaultWarPath }

if (-not (Test-Path $resolvedWarPath) -and -not $DryRun) {
    throw "WAR file not found: $resolvedWarPath"
}

$resolvedWarPath = if ($DryRun) { $resolvedWarPath } else { (Resolve-Path $resolvedWarPath).Path }
$targetWar = Join-Path $webappsPath ("{0}.war" -f $AppName)
$targetDir = Join-Path $webappsPath $AppName

Write-Step "Deploy target WAR: $targetWar"

if (-not $NoRestart) {
    if ((Test-Path $shutdownBat) -and (Test-Path $startupBat)) {
        Write-Step "Stopping Tomcat..."
        if (-not $DryRun) {
            & $shutdownBat | Out-Null
            Start-Sleep -Seconds 4
        }
        else {
            Write-Step "DRY RUN: Skipped Tomcat shutdown."
        }
    }
    else {
        Write-Step "Tomcat startup/shutdown scripts not found. Restart will be skipped."
        $NoRestart = $true
    }
}

if (Test-Path $targetDir) {
    Write-Step "Removing exploded app folder: $targetDir"
    if (-not $DryRun) {
        Remove-Item -Path $targetDir -Recurse -Force
    }
}

Write-Step "Copying WAR to Tomcat webapps..."
if (-not $DryRun) {
    Copy-Item -Path $resolvedWarPath -Destination $targetWar -Force
}

if (-not $NoRestart) {
    Write-Step "Starting Tomcat..."
    if (-not $DryRun) {
        & $startupBat | Out-Null
        Start-Sleep -Seconds 3
    }
}

$appUrl = "http://localhost:8080/{0}/" -f $AppName
Write-Step "Deployment complete: $appUrl"

if ($OpenBrowser -and -not $DryRun) {
    Start-Process $appUrl | Out-Null
}
