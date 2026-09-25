# Launch RideFlow backend using environment variables from .env
$repoRoot = Resolve-Path "$PSScriptRoot\.."

# Read .env if present and export variables for PowerShell child processes
if (Test-Path "$repoRoot\.env") {
    Get-Content "$repoRoot\.env" | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $key = $parts[0].Trim()
            $val = $parts[1].Trim()
            [Environment]::SetEnvironmentVariable($key, $val, "Process")
        }
    }
}

Set-Location -Path "$repoRoot\backend"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
