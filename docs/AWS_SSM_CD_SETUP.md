# F05-04 — Deploy AWS DEV por GitHub OIDC, S3 privado e Systems Manager

**Estado:** preparado em PR, **nao executar** ate que bucket, IAM e Environment
tenham sido configurados e revisados. O teste OIDC read-only da PR #27
foi aprovado pelo usuario. O workflow atual de SSH deve ser substituido
somente depois de testar este novo caminho.

## Arquitetura

GitHub Actions (workflow_dispatch da main, Environment development e
confirmacao DEPLOY_AWS_DEV) -> IAM OIDC (credenciais temporarias) ->
S3 privado -> AWS Systems Manager / AWS-RunRemoteScript ->
EC2 i-02063104b98a730db -> Docker Compose + rollback existente.

Build Java/Docker acontece no runner hospedado pelo GitHub, nunca na EC2
t3.small. A imagem Docker e scripts da versao ficam em
`s3://BUCKET/releases/SHORT_SHA/`; scripts/deploy/aws-ec2-release-ssm.sh
confere SHA-256 do arquivo e chama aws-ec2-release.sh, que preserva o
Compose anterior e restaura o container em caso de health check ruim.
Nao precisa instalar AWS CLI na EC2: o SSM Agent baixa a pasta S3 pelo
plugin nativo do documento AWS-RunRemoteScript.

## 1. Criar bucket S3 PRIVADO em us-east-2

Nome sugerido: `agenda-barber-dev-releases-916949393497`.
O nome precisa ser globalmente unico. Se indisponivel, acrescente um sufixo
aleatorio curto e utilize exatamente o mesmo nome nas policies e variaveis.
No console S3:
- Region us-east-2 (Ohio); bucket do tipo General purpose;
- Object Ownership = Bucket owner enforced / ACLs disabled;
- Block all public access = ligado;
- Default encryption = SSE-S3 (AES256), nao usar SSE-KMS custom sem IAM extra;
- Sem public website hosting; sem bucket policy concedendo acesso publico;
- Versioning opcional; para artefatos imutaveis por SHA pode manter desligado;
- Regra de lifecycle opcional: expirar objetos sob `releases/` apos 14 dias
  (custos e armazenamento nao sao necessariamente gratuitos).

## 2. GitHub Environment development

Adicionar **variavel** (nao secret):
`AWS_DEV_RELEASE_BUCKET` = nome exato do bucket criado.
Variaveis ja presentes: `AWS_ROLE_ARN`, `AWS_DEV_BASE_URL`,
`AWS_DEV_HOST` (ultima sera ignorada pelo CD SSM).
A funcao OIDC e `AgendaBarber-GitHub-SSM-DEV-Role`.
O workflow continua restrito a `main` e confirmacao `DEPLOY_AWS_DEV`.

## 3. IAM — funcao GitHub OIDC (gravar artefatos e iniciar SSM)

Na funcao `AgendaBarber-GitHub-SSM-DEV-Role`, edite a **policy de
permissoes em linha** `AgendaBarber-GitHub-SSM-DEV-RolePolicy`.
Substitua BUCKET_REAL no JSON abaixo, mantendo as permissoes existentes
de leitura de comandos e limitando SendCommand ao documento remoto
`AWS-RunRemoteScript` e a uma unica instancia EC2:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "UploadOnlyVersionedAgendaBarberDevReleases",
      "Effect": "Allow",
      "Action": ["s3:PutObject"],
      "Resource": "arn:aws:s3:::BUCKET_REAL/releases/*"
    },
    {
      "Sid": "RunRemoteScriptOnOnlyAgendaBarberDev",
      "Effect": "Allow",
      "Action": ["ssm:SendCommand"],
      "Resource": [
        "arn:aws:ssm:us-east-2::document/AWS-RunRemoteScript",
        "arn:aws:ec2:us-east-2:916949393497:instance/i-02063104b98a730db"
      ]
    },
    {
      "Sid": "ReadDeploymentStatus",
      "Effect": "Allow",
      "Action": [
        "ssm:GetCommandInvocation",
        "ssm:ListCommandInvocations",
        "ssm:ListCommands",
        "ssm:DescribeInstanceInformation"
      ],
      "Resource": "*"
    }
  ]
}
```

Esta e uma **policy de permissoes**: NAO incluir `Principal`.
A **policy de confianca OIDC** deve permanecer como ja aprovada,
com `sub` especifico do repositorio e do Environment development.
Nao adicionar `AdministratorAccess` ou `AmazonS3FullAccess`.

## 4. IAM — funcao da EC2 (somente leitura no bucket)

Na funcao `AgendaBarber-EC2-SSM-Role`, **mantenha** a policy
`AmazonSSMManagedInstanceCore` e anexe uma NOVA policy em linha
chamada `AgendaBarberDevReleaseReadOnly`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ListOnlyAgendaBarberDevReleases",
      "Effect": "Allow",
      "Action": "s3:ListBucket",
      "Resource": "arn:aws:s3:::BUCKET_REAL",
      "Condition": {"StringLike": {"s3:prefix": ["releases/*"]}}
    },
    {
      "Sid": "DownloadOnlyAgendaBarberDevReleases",
      "Effect": "Allow",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::BUCKET_REAL/releases/*"
    }
  ]
}
```

O SSM Agent usa a credencial da propria EC2 para baixar os arquivos privados.
Em bucket na mesma conta e sem explicit Deny, essas policies IAM
normalmente bastam; verifique outras policies/bucket policy se houver.
O documento `AWS-RunRemoteScript` precisa poder baixar uma pasta com os
tres arquivos: imagem comprimida e dois scripts.

## 5. Validacao antes de qualquer deploy

- Confirmar bucket privado, policies anexadas e variavel GitHub.
- Confirmar que EC2 aparece Online no SSM e API HTTPS continua 200 UP.
- Opcional: testar primeiro upload e download pequeno com SSM.
- Somente entao integrar esta PR e iniciar manualmente o workflow
  `CD - AWS EC2 DEV via OIDC + S3 + SSM (manual)`, na main,
  digitando `DEPLOY_AWS_DEV`.
- Acompanhar a ID do comando SSM, status Success e HTTP 200 UP/401
  sem token; conferir Docker tag e manter imagem antiga para rollback.
- O primeiro deploy real precisa validar se o documento
  `AWS-RunRemoteScript` baixa o diretorio S3 com os nomes esperados.

O novo workflow NAO faz deploy automatico em merge ou push.
Nao alterar Neon production; Render nao e destino do CD.
Apos o primeiro deploy validado, remover os SECRETS SSH legados do
Environment GitHub e revisar o acesso publico SSH da EC2.
O endereco HTTPS atual usa sslip.io e IP dinamico; planejar Elastic IP
ou dominio estavel e os custos associados.
