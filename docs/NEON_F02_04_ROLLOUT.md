# F02-04 — Neon migrado, API aguardando hospedagem HTTPS

Atualizado em 27/10/2026 após migrações e testes com conta de aplicação.

## Neon — estado confirmado

Projeto Agenda-Barber: holy-morning-13605097 / PostgreSQL 17,
banco agenda_barber.

| Branch | Flyway history | Esquema | Dados operacionais |
| --- | --- | --- | --- |
| development | V1 a V6 bem-sucedidas | COMPLETO | 0 usuários/clientes/atendimentos após smoke test |
| production | V1 a V6 bem-sucedidas | COMPLETO | 0 usuários/clientes/atendimentos |

A migration de development V2–V6 foi aplicada com conta
agenda_migrator_dev em transação SQL do Neon; production V1–V6
foi criada com agenda_barber_owner em transação SQL do Neon.
Os registros de flyway_schema_history foram preenchidos com
os checksums CRC32 do repositório, verificados contra a V1
instalada anteriormente em development. **Não foi executada a
CLI Flyway neste ambiente**. Uma validação futura usando a
própria CLI Flyway é recomendada antes de novos releases.

Cópia de segurança development:
br-dawn-dawn-b6aq5xxj (clone antes das migrações).
Snapshot production: snap-orange-dust-b6vve8wx
(antes da migração).

## Verificações executadas

- Existência de appointment_events, recurring_blocks, constraints
  de unicidade composta e campos de cliente.
- Permissões DML das roles agenda_app_dev e agenda_app_prod nas
  tabelas operacionais; sem SELECT na flyway_schema_history.
- Smoke SQL real em development, como agenda_app_dev:
  cadastro temporário de usuário/barbeiro/cliente/serviço,
  criação de atendimento e histórico, proteção anti-overlap
  PostgreSQL, unicidade da chave de idempotência e alterações
  até COMPLETED. Todos os dados do teste foram removidos.
- CI GitHub do backend Java e Android aprovado anteriormente.

**Isso ainda não comprova HTTP real nem uso no APK:** nenhuma API
HTTPS foi publicada e não existem contas de barbeiro cadastradas.

## Publicação HTTPS proposta: Render ou Railway

Hospedar o backend Java (não o APK Android) usando Railway ou outra
plataforma Java compatível. O Dockerfile de produção está em
backend/Dockerfile e usa JRE 21, imagem multi-stage e usuário sem root.

1. Conectar Render ou Railway, criar serviço Web e vincular GitHub
   tteuw850/Agenda-Barber com Root Directory /backend.
   O Dockerfile preservado da main já foi criado para Render;
   em Railway o mesmo Dockerfile pode ser usado.
2. Selecionar main após CI aprovado e configurar
   o health check GET /actuator/health no serviço.
   Ele não consulta Neon continuamente.
3. Criar variáveis **protegidas do provedor de hospedagem**:
   SPRING_PROFILES_ACTIVE=neon,
   NEON_JDBC_URL (JDBC TLS do banco agenda_barber),
   NEON_DB_USERNAME=agenda_app_prod,
   NEON_DB_PASSWORD (nunca no Git nem APK),
   AUTH_JWT_ISSUER,
   AUTH_JWT_AUDIENCE,
   AUTH_JWT_PRIVATE_KEY_PEM,
   AUTH_JWT_PUBLIC_KEY_PEM.
   Para staging, usar agenda_app_dev e a conexão da branch development.
   A aplicação valida par RSA de pelo menos 2048 bits.
4. Confirmar a URL HTTPS pública gerada pelo provedor.
   Configurar essa URL na tela inicial do Android.
5. Provisionar primeira conta de barbeiro por rotina
   administrativa segura (não há endpoint de registro público).
   Não usar credencial de demonstração em produção.
6. Testar login e fluxo GET agenda, POST com Idempotency-Key,
   GET detalhe/histórico, PATCH, start, complete, cancel,
   sobreposição 409, JWT inválido 401, desconexão 503 e reinício.

O arquivo application.yml preserva spring.flyway.enabled=false para
não executar migrações no start e usa Hikari maxPoolSize=3,
minimumIdle=0 e keepaliveTime=0 para reduzir impacto no Neon.

Status final desta entrega: bancos migrados; código pronto para
construir no host; **host HTTPS e teste operacional HTTP pendentes**
até conectar provedor de hospedagem e configurar segredos.
