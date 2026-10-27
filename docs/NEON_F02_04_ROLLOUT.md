# F02-04 — Preparação real do Neon e publicação da API

## Evidência conferida em 27/10/2026 (somente leitura)

Projeto Neon: Agenda-Barber (holy-morning-13605097)
Banco: agenda_barber, PostgreSQL 17.

- Branch production (br-lucky-voice-b6x87zwu): sem flyway_schema_history,
  sem tabela appointments e sem appointment_events.
- Branch development (br-young-flower-b6fb6z4j): possui
  flyway_schema_history + appointments, mas somente Flyway V1 aplicada,
  e não possui appointment_events.

Assim, **não é seguro executar apenas V6 em production ou development**.
A migração V6 depende da sequência V1 -> V2 -> V3 -> V4 -> V5 -> V6.
As seis migrações passaram no Testcontainers PostgreSQL 17 do CI, mas
essa validação NÃO equivale à implantação nas branches Neon.

## Procedimento de implantação separado do merge

1. Confirmar backup/snapshot do banco que receberá a migração, verificar
   esquemas existentes e conflitos de unicidade (especialmente telefones).
2. No development, executar as pendentes V2–V6 com Flyway (respeitando
   checksums e flyway_schema_history), não com SQL solto.
3. Validar restrições, índice de appointments e eventos, e executar
   testes HTTP contra a API apontando para development.
4. Após validação e autorização separada, criar/validar esquema de
   produção pela sequência V1–V6 em produção (atualmente vazia).
5. Publicar API com HTTPS e variáveis de ambiente protegidas; habilitar
   profile neon e configurar issuer/keys JWT fora do GitHub e APK.
6. No Android informar a URL HTTPS publicada, fazer login e executar
   roteiro de aceite: listar dia/semana/mês, cliente/serviço/slot, POST
   com Idempotency-Key, detalhes, histórico, iniciar, concluir, cancelar,
   editar, 409 colisões, indisponibilidade 503 e teste pós-retomada.
7. Somente depois concluir aceite integral do cartão F02-04.

## Segurança e reversão

- Jamais inserir credenciais do Neon ou chaves JWT no APK, Git ou Trello.
- Não desabilitar restrições PostgreSQL ou fazer keep-alive Neon.
- Se a V6 falhar, NÃO editar versões de migrations aplicadas;
  analisar status do Flyway/backup antes de tentar recuperar.
- O merge da camada Android não implica autorização para aplicar
  migrações em production ou deploy de API.

Status: migração/deploy **não executados**. A integração Android
compila em CI mas ainda exige ensaio manual com um servidor acessível.
