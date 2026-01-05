Building the Docker image

```bash
docker build -t math-assistant:latest .
```

Running locally (pass envs or use secret manager later)

```bash
docker run -p 8080:8080 --env-file .env --name math-assistant math-assistant:latest
```

Notes
- This project uses Gradle wrapper (`gradlew`), the `Dockerfile` runs `./gradlew bootJar` in the build stage.
- For EC2: push the image to ECR and run via ECS, or install Docker on the EC2 instance and run the container directly.
- For secrets: keep using AWS Secrets Manager (inject at runtime via user-data, SSM, or ECS task definitions).