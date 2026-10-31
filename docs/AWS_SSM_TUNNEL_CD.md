# Agenda Barber — CD somente na EC2, sem bucket S3

Estado: preparado em PR em rascunho. **Nao executar deploy antes de validar
as permissoes IAM por sessao e testar o tunel controladamente.**

## Como funciona

- GitHub Actions e acionado APENAS por workflow_dispatch na main, com
  confirmacao literal DEPLOY_AWS_DEV e Environment development protegido.
- Java/Maven + Docker build rodam no runner do GitHub para poupar memoria da
  instancia EC2 t3.small.
- GitHub assume via OIDC a role temporaria
  AgendaBarber-GitHub-SSM-DEV-Role, ja validada em teste read-only.
- A AWS CLI e o Session Manager plugin abrem tunel de rede privado
  `127.0.0.1:2222 (runner) -> 22 (EC2)` com documento SSM
  `AWS-StartPortForwardingSession`.
- O SCP transfere a imagem Docker compactada e o script de deploy pelo
  tunel, com chave SSH dedicada e host key previamente verificada.
- O script existente `scripts/deploy/aws-ec2-release.sh` importa a imagem
  e atualiza Docker Compose; guarda backup do Compose e faz rollback se
  a API nao ficar saudavel.
- O GitHub valida HTTPS status UP e endpoint protegido HTTP 401.
- O tunel e encerrado no final. Portas 80/443 permanecem publicas;
  nenhuma alteracao em Security Group ou porta 22 publica e necessaria.

**Nao criar S3 bucket, ECR, GHCR, Runner self-hosted ou armazenamento extra.**
O download do plugin oficial do Session Manager vem da distribuicao
publica da AWS, nao e um bucket da conta do proprietario.

## Variaveis e secrets do GitHub Environment development

Usa os valores JA existentes, sem adicionar nenhum novo:
- variable AWS_ROLE_ARN:
  arn:aws:iam::916949393497:role/AgendaBarber-GitHub-SSM-DEV-Role
- variable AWS_DEV_BASE_URL:
  https://18-216-115-216.sslip.io
- secret AWS_DEV_SSH_PRIVATE_KEY: chave de deploy exclusiva;
- secret AWS_DEV_SSH_KNOWN_HOSTS: host key verificada da EC2;
- AWS_DEV_HOST pode continuar configurada, mas NAO e usada no novo CD.

Nao imprimir, exportar ou salvar a chave privada em arquivos versionados.
O usuario criou a chave exclusiva e autorizou a publica na EC2.
O SSH roda somente dentro do tunel SSM e ainda verifica o host por chave.

## IAM — atualizar a policy de PERMISSOES da role OIDC

Em IAM > Roles > AgendaBarber-GitHub-SSM-DEV-Role >
Permissions > AgendaBarber-GitHub-SSM-DEV-RolePolicy >
Edit > JSON, use a politica minima abaixo.

Nao colar isto na policy de *Trust relationships*; mantenha a trust
OIDC existente restrita ao repo + environment development.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "StartOnlyPortForwardingToAgendaBarberDev",
      "Effect": "Allow",
      "Action": "ssm:StartSession",
      "Resource": [
        "arn:aws:ec2:us-east-2:916949393497:instance/i-02063104b98a730db",
        "arn:aws:ssm:us-east-2::document/AWS-StartPortForwardingSession"
      ]
    },
    {
      "Sid": "OpenDataChannelForOwnSession",
      "Effect": "Allow",
      "Action": "ssmmessages:OpenDataChannel",
      "Resource": [
        "arn:aws:ssm:us-east-2:916949393497:session/AgendaBarberDevDeploy-*",
        "arn:aws:ssm:us-east-2:916949393497:session/${aws:userid}-*"
      ]
    },
    {
      "Sid": "EndOwnSession",
      "Effect": "Allow",
      "Action": ["ssm:TerminateSession", "ssm:ResumeSession"],
      "Resource": [
        "arn:aws:ssm:us-east-2:916949393497:session/AgendaBarberDevDeploy-*",
        "arn:aws:ssm:us-east-2:916949393497:session/${aws:userid}-*"
      ]
    },
    {
      "Sid": "ReadSsmAvailability",
      "Effect": "Allow",
      "Action": ["ssm:DescribeInstanceInformation", "ssm:GetConnectionStatus"],
      "Resource": "*"
    }
  ]
}
```

Nao adicionar AdministratorAccess, AmazonSSMFullAccess ou permissoes S3.
Esta permissao SSM e poderosa: permite abrir uma porta de tunel para a
instancia DEV, mas NAO autoriza outras EC2; o SSH exige chave dedicada.
Se desejar segregar CD do teste OIDC, mantenha as permissoes
DescribeInstanceInformation acima.

## Checklist antes de integrar a PR

1. Confirmar role de OIDC com trust policy restrita ao repositorio com
   IDs imutaveis e environment development.
2. Atualizar a **permissao** IAM acima e confirmar que a EC2 esta Online no SSM.
3. Confirmar que Ubuntu EC2 tem sshd ouvindo em 127.0.0.1:22 (ou 0.0.0.0:22)
   e chave PUBLICA especifica autorizada para ubuntu.
4. Validar a PR Security/CI e revisar os comandos do script de deploy.
5. Fazer um primeiro teste controlado do tunel e de autenticao SSH,
   de preferencia usando um workflow de read-only, antes de qualquer release.
6. Depois liberar o merge e o primeiro workflow manual de deploy na main.
7. Confirmar que imagem no Docker e tag nova, GET /actuator/health = UP,
   GET /api/v1/clients sem token = 401 e rollback preservado.
8. Nao alterar Neon PRODUCTION nem Render durante este teste.

## Custos e operacao

Sem bucket S3, sem armazenamento adicional na AWS. Permanecem custos
potenciais de EC2/EBS/IPv4, trafego e minutos do GitHub Actions conforme
plano. Nao confundir com custo zero garantido.

Hostname sslip.io atual depende do IP publico 18.216.115.216; a AWS
nao tem Elastic IP associado. Antes de servico de longo prazo, escolher
IP estavel/dominio e revisar custos associados.
