# llama.cpp Auto Setup Script for Windows
# Downloads and extracts llama-server.exe

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "llama.cpp Auto Setup Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# GitHub API URL for latest release
$apiUrl = "https://api.github.com/repos/ggerganov/llama.cpp/releases/latest"

Write-Host "Fetching latest release info..." -ForegroundColor Yellow

try {
    # Get latest release info
    $release = Invoke-RestMethod -Uri $apiUrl -Headers @{ "User-Agent" = "PowerShell" }
    $version = $release.tag_name

    Write-Host "Latest version: $version" -ForegroundColor Green
    Write-Host ""

    # Find Windows CUDA build (or fallback to CPU)
    $cudaAsset = $release.assets | Where-Object { $_.name -like "*win-cuda*" -and $_.name -like "*.zip" } | Select-Object -First 1
    $cpuAsset = $release.assets | Where-Object { $_.name -like "*win-*" -and $_.name -like "*.zip" -and $_.name -notlike "*cuda*" } | Select-Object -First 1

    $asset = $null
    $buildType = ""

    if ($cudaAsset) {
        $asset = $cudaAsset
        $buildType = "CUDA (GPU-accelerated)"
    } elseif ($cpuAsset) {
        $asset = $cpuAsset
        $buildType = "CPU-only"
    } else {
        Write-Host "[ERROR] Could not find suitable Windows build" -ForegroundColor Red
        Write-Host "Please download manually from:" -ForegroundColor Yellow
        Write-Host "https://github.com/ggerganov/llama.cpp/releases" -ForegroundColor Cyan
        pause
        exit 1
    }

    Write-Host "Build type: $buildType" -ForegroundColor Green
    Write-Host "File: $($asset.name)" -ForegroundColor Green
    Write-Host "Size: $([math]::Round($asset.size / 1MB, 2)) MB" -ForegroundColor Green
    Write-Host ""

    $downloadUrl = $asset.browser_download_url
    $zipFile = "llama-cpp.zip"
    $extractPath = "llama-cpp"

    # Download
    Write-Host "Downloading..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri $downloadUrl -OutFile $zipFile -UseBasicParsing
    Write-Host "Download complete!" -ForegroundColor Green
    Write-Host ""

    # Extract
    Write-Host "Extracting..." -ForegroundColor Yellow
    if (Test-Path $extractPath) {
        Remove-Item $extractPath -Recurse -Force
    }
    Expand-Archive -Path $zipFile -DestinationPath $extractPath -Force
    Write-Host "Extraction complete!" -ForegroundColor Green
    Write-Host ""

    # Find llama-server.exe
    Write-Host "Locating llama-server.exe..." -ForegroundColor Yellow
    $llamaServer = Get-ChildItem -Path $extractPath -Recurse -Filter "llama-server.exe" | Select-Object -First 1

    if ($llamaServer) {
        # Copy to current directory
        Copy-Item -Path $llamaServer.FullName -Destination "llama-server.exe" -Force
        Write-Host "llama-server.exe copied to current directory!" -ForegroundColor Green
        Write-Host ""

        # Cleanup
        Write-Host "Cleaning up..." -ForegroundColor Yellow
        Remove-Item $zipFile -Force
        Remove-Item $extractPath -Recurse -Force
        Write-Host "Cleanup complete!" -ForegroundColor Green
        Write-Host ""

        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host "Setup Complete!" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "You can now run: start-llama-server.bat" -ForegroundColor Yellow
        Write-Host ""

    } else {
        Write-Host "[ERROR] llama-server.exe not found in the archive" -ForegroundColor Red
        Write-Host "Please check the extracted files manually" -ForegroundColor Yellow
        pause
        exit 1
    }

} catch {
    Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please download manually from:" -ForegroundColor Yellow
    Write-Host "https://github.com/ggerganov/llama.cpp/releases" -ForegroundColor Cyan
    pause
    exit 1
}

pause
