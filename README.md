# Math-assistant

## Quick Start

Siga estes passos rápidos para compilar, executar e testar o backend localmente (Windows):

- Preparar banco de dados local (Docker):

```powershell
docker-compose up -d
```

- Build e executar a aplicação (Windows):

```powershell
.
gradlew.bat build
gradlew.bat bootRun
```

- Executar testes:

```powershell
gradlew.bat test
```

- Configurações e variáveis:

- As propriedades do Spring Boot ficam em `src/main/resources/application.properties`.
- Para configurar o MongoDB local, ajuste `spring.data.mongodb.*` ou use o `docker-compose.yml` já presente.

Local dev & secrets

- Copy `.env.example` to `.env` and fill the required values (keep `.env` gitignored).
- Two helper scripts were added to load `.env` into the process and run the app locally:
	- Windows PowerShell: `scripts/run-local.ps1`
	- Unix/macOS: `scripts/run-local.sh` (make executable)

Usage examples:

Windows PowerShell (run from repo root):
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-local.ps1
```

Unix/macOS:
```bash
chmod +x ./scripts/run-local.sh
./scripts/run-local.sh
```

- Alternative: use `dotenv-cli` to load `.env` when running Gradle: `dotenv -e .env -- gradlew bootRun`.
- Docker / compose: `docker-compose.yml` supports `env_file: .env` so containers read the same file.

EC2 / Production notes (overview):
- In production prefer AWS Secrets Manager (or cloud secret store). Two common approaches:
	1. Use Spring Cloud AWS starter that maps secrets to properties.
 2. Implement an `EnvironmentPostProcessor` that reads AWS Secrets Manager via AWS SDK and injects a `MapPropertySource` before Spring context refresh — this supports EC2 IAM roles and local testing via `AWS_PROFILE` or LocalStack.
- The project can be extended to fetch secrets at startup so `${VAR}` placeholders in `application.properties` are resolved from Secrets Manager.

Security notes:
- Never commit real secrets. Use `.env.example` to document required variables and store real values in Secret Manager, `.env` locally, or CI/CD secret stores.


Consulte a seção de Design abaixo para detalhes da arquitetura e endpoints.

# Documento de Design do Sistema de Assistente de Matemática with Java and ReactJS/React Native

## 1. Introdução
Este documento descreve a arquitetura e os requisitos do sistema de assistente de matemática. O backend é Java (Spring Boot). The web frontend will be a ReactJS app and the mobile frontend will use React Native. The integration between frontend and backend is performed via RESTful APIs.

## 2. Requisitos Funcionais
### 2.1 Registro e Autenticação de Usuários
- Permitir que os usuários se registrem e façam login na aplicação móvel.
- Autenticar usuários através de APIs RESTful utilizando tokens de acesso.

### 2.2 Consulta de Fórmulas
- Permitir que os usuários pesquisem e visualizem fórmulas matemáticas disponíveis.
- Consultar e recuperar dados das fórmulas disponíveis através de APIs RESTful.

### 2.3 Cálculo de Fórmulas
- Permitir que os usuários insiram valores para os parâmetros das fórmulas e obtenham os resultados calculados.
- Enviar valores dos parâmetros para o backend através de APIs RESTful para realizar os cálculos.
- Receber e exibir os resultados dos cálculos fornecidos pelo backend.

## 3. Requisitos Não Funcionais

### 3.1 Desempenho

- Garantir que as APIs RESTful do backend sejam otimizadas para oferecer uma resposta rápida, mesmo para cálculos complexos.
- Frontend: use ReactJS performance best practices (memoization, avoid unnecessary re-renders).

Mobile: React Native performance considerations (use native modules sparingly, optimize lists).

### 3.2 Segurança

- Implementar autenticação e autorização robustas nas APIs RESTful para proteger os dados do usuário.
- Utilizar HTTPS para comunicação segura entre o frontend e o backend.

### 3.3 Interface do Usuário

- Desenvolver uma interface do usuário intuitiva e atraente usando ReactJS for web (components, hooks) and React Native for mobile.

## 4. Arquitetura do Sistema

### 4.1 Frontend (ReactJS / React Native)

- Web: ReactJS — single-page app for browsers. Use `create-react-app`, Vite, or Next.js as preferred starter.
- Mobile: React Native — cross-platform mobile app compatible with Android and iOS.
- Use `fetch`/`axios` on the client to call the REST endpoints exposed by the backend.

### 4.2 Backend (Java)

- O backend será construído utilizando o framework Spring Boot para criar uma API RESTful.
- Implementar endpoints RESTful para autenticação de usuários, consulta de fórmulas e cálculo de fórmulas.
- Utilizar um banco de dados relacional (como MySQL ou PostgreSQL) para armazenar dados de usuários e fórmulas.

## 5. Fluxo de Trabalho

1. Um usuário acessa o aplicativo móvel e faz login.
2. O usuário pesquisa ou navega pelas fórmulas disponíveis no aplicativo.
3. O usuário seleciona uma fórmula e insere os valores para os parâmetros.
4. O frontend envia os valores dos parâmetros para o backend através de APIs RESTful.
5. O backend realiza os cálculos com base nos valores fornecidos e retorna os resultados ao frontend.
6. O resultado é exibido para o usuário no aplicativo móvel.

## 6. Considerações de Implementação


- Utilizar o Spring Security para implementar autenticação e autorização seguras no backend.
- Use `axios` or the browser/React Native `fetch` API to call the backend REST endpoints. For React Native, prefer the built-in `fetch` or `axios` with native adapters.
- Implementar testes automatizados tanto no frontend quanto no backend para garantir a qualidade e a estabilidade do sistema.

## 7. Cronograma de Desenvolvimento

- Definir marcos e prazos para o desenvolvimento, testes e lançamento do aplicativo, levando em consideração a integração entre o frontend e o backend.

## 8. Considerações de Manutenção

- Monitorar o desempenho do sistema e fazer ajustes conforme necessário para garantir uma experiência do usuário ideal.
-- Estar atento às atualizações do ReactJS/React Native e do Spring Boot e aplicar patches de segurança e melhorias conforme necessário.

## 9. Conclusão

Este documento estabelece a estrutura e os requisitos do sistema de assist
