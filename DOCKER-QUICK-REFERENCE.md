# 🐳 Quick Docker Commands Reference

## Build the Image
```
docker build -t job-portal-backend:1.0.0 .
```

## Run with Docker Compose (Recommended)
```
# Start everything (app + MySQL)
docker-compose up -d

# View logs
docker-compose logs -f

# Stop everything
docker-compose down
```

## Push to Docker Hub

### Using PowerShell Script (Windows)
```
.\docker-build-push.ps1 -DockerUsername "your-username" -Version "1.0.0"
```

### Using Bash Script (Linux/Mac)
```
chmod +x docker-build-push.sh
./docker-build-push.sh your-username 1.0.0
```

### Manual Commands
```bash
# 1. Login
docker login

# 2. Tag the image
docker tag job-portal-backend:1.0.0 chaitanyagidijala123/job-portal-backend:1.0.0
docker tag job-portal-backend:1.0.0 chaitanyagidijala123/job-portal-backend:latest

# 3. Push to Docker Hub
docker push chaitanyagidijala123/job-portal-backend:1.0.0
docker push chaitanyagidijala123/job-portal-backend:latest
```

## Pull and Run from Docker Hub
```bash
docker pull chaitanyagidijala123/job-portal-backend:latest
docker run -d -p 8080:8080 -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/job_portal -e SPRING_DATASOURCE_USERNAME=root -e SPRING_DATASOURCE_PASSWORD=root chaitanyagidijala123/job-portal-backend:1.0.0
```

## Run the container locally (PowerShell)

```powershell
# Run with external MySQL (ensure MySQL is running on localhost:3306)
docker run -d --name job-portal-backend -p 8080:8080 -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/job_portal?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC -e SPRING_DATASOURCE_USERNAME=root -e SPRING_DATASOURCE_PASSWORD=root chaitanyagidijala123/job-portal-backend:1.0.0
```

## Useful Commands
```bash
# View running containers
docker ps

# View logs
docker logs -f job-portal-backend

# Stop container
docker stop job-portal-backend

# Remove container
docker rm job-portal-backend

# Remove image
docker rmi job-portal-backend:1.0.0

# Check health
curl http://localhost:8080/actuator/health
```

---

📖 **For detailed documentation, see [DOCKER.md](DOCKER.md)**
