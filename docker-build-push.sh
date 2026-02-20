#!/bin/bash

# Docker Build and Push Script for Job Portal Backend
# Usage: ./docker-build-push.sh [your-dockerhub-username] [version]

# Configuration
DOCKER_USERNAME="${1:-yourusername}"
IMAGE_NAME="job-portal-backend"
VERSION="${2:-1.0.0}"

echo "=========================================="
echo "Docker Build and Push Script"
echo "=========================================="
echo "Docker Hub Username: $DOCKER_USERNAME"
echo "Image Name: $IMAGE_NAME"
echo "Version: $VERSION"
echo "=========================================="

# Step 1: Build the Docker image
echo ""
echo "Step 1: Building Docker image..."
docker build -t ${IMAGE_NAME}:${VERSION} .
if [ $? -ne 0 ]; then
    echo "❌ Error: Docker build failed"
    exit 1
fi
echo "✅ Build successful: ${IMAGE_NAME}:${VERSION}"

# Step 2: Tag as latest
echo ""
echo "Step 2: Tagging as latest..."
docker tag ${IMAGE_NAME}:${VERSION} ${IMAGE_NAME}:latest
echo "✅ Tagged as latest"

# Step 3: Tag for Docker Hub
echo ""
echo "Step 3: Tagging for Docker Hub..."
docker tag ${IMAGE_NAME}:${VERSION} ${DOCKER_USERNAME}/${IMAGE_NAME}:${VERSION}
docker tag ${IMAGE_NAME}:latest ${DOCKER_USERNAME}/${IMAGE_NAME}:latest
echo "✅ Tagged for Docker Hub"

# Step 4: Login to Docker Hub
echo ""
echo "Step 4: Logging in to Docker Hub..."
docker login
if [ $? -ne 0 ]; then
    echo "❌ Error: Docker login failed"
    exit 1
fi
echo "✅ Login successful"

# Step 5: Push to Docker Hub
echo ""
echo "Step 5: Pushing to Docker Hub..."
docker push ${DOCKER_USERNAME}/${IMAGE_NAME}:${VERSION}
if [ $? -ne 0 ]; then
    echo "❌ Error: Push failed for version ${VERSION}"
    exit 1
fi
echo "✅ Pushed ${DOCKER_USERNAME}/${IMAGE_NAME}:${VERSION}"

docker push ${DOCKER_USERNAME}/${IMAGE_NAME}:latest
if [ $? -ne 0 ]; then
    echo "❌ Error: Push failed for latest"
    exit 1
fi
echo "✅ Pushed ${DOCKER_USERNAME}/${IMAGE_NAME}:latest"

# Summary
echo ""
echo "=========================================="
echo "✅ SUCCESS!"
echo "=========================================="
echo "Images pushed to Docker Hub:"
echo "  - ${DOCKER_USERNAME}/${IMAGE_NAME}:${VERSION}"
echo "  - ${DOCKER_USERNAME}/${IMAGE_NAME}:latest"
echo ""
echo "To pull and run:"
echo "  docker pull ${DOCKER_USERNAME}/${IMAGE_NAME}:latest"
echo "  docker run -d -p 8080:8080 ${DOCKER_USERNAME}/${IMAGE_NAME}:latest"
echo "=========================================="
