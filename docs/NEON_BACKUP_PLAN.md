# Agenda Barber — plano de backup PROD (proposta)

## Verificacoes de 06/11/2026
- Neon Free, projeto holy-morning-13605097, PostgreSQL 17.
- 10/10 branches ocupados. Production, development e backups historicos foram preservados.
- Novo snapshot recusado pelo Neon: snapshots limit exceeded. O snapshot existente nao foi excluido.
- Janela PITR gratuita: 6 horas. Nao substitui backup externo diario.
- EC2 PROD com variaveis no arquivo protegido /opt/agenda-barber-prod/deploy/.env.
- Imagem Docker postgres:17 disponivel na EC2.
- Nenhum bucket S3, rotina automatica ou restauracao foi configurado ou validado por esta PR.

## Pre-requisitos AWS
1. Criar bucket S3 PRIVADO, bloquear acesso publico, ativar versionamento e criptografia padrao. Rever custos e politica de retencao.
2. Conceder a role da instancia EC2 apenas PutObject e ListBucket no prefixo de backups; evitar DeleteObject. Recuperacao preferencialmente por outra role.
3. Instalar AWS CLI e conferir identidade e permissao no bucket sem expor credenciais.
4. No servidor, instalar backup-production.sh como /usr/local/sbin/agenda-barber-backup-production (root, chmod 0700).
5. Criar /etc/agenda-barber-backup.conf (root, chmod 0600) contendo:
   AGENDA_BACKUP_S3_BUCKET=your-private-bucket-name
   AGENDA_BACKUP_S3_PREFIX=agenda-barber-prod
6. Executar manualmente a rotina. Exigir LOCAL_BACKUP_VERIFIED e OFFSITE_BACKUP_VERIFIED.
7. Baixar um backup do S3 e rodar restore-smoke-test.sh em contêiner descartavel, sem rede. Exigir RESTORE_TEST_PASS.
8. Somente depois da restauracao homologada: instalar unit files no systemd e ativar o timer via systemctl enable --now agenda-barber-backup.timer.
9. Monitorar diario: journalctl -u agenda-barber-backup.service e ultimo objeto valido, alertando quando a copia verificada tiver mais de 24 horas.

## Protecoes
- pg_dump executa apenas leitura em agenda_barber com TLS verify-full e conta agenda_app_prod.
- Banco e conta inesperados resultam em falha imediata.
- Senha nunca e impressa e e passada para o contêiner por nome de variavel de ambiente, sem valor nos argumentos.
- Arquivo local e checksum SHA-256 sao restritos a root; S3 usa SSE-S3 (AES256).
- Teste de restauracao usa PostgreSQL 17 separado, --network none, sem acesso ao Neon.
- Scripts nao apagam branches, snapshots, backups antigos ou dados do app; nao executam migracoes, merge ou deploy da API.
- Nao habilitar o timer ate validar S3 e restauracao. Configurar lifecycle de backup somente apos teste.

## Bloqueio de lancamento APK
Nao liberar APK ate confirmar backup externo atualizado, restauracao, login da primeira conta PROD, testes E2E autenticados de agendamentos/recorrencias/conflitos e homologacao do Android.
