# F00-03 - CI e convencoes do Agenda Barber

## Gatilhos e seguranca
- Push na branch main; pull requests e workflow_dispatch.
- Permissoes minimas GitHub Actions: contents: read.
- Checkout sem persistir credenciais; nao usar pull_request_target.
- Nenhum token, JWT, string Neon real, senha ou keystore entra em CI/logs.
- Nao publicar ou alterar Trello/GitHub sem nova aprovacao.

## Backend Java 21 e Maven
- Maven verify executa JUnit e Checkstyle 3.6.0 na fase validate.
- Utilizar configuracao interna sun_checks.xml; falha em violacoes.
- 4 espacos de indentacao, imports explicitos, classes claras.
- Comando CI: mvn -B -ntp -f backend/pom.xml verify.
- Maven Checkstyle nao consta no cache local: verificar offline sem instalar.
- Erros de estilo em codigo anterior exigem proposta separada.

## Android Kotlin e Gradle
- Gradle 9.4.1 no runner com AGP 9.2.0 e Kotlin Compose 2.4.20.
- ktlint Gradle 14.2.0: ktlintCheck falha por estilo; sem autoformat.
- Android Lint abortOnError=true; sem baseline.
- CI: gradle --no-daemon :app:ktlintCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug.
- Gradle e SDK nao estao instalados no PC; nada instalar na F00-03.
- Gradle Wrapper continua ausente; runners GitHub provisionam Gradle.

## Formato e criterios
- .editorconfig: UTF-8, LF, newline, 4 espacos Java/Kotlin, YAML 2.
- Alvo de 120 caracteres para codigo; revisar imports/warnings.
- CI deve falhar para JUnit, Checkstyle, ktlint e Android Lint.
- Falta validar a pipeline realmente executada em push ou PR apos autorizacao.
- Validar YAML, XML, git diff --check e build Java local offline.
- Compatibilidade ktlint com AGP9 Kotlin embutido ainda requer teste real.
- Nao alterar segredos, fontes anteriores, README ou demais projetos.
