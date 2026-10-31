# Agenda Barber: AWS EC2 only (31/10/2026)

## Current hosting
- API Spring Boot 4.1.1 / Java 21 / PostgreSQL Neon.
- AWS EC2 Ubuntu 24.04, Caddy HTTPS, Docker Compose.
- URL: https://18-216-115-216.sslip.io
- Runtime: /opt/agenda-barber-dev/deploy/compose.yaml
- Secret env: /opt/agenda-barber-dev/deploy/.env (chmod 600; never commit).
- Source checkout: /opt/agenda-barber-dev/src, branch main.
- Container: deploy-api-1, local-only port 127.0.0.1:8080.
- Known good image: agenda-barber-dev:aws-dfd774e
- Previous rollback image: agenda-barber-dev:rollback-1c0e86a.
- Health HTTP 200 UP; unauthenticated /api/v1/clients HTTP 401.

## CI
.github/workflows/ci.yml builds/tests Java and Android. security.yml
checks source guardrails. The backend remains deployed to AWS, not Render.

## Deployment
Deployment to EC2 is currently managed with authorized SSH access from
CodeBridge. Avoid building Docker images on the 2 GB EC2; the Docker build
caused SSH instability on 31/10/2026. Prefer preparing an image on a CI
runner or stronger build host, transferring the image securely, and activating
it with scripts/deploy/aws-ec2-release.sh. The script saves the previous
Compose file and restores it when health checks fail.

The old Render deployment trigger was removed. There is no enabled
automatic AWS deployment workflow; do not imply future GitHub merges
deploy themselves. Add an AWS deployment workflow after provisioning
a dedicated restricted key and testing rollback.

## Monitoring
.github/workflows/aws-dev-health.yml checks API health and authentication
four times per day. The monitoring workflow currently uses the verified AWS HTTPS URL
https://18-216-115-216.sslip.io directly. Update it when public DNS changes.
Use AWS CloudWatch/Billing Budgets, Docker logs and Caddy logs.

## Android
The login screen defaults to https://18-216-115-216.sslip.io/ and still
offers API HTTPS for changing host. Old APKs need manual configuration.
The GitHub Actions debug APK is not a production release.
Validate login, scheduling, client/service CRUD, financial reports and
offline behavior with Neon development.

## Stable public address
The sslip.io hostname encodes the current EC2 public IPv4. A stop/start
of EC2 can change the IPv4 address and break Android HTTPS. Set up
an AWS Elastic IP or managed DNS and consider its price before relying
on this as a long-term public address.

## Retiring Render
Remove old Render deployment web service, hooks and GitHub secrets.
Never delete Neon PostgreSQL; it is an independent provider.
The Render connector does not support deleting services. Confirm
deletion separately in Render dashboard Settings. Until then do not
claim the Render resource has been deleted or billing terminated.

## GitHub Actions AWS CD — migracao OIDC + S3 + SSM

O workflow manual anterior por SSH falhou porque a regra do Security Group
libera TCP/22 somente para o IP residencial. O teste GitHub OIDC com
credenciais temporarias e leitura do SSM (PR #27) passou.

O novo workflow esta preparado numa PR separada, **ainda nao ativado**:
- autenticacao OIDC com `AWS_ROLE_ARN` restrita a `development`;
- build Maven + Docker no runner GitHub, evitando carga na EC2 pequena;
- envio da imagem e scripts para bucket S3 privado, por hash do commit;
- SSM `AWS-RunRemoteScript` na instancia EC2 de desenvolvimento;
- validacao SHA-256 da imagem, Docker Compose e rollback existente;
- verificacao HTTPS da API, sem deploy automatico por push ou merge.

**Pre-requisitos e policies de IAM:** veja
[docs/AWS_SSM_CD_SETUP.md](AWS_SSM_CD_SETUP.md).

Antes da primeira execucao: criar o bucket, anexar permissoes minimas
a funcao OIDC e a funcao EC2, cadastrar `AWS_DEV_RELEASE_BUCKET` no
Environment `development` e testar leitura via SSM. Ate a validacao
deste fluxo, nao apagar credenciais antigas nem alegar CD concluido.
O workflow somente tem efeito ao executar manualmente
`DEPLOY_AWS_DEV` na branch `main`.
