# Agenda Barber — CI/CD (revisão inicial, sem deploy executado)

## Limites e status

A CI valida Java/Spring Boot, Dockerfile, Kotlin/Android e gera APK **debug** para revisão manual.
A CD é **somente manual** e limitada ao **Render DEV**, após nova autorização, merge na
branch padrão e configuração de segredos/proteções. Não há pipeline de produção,
nenhum deploy nesta entrega, publicação Play Store ou migração Neon automática.

Dependência de branches: PR F02-07 empilhada na branch `feat/f02-05-android-shell`
(PR #9 ainda pendente de integração na `main`). Novos workflows somente ficam
disponíveis para `workflow_dispatch` na interface quando estiverem na branch padrão.

## CI — `.github/workflows/ci.yml`

Triggers: PR, push na `main` e execução manual. Permissão mínima `contents: read`.
- Java 21, `mvn -B -ntp -f backend/pom.xml verify`, Checkstyle e `docker build` **sem push**.
- Android SDK 36, Gradle 9.4.1, `ktlintCheck`, `testDebugUnitTest`, `lintDebug`, `assembleDebug`.
- Artefatos: `agenda-barber-debug-apk`, `backend-test-reports`,
  `android-test-and-lint-reports` (até 7 dias). APK debug **não** é versão de produção.
- Timeouts e concurrency. CI não acessa Neon de produção nem Render via deploy hook.

## Segurança — `.github/workflows/security.yml`

Trigger: PR, push main e manual. Executa `scripts/ci/security_checks.py`:
busca padrões **de alta confiança** de chaves em arquivos Git rastreados e verifica
que a CD não dispara em push/PR. Saída imprime caminhos/linhas, **nunca o segredo**.
`scripts/ci/dependency_inventory.py` produz lista de **dependências diretas** de
Maven/Gradle para revisão. Isso **não é** auditoria de CVEs ou SCA abrangente;
implementar Dependabot e varredura de CVEs conforme políticas do repositório.

## CD DEV — `.github/workflows/cd.yml` (não executar nesta etapa)

Somente `workflow_dispatch`, branch `main`, texto exato `DEPLOY_DEV`, e
GitHub Environment `development`. Ao ser executada posteriormente, valida
o backend, depois aciona o Render deploy hook via `RENDER_DEV_DEPLOY_HOOK`
**armazenado como secret do Environment**. O hook não deve aparecer em logs.
No Render, o serviço DEV é `agenda-barber-api-dev` (`autoDeploy=no`,
verificado durante o planejamento); nenhuma configuração de produção é tocada.

### Preparação manual ANTES de qualquer futuro deploy (não realizada nesta entrega)
1. Após aprovação dos PRs, mesclar ordenadamente F02-05 e F02-07 na `main`.
2. Em GitHub Settings > Environments, configurar `development` com
   required reviewers e regra de branch limitada a `main`, se disponível no plano.
   **Não presumir que a proteção esteja configurada automaticamente.**
3. Criar secret de Environment `RENDER_DEV_DEPLOY_HOOK` contendo **somente**
   o hook do serviço DEV, obtido do painel Render. Não colocar URL/chave em commit,
   comentários do Trello ou logs. Manter `autoDeploy=no` para não implantar em merges.
4. Solicitar autorização específica para uma execução de CD; o `workflow_dispatch`
   não será disparado nesta etapa.

### Verificação e rollback

A chamada ao hook indica **solicitação** de deploy, não comprova nova versão no ar.
Acompanhar deploy, logs e `/actuator/health` no Render; verificar autenticação e API
contra Neon **development**. Se falhar, usar o rollback/novo deploy manual no Render
e revisar logs e migrações antes de tentar novamente. Banco de produção não é alvo.

## Rodar as verificações localmente

```powershell
cd D:\appeber
python scripts\ci\security_checks.py
python scripts\ci\dependency_inventory.py
cd android-app
gradle :app:ktlintCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
cd ..\backend
mvn verify
```

O Python não instala dependências extras. O SDK/Gradle/Maven necessários já fazem
parte da configuração existente; substituir comandos pelos executáveis locais.
Conferir os reports em GitHub Actions no PR antes de aprovar merge.

## Pendências F02-07

Além da CI, continuam necessários teste operacional de criação, edição, início,
conclusão, cancelamento, retorno da UX-05, repetição idempotente e conflito 409,
além dos screenshots comparativos UX-03/UX-04 com o mockup aprovado. Não aprovar
F02-07 somente porque o Gradle passou.
