# Docker Build and Push Script for Job Portal Backend (Windows PowerShell)
# Usage: .\docker-build-push.ps1 -DockerUsername "your-dockerhub-username" -Version "1.0.0"

param(
    [Parameter(Mandatory=$false)]
    [string]$DockerUsername = "yourusername",
    
    [Parameter(Mandatory=$false)]
    [string]$Version = "1.0.0"
)

$ImageName = "job-portal-backend"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Docker Build and Push Script" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Docker Hub Username: $DockerUsername" -ForegroundColor White
Write-Host "Image Name: $ImageName" -ForegroundColor White
Write-Host "Version: $Version" -ForegroundColor White
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Build the Docker image
Write-Host "Step 1: Building Docker image..." -ForegroundColor Yellow
docker build -t "${ImageName}:${Version}" .
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Error: Docker build failed" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Build successful: ${ImageName}:${Version}" -ForegroundColor Green
Write-Host ""

# Step 2: Tag as latest
Write-Host "Step 2: Tagging as latest..." -ForegroundColor Yellow
docker tag "${ImageName}:${Version}" "${ImageName}:latest"
Write-Host "✅ Tagged as latest" -ForegroundColor Green
Write-Host ""

# Step 3: Tag for Docker Hub
Write-Host "Step 3: Tagging for Docker Hub..." -ForegroundColor Yellow
docker tag "${ImageName}:${Version}" "${DockerUsername}/${ImageName}:${Version}"
docker tag "${ImageName}:latest" "${DockerUsername}/${ImageName}:latest"
Write-Host "✅ Tagged for Docker Hub" -ForegroundColor Green
Write-Host ""

# Step 4: Login to Docker Hub
Write-Host "Step 4: Logging in to Docker Hub..." -ForegroundColor Yellow
docker login
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Error: Docker login failed" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Login successful" -ForegroundColor Green
Write-Host ""

# Step 5: Push to Docker Hub
Write-Host "Step 5: Pushing to Docker Hub..." -ForegroundColor Yellow
docker push "${DockerUsername}/${ImageName}:${Version}"
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Error: Push failed for version ${Version}" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Pushed ${DockerUsername}/${ImageName}:${Version}" -ForegroundColor Green

docker push "${DockerUsername}/${ImageName}:latest"
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Error: Push failed for latest" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Pushed ${DockerUsername}/${ImageName}:latest" -ForegroundColor Green
Write-Host ""

# Summary
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "✅ SUCCESS!" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Images pushed to Docker Hub:" -ForegroundColor White
Write-Host "  - ${DockerUsername}/${ImageName}:${Version}" -ForegroundColor White
Write-Host "  - ${DockerUsername}/${ImageName}:latest" -ForegroundColor White
Write-Host ""
Write-Host "To pull and run:" -ForegroundColor Yellow
Write-Host "  docker pull ${DockerUsername}/${ImageName}:latest" -ForegroundColor White
Write-Host "  docker run -d -p 8080:8080 ${DockerUsername}/${ImageName}:latest" -ForegroundColor White
Write-Host "==========================================" -ForegroundColor Cyan
