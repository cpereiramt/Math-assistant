# Primeiro deploy do Math Assistant no Cloud Run

1. Confirme o **ID** do projeto no Console e que há faturamento ativo. Um administrador
   do projeto pode ativar Cloud Run, Artifact Registry, Secret Manager, IAM, IAM
   Service Account Credentials e Security Token Service:

   ```bash
   gcloud services enable run.googleapis.com artifactregistry.googleapis.com \
     secretmanager.googleapis.com iam.googleapis.com iamcredentials.googleapis.com \
     sts.googleapis.com --project PROJECT_ID
   ```

2. Crie um repositório Docker no Artifact Registry e duas contas de serviço.
   Conceda à conta de deploy Artifact Registry Writer no repositório, Cloud Run
   Developer no projeto e Service Account User na conta de execução. Conceda
   Secret Manager Secret Accessor à conta de execução em cada um dos cinco
   segredos abaixo. Configure Workload Identity Federation para GitHub Actions,
   restrinja o provedor ao repositório `cpereiramt/Math-assistant` e à branch
   autorizada (por exemplo, `refs/heads/main`) e conceda Workload Identity User
   na conta de deploy à identidade federada correspondente.
   [Configuração oficial do Workload Identity Federation](https://cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines#github-actions)

3. Crie os segredos no Google Secret Manager, cada um com uma versão ativa.
   Use uma base MongoDB de homologação com transações para o primeiro deploy.
   Confira a conectividade do Cloud Run com a base, incluindo eventual allowlist.
   Preserve a chave JWT atual quando migrar tráfego de produção.

   | Segredo | Variável injetada |
   | --- | --- |
   | `math-assistant-mongo-uri` | `MONGO_URI` |
   | `math-assistant-jwt-secret` | `JWT_SECRET` |
   | `math-assistant-google-client-id` | `OAUTH_GOOGLE_CLIENT_ID` |
   | `math-assistant-google-client-secret` | `OAUTH_GOOGLE_CLIENT_SECRET` |
   | `math-assistant-admin-creds` | `ADMIN_CREDS` |

4. No GitHub, em **Settings → Environments**, crie `cloud-run` e configure estas
   variáveis. Se usar uma restrição de branch no provedor OIDC, aplique a mesma
   restrição no ambiente.

   | Variável | Valor |
   | --- | --- |
   | `GCP_PROJECT_ID` | ID do projeto GCP |
   | `GCP_REGION` | Região do Cloud Run e do Artifact Registry |
   | `ARTIFACT_REGISTRY_REPOSITORY` | Nome do repositório Docker |
   | `CLOUD_RUN_SERVICE` | Nome do serviço, por exemplo `math-assistant-staging` |
   | `GCP_WORKLOAD_IDENTITY_PROVIDER` | Caminho completo `projects/NUMERO/locations/global/workloadIdentityPools/POOL/providers/PROVIDER` |
   | `GCP_DEPLOY_SERVICE_ACCOUNT` | E-mail da conta de deploy |
   | `CLOUD_RUN_RUNTIME_SERVICE_ACCOUNT` | E-mail da conta de execução |
   | `MONGODB_DATABASE` | Nome da base |
   | `OAUTH_GOOGLE_REDIRECT_URI` | URL HTTPS pública do backend + `/login/oauth2/code/google` |
   | `FRONTEND_BASE_URL` | URL do frontend |
   | `APP_CORS_ALLOWED_ORIGINS` | Origens autorizadas, separadas por vírgula |
   | `JWT_EXPIRATION` | Duração em milissegundos, padrão `3600000` |

5. Depois de o workflow estar disponível na branch padrão, abra **Actions →
   Deploy to Google Cloud Run → Run workflow**. Se ainda não souber a URL gerada
   pelo Cloud Run, use uma URL provisória válida para o primeiro deploy, consulte a
   URL do serviço, atualize a variável do callback e registre a URL exata no
   Google OAuth antes do teste de login. Novos serviços seguem o padrão privado
   da plataforma. Configure acesso público no Cloud Run quando for testar login
   pelo navegador, com a autenticação do Spring protegendo a API.

O workflow do EC2 continua ativo para pushes na `main` durante a transição.
Valide `/actuator/health`, login Google e publicação/revisão de fórmulas antes de
trocar o domínio. A suíte de moderação requer `FORMULA_TEST_MONGO_URI` apontando
para um replica set de testes. O deploy manual não usa a base de produção nos testes.
