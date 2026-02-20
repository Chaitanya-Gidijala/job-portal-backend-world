# Job Portal Backend - Docker Setup

This document provides instructions for building and deploying the Job Portal Backend using Docker.

## Prerequisites

- Docker installed on your system
- Docker Hub account (for pushing images)
- Git (optional, for version control)

## Quick Start with Docker Compose

The easiest way to run the application with MySQL:

```bash
# Start both MySQL and the application
docker-compose up -d

# View logs
docker-compose logs -f app

# Stop all services
docker-compose down

# Stop and remove volumes (WARNING: This will delete database data)
docker-compose down -v
```

## Building the Docker Image

### 1. Build the image locally

```bash
# Navigate to the project directory
cd e:\myproject\findsharp\Job-Portal-Backend-master

# Build the Docker image
docker build -t chaitanyagidijala123/job-portal-backend:1.0.0 .
```

### 2. Run the container locally

```bash
# Run with external MySQL (ensure MySQL is running on localhost:3306)
docker run -d \
  --name job-portal-backend \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/job_portal?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC \
  -e SPRING_DATASOURCE_USERNAME=root \
  -e SPRING_DATASOURCE_PASSWORD=root \
  chaitanyagidijala123/job-portal-backend:1.0.0

# View logs
docker logs -f job-portal-backend

# Stop the container
docker stop job-portal-backend

# Remove the container
docker rm job-portal-backend
```

### 2. Run the container locally (PowerShell)

```powershell
docker run -d --name job-portal-backend -p 8080:8080 -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/job_portal?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC -e SPRING_DATASOURCE_USERNAME=root -e SPRING_DATASOURCE_PASSWORD=root chaitanyagidijala123/job-portal-backend:1.0.0
```

## Pushing to Docker Hub

### 1. Login to Docker Hub

```bash
docker login
# Enter your Docker Hub username and password
```

### 2. Tag the image

```bash
docker tag chaitanyagidijala123/job-portal-backend:1.0.0 chaitanyagidijala123/job-portal-backend:latest
```

### 3. Push to Docker Hub

```bash
# Push the latest tag
docker push chaitanyagidijala123/job-portal-backend:latest

# Push the versioned tag
docker push chaitanyagidijala123/job-portal-backend:1.0.0
```

### 4. Pull and run from Docker Hub (PowerShell)

```powershell
# Pull the image
docker pull chaitanyagidijala123/job-portal-backend:1.0.0

# Run the pulled image
docker run -d --name job-portal-backend -p 8080:8080 -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/job_portal?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC -e SPRING_DATASOURCE_USERNAME=root -e SPRING_DATASOURCE_PASSWORD=root chaitanyagidijala123/job-portal-backend:1.0.0
```

## Complete Build and Push Script

Here's a complete script to build and push to Docker Hub:

```bash
# Set your Docker Hub username
DOCKER_USERNAME="yourusername"
IMAGE_NAME="job-portal-backend"
VERSION="1.0.0"

# Build the image
docker build -t ${IMAGE_NAME}:${VERSION} .
docker build -t ${IMAGE_NAME}:latest .

# Tag for Docker Hub
docker tag ${IMAGE_NAME}:${VERSION} ${DOCKER_USERNAME}/${IMAGE_NAME}:${VERSION}
docker tag ${IMAGE_NAME}:latest ${DOCKER_USERNAME}/${IMAGE_NAME}:latest

# Login to Docker Hub
docker login

# Push to Docker Hub
docker push ${DOCKER_USERNAME}/${IMAGE_NAME}:${VERSION}
docker push ${DOCKER_USERNAME}/${IMAGE_NAME}:latest

echo "Successfully pushed ${DOCKER_USERNAME}/${IMAGE_NAME}:${VERSION} and latest to Docker Hub"
```

## Environment Variables

You can customize the application behavior using environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_DATASOURCE_URL` | MySQL connection URL | `jdbc:mysql://localhost:3306/job_portal` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `root` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `root` |
| `APP_JWT_SECRET` | JWT secret key | (from application.properties) |
| `APP_JWT_EXPIRATION_MILLISECONDS` | JWT expiration time | `604800000` (7 days) |
| `APP_CORS_ALLOWED_ORIGINS` | Allowed CORS origins | `http://localhost:5173,http://localhost:3000` |
| `SERVER_PORT` | Application port | `8080` |

## Health Check

The application includes a health check endpoint:

```bash
# Check application health
curl http://localhost:8080/actuator/health
```

## Troubleshooting

### Container won't start

```bash
# Check logs
docker logs job-portal-backend

# Check if MySQL is accessible
docker exec -it job-portal-backend ping mysql
```

### Database connection issues

```bash
# Ensure MySQL is running
docker ps | grep mysql

# Check MySQL logs
docker logs job-portal-mysql
```

### Rebuild without cache

```bash
docker build --no-cache -t job-portal-backend:latest .
```

## Production Deployment

For production, consider:

1. **Use secrets management** instead of hardcoded passwords
2. **Use environment-specific configurations**
3. **Enable SSL/TLS** for database connections
4. **Set up proper logging** and monitoring
5. **Use orchestration tools** like Kubernetes or Docker Swarm
6. **Implement CI/CD pipelines** for automated builds and deployments

## Docker Image Details

- **Base Image**: Eclipse Temurin 21 JRE Alpine (minimal size)
- **Build Tool**: Maven 3.9.6
- **Multi-stage Build**: Yes (reduces final image size)
- **Security**: Runs as non-root user
- **Health Check**: Enabled via Spring Boot Actuator
