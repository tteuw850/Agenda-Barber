# Agenda Barber 1.0.0 — Runbook de lançamento

**Estado:** RC1, publicação bloqueada até os critérios de aceite serem verificados. Não mesclar na `main` antes do checklist.

## Evidências concluídas

- Branch isolada `release/1.0.0-rc1`, PR #43. Inclui logo AB, perfil, agenda diária/mensal, serviços, clientes, indicadores, recorrência e Devedores.
- CI Java/Android aprovada em https://github.com/tteuw850/Agenda-Barber/actions/runs/42160607125 (SHA 832de6b).
- Neon `production` (`br-lucky-voice-b6x87zwu`): migrações V1–V10, conferidas após execução. Backup anterior: `br-cold-rain-b6v6ji98` (`backup-production-before-release-1-0-20261106`).
- Simulação das migrações concluída em clone da produção `br-proud-sound-b6qpyjll`.
- Estrutura de produção separada preparada na EC2: `/opt/agenda-barber-prod/deploy/` e porta loopback 8081. **Não habilitada**.
- Chaves JWT independentes geradas na EC2 em `/opt/agenda-barber-prod/secrets/`, fora do repositório.
- Keystore Android local em `C:\Users\Administrador\Documents\AgendaBarberRelease\agenda-barber-release.jks`, alias `agenda-barber-v1`. Senha criptografada pelo Windows DPAPI no mesmo diretório. **Não subir ao GitHub.**
- Workflow `.github/workflows/release-smoke.yml` testa compilação e assinatura com chave CI efêmera, não destinada à distribuição.

## Bloqueios obrigatórios para liberar

1. Prover **somente por canal seguro** `NEON_JDBC_URL`, `NEON_DB_USERNAME=agenda_app_prod` e `NEON_DB_PASSWORD` de production na EC2, em `/opt/agenda-barber-prod/deploy/.env` (permissão 600). Nunca reutilizar a configuração DEV, nem imprimir segredos nos logs.
2. Configurar `AUTH_JWT_PRIVATE_KEY_PEM`, `AUTH_JWT_PUBLIC_KEY_PEM`, `AUTH_JWT_ISSUER` e `AUTH_JWT_AUDIENCE` para o ambiente PROD; as chaves já estão no diretório `secrets/` e devem ser carregadas sem registrá-las no terminal.
3. Construir `agenda-barber-prod:1.0.0-rc1` a partir do SHA aprovado; subir `docker compose` na porta 8081. Só adicionar bloco Caddy `prod-18-216-115-216.sslip.io` após endpoint `/actuator/health` retornar 200 localmente. Confirmar HTTPS público com certificado válido.
4. Provisionar primeiro barbeiro em production por procedimento administrativo seguro (senha com hash BCrypt, nunca plaintext no banco) e testar login, `/auth/me`, 401/403 e logout/refresh.
5. Em ambiente de teste autenticado, validar: cadastro de cliente/serviço, expediente, agendamento, seleção do dia, conflito 409, cancelamento, recorrência semanal, devedor, pagamento parcial e quitação, relatório e despesas. Conferir isolamento por `barber_id` e idempotência de POST.
6. Simular falha de rede no Android com cache já carregado: manter leitura da agenda, exibir último horário de sincronização, impedir escritas offline, recuperar a sincronização ao reconectar; verificar limpeza do cache após logout.
7. Exercitar plano de restauração do Neon **em clone**, não diretamente em produção. Manter o backup pre-release intacto até a homologação. Um rollback de banco pode descartar dados posteriores ao backup: planejar janela e checkpoint antes.
8. Gerar `assembleRelease` com `AB_PROD_API_URL` HTTPS real e `AB_RELEASE_KEYSTORE`, `AB_RELEASE_STORE_PASSWORD`, `AB_RELEASE_KEY_ALIAS`, `AB_RELEASE_KEY_PASSWORD` fornecidos por ambiente seguro. Conferir `apksigner verify`, package ID `com.agendabarber.app`, versão 1.0.0 e certificado SHA-256; registrar checksum do APK.
9. **Backup offline obrigatório** do arquivo JKS e da senha recuperável, em locais separados. A senha DPAPI local só pode ser decifrada pelo mesmo usuário Windows; não é substituto de um backup independente.
10. Instalar primeiro em dispositivo de homologação. Se há uma versão debug anterior de `com.agendabarber.app` assinada por outra chave, a atualização diretamente por cima **não funciona**. Preservar dados de servidor e validar migração antes de qualquer desinstalação, com autorização do usuário.
11. Só após todos os testes positivos, autorizar merge da PR #43, atribuir tag `v1.0.0` e distribuir o APK assinado definitivo.

## Política de rollback

- API: reverter imagem/compose preservada, sem tocar em Neon automaticamente; exigir compatibilidade de esquema.
- Neon: restaurar backup em branch nova, verificar dados e planejar cutover coordenado. Não usar operação destrutiva na branch atual por padrão.
- Android: não trocar assinatura de lançamento nem versionCode para trás; publicar hotfix com a mesma chave quando necessário.

**Status de segurança:** sem credenciais PROD no repositório; sem publicação de APK com URL DEV; sem alteração no aplicativo instalado.

## Evidências complementares — 06/11/2026

- Imagem de release na EC2: `agenda-barber-prod:1.0.0-rc1`, digest local `sha256:6061239c8e35b00b0850b5f9f7751de4435577805133552d7a0892471ec4096f`. `docker build` terminou com exit code 0.
- Teste isolado: container temporário com perfil `default`, porta local 18081, `/actuator/health` HTTP 200. API DEV permaneceu HTTP 200; container temporário removido.
- Neon: restaurado clone do backup PROD `br-icy-smoke-b65pj4r7`. Confirma Flyway V6, sem tabela `client_debt_entries`, zero clientes e zero usuários — cópia recuperou estado pré-migração. **Não remover automaticamente branches de backup ou validação.**
- APK debug do commit aprovado 832de6b instalado no Pixel_7 via ADB (`Performing Streamed Install / Success`), mas abertura funcional não confirmada: comandos ADB subsequentes travaram durante o boot do emulador. Não declarar teste visual/offline concluído.
- Produção ainda desligada: `/opt/agenda-barber-prod/deploy/.env` ausente, nenhum container PROD ativo. Não copiar segredos DEV para liberar prematuramente.
- Assinatura de distribuição continua dependente de credenciais protegidas no computador do responsável. O teste CI com assinatura descartável **não substitui a assinatura oficial**.

## Runbook complementar — configuração segura da API PROD

Artefatos criados e verificados na EC2:

- `/opt/agenda-barber-prod/deploy/configure-prod-env.py` — assistente interativo, código local com permissões `0700`. Solicita JDBC production e senha por `getpass`, exige `sslmode=verify-full`, cria `.env` com `0600` e preserva as chaves JWT exclusivas, sem imprimir a senha.
- `/opt/agenda-barber-prod/deploy/start-prod-loopback.sh` — recusa iniciar sem `.env` com modo `0600`; faz `docker compose config --quiet` e sobe a API somente em `127.0.0.1:8081`. Confirma endpoint `/actuator/health`, mas isso **não substitui um teste autenticado do banco**.
- `/opt/agenda-barber-prod/deploy/enable-prod-https.sh` — recusa habilitar o Caddy sem API saudável em 8081; salva a configuração anterior e valida o novo arquivo antes de recarregar. Só executar depois dos testes autenticados e da aprovação de publicação.

**Ação humana exigida pelo cofre de segredos:** abrir uma sessão SSH diretamente no CodeBridge, sem colar credenciais no chat, e executar:

```bash
python3 /opt/agenda-barber-prod/deploy/configure-prod-env.py
```

Inserir URL `jdbc:postgresql://...` da **branch production** do Neon, banco `agenda_barber`, com `?sslmode=verify-full`; quando solicitado, digitar a senha da role `agenda_app_prod` no prompt sem eco. Não usar credenciais da branch development.

Após configurar:

```bash
/opt/agenda-barber-prod/deploy/start-prod-loopback.sh
```

Testar autenticação, isolamento e escrita com usuário de produção criado de forma segura **antes** de executar:

```bash
/opt/agenda-barber-prod/deploy/enable-prod-https.sh
curl --fail --show-error https://prod-18-216-115-216.sslip.io/actuator/health
```

Não confundir: o teste HTTP 200 de `/actuator/health` pode funcionar mesmo com testes de banco pendentes, pois a checagem DB está desabilitada no perfil Neon.

### Evidências em clone isolado

No clone `br-proud-sound-b6qpyjll` foram inseridos **somente dados sintéticos** de dois barbeiros: lançamento de R$ 25 menos pagamento de R$ 10 resultou em R$ 15. PostgreSQL recusou conflito de horários, débito cruzado entre barbeiros (FK composta) e chave de lançamento duplicada. **Nenhuma dessas inserções foi executada na branch production**.

Os scripts de proteção foram exercitados sem credenciais e impediram subir PROD e habilitar HTTPS. A ativação real aguarda o passo de configuração segura acima.

## Produção interna conectada — 06/11/2026

- O usuário configurou as credenciais de `agenda_app_prod` em `/opt/agenda-barber-prod/deploy/.env`, modo `0600`, sem imprimir senhas.
- Startup inicialmente falhou pois o PostgreSQL JDBC procurou `/app/.postgresql/root.crt`. Corrigido com `sslmode=verify-full&sslrootcert=/etc/ssl/certs/ca-certificates.crt` no arquivo protegido, preservando validação de cadeia CA e hostname TLS. O assistente interativo de configuração passou a adicionar esse certificado automaticamente para futuras instalações.
- Container `agenda-barber-prod-api-1` usa `agenda-barber-prod:1.0.0-rc1` e responde HTTP 200 em `127.0.0.1:8081/actuator/health`; logs registram início bem-sucedido do pool Hikari. A API DEV em `127.0.0.1:8080` mantém HTTP 200.
- Endpoints sem bearer e login inválido retornaram 401, incluindo `/api/v1/debts`, `/api/v1/services` e `/api/v1/reports/dashboard`. Isso prova proteção básica, **não um teste completo de autenticação positiva**.
- Neon production ainda sem usuários, barbeiros, clientes, agendamentos ou dívidas. Backup adicional criado antes da tentativa de testes E2E: `br-misty-term-b6x00t7p` (backup-prod-before-auth-e2e-20261106).
- Criação automatizada de usuário temporário e execução E2E foi bloqueada por controles de segurança do CodeBridge. Nenhuma conta de teste foi criada. Um primeiro usuário deve ser provisionado por procedimento autorizado, com senha fornecida exclusivamente em canal seguro; nunca commit ou logar senha/hash.
- Container antigo de smoke `brave_taussig` (sem portas ou volumes) removido para liberar memória; serviços ativos são somente PROD interno e DEV interno.
- **Caddy de produção não foi ativado**; somente hostname DEV permanece no Caddyfile. Executar teste positivo de login/isolamento/agenda/recorrência/devedores/financeiro antes de publicar HTTPS PROD.
- Testes JVM do Room em PR #45, CI verde na execução 42246608976. Teste offline em dispositivo físico segue pendente.

## Primeira conta PROD e verificação positiva — 06/11/2026

- Cadastro de `ebinho` (`eber@barber.com`), role `BARBER`, efetuado **manualmente no SSH** pelo responsável usando o script interativo `create-first-barber.py`. Verificado no Neon: conta ativa, perfil de barbeiro vinculado e hash em formato BCrypt válido. Senha **não documentada nem registrada no repositório**.
- `agenda-barber-prod-api-1` segue saudável em `127.0.0.1:8081`; API DEV saudável em 8080. Teste de login inválido retornou 401. **Login correto ainda não testado.**
- Script de verificação de login real e leituras autenticadas preparado em `/opt/agenda-barber-prod/deploy/verify-ebinho-login.py` com permissão `0700`. Solicita senha por `getpass`; não grava nem imprime segredo/token. Verifica `auth/me`, clientes, serviços, agenda, recorrências, Devedores e dashboard. Apenas leitura, sem criar agendamentos nem modificar dados financeiros.
- Ao passar em todos os testes, grava recibo sem segredo em `.auth-qa-verified` (modo `0600`). Script `enable-prod-https.sh` **recusa** publicar Caddy PROD sem recibo válido e recente (até 1 hora), além de verificar saúde local e validar a configuração.
- **Próxima ação humana:** executar em terminal SSH próprio `python3 /opt/agenda-barber-prod/deploy/verify-ebinho-login.py`, digitar a senha temporária sem enviá-la ao chat e compartilhar apenas o resultado `QA_READ_ONLY_SUCCESS` ou erros HTTP. Revalidar fluxos de escrita e offline em etapas posteriores.
- Senha provisória foi compartilhada em conversa antes do lançamento e **deve ser trocada**, com revogação das sessões, antes de distribuir a versão oficial.

## Produção HTTPS ativada após QA positivo — 06/11/2026

- O responsável executou a rotina protegida de login por SSH: `LOGIN_RESULT=PASS HTTP_200`, e `auth_me`, `clients`, `services`, `debtors`, `recurrences`, `appointments`, `reports` retornaram HTTP 200, em modo somente leitura.
- Recibo QA com permissão `0600` e válido por uma hora foi verificado antes de habilitar PROD no Caddy.
- `enable-prod-https.sh` validou o Caddyfile, criou o backup `/etc/caddy/Caddyfile.before-prod-20261106T222548Z` e adicionou `prod-18-216-115-216.sslip.io` → `127.0.0.1:8081`.
- Certificado emitido pelo Let's Encrypt e validado pelo `curl`, com `HTTP 200` e `ssl_verify_result=0`, em `https://prod-18-216-115-216.sslip.io/actuator/health`. DEV permanece HTTPS 200 em `https://18-216-115-216.sslip.io`.
- HTTPS PROD sem bearer retornou HTTP 401 em `/api/v1/auth/me`, `/api/v1/debts`, `/api/v1/clients` e `/api/v1/appointment-recurrences`. **As operações de escrita autenticada continuam pendentes**, não devem ser confundidas com os testes somente de leitura.
- CI release `42305149176` validou `assembleRelease` e assinatura com **keystore CI descartável**, agora com `AB_PROD_API_URL=https://prod-18-216-115-216.sslip.io/`. O Gradle Kotlin DSL foi corrigido para comparar a hostname exata da DEV (evitando bloquear o subdomínio PROD). **Isso não produziu um APK com a chave oficial**.
- PR #45 passou na CI `42246608976` com testes de cache offline; requer revisão/merge.
- PR #46 (`feat/secure-password-rotation`) implementa alteração de senha autenticada e revogação de sessões, com testes integrados; aguarda CI e revisão. A senha temporária divulgada em conversa **precisa ser substituída antes da distribuição do APK**.
- Neon production tem 10 versões Flyway, uma conta ativa e um barbeiro, sem clientes/atendimentos lançados. Foi tentado novo backup após a conta, mas o Neon retornou `branches limit exceeded`; backups anteriores foram preservados. Não excluir branches existentes sem aprovação. Planejar backup consistente da configuração atual antes de qualquer alteração destrutiva.
- O domínio sslip.io inclui o IPv4 da EC2: antes de distribuir o aplicativo, confirmar que o endereço público é estático ou migrar para um domínio estável para evitar quebra do cliente após troca de IP.
