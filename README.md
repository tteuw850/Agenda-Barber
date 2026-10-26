# Agenda Barber

App nativo Android (APK) EXCLUSIVO PARA O BARBEIRO. Foco na agenda e nos resultados do barbeiro. Navegacao prevista: **Agenda | Clientes | + | Estatisticas | Mais**. Cliente nao acessa sistema; o barbeiro cadastra os agendamentos manualmente.

**Tecnologias:** Java 21 + Spring Boot 4.1.x + JPA + Spring Security + Flyway; Kotlin + Jetpack Compose + MVVM + Retrofit + Room; Neon PostgreSQL.

**Status:** estrutura F00-01 criada localmente; agendamentos, API Neon e telas UX ainda nao implementados.

## Documentacao
- [Handoff e arquitetura](docs/HANDOFF.md)
- [Recuperacao da suspensao do Neon](docs/NEON_RESILIENCE.md)
- [Design system UX](docs/ux/DESIGN_SYSTEM.md)
- [Dez telas e componentes](docs/ux/SCREENS.md)
- [Mapa de navegação](docs/ux/NAVIGATION.md)
- [Critérios de aceite UX](docs/ux/UX_ACCEPTANCE.md)
- [Trello APPBARBEARIA](https://trello.com/b/p6yR1n5x/appbarbearia)

## Organizacao futura
backend/ - API Java; android-app/ - aplicativo Kotlin; docs/ - especificacoes; .github/workflows/ - testes.

**Seguranca:** APK nunca se conecta ao banco diretamente; nao versionar senhas nem chaves.

## Estrutura inicial
- backend/: Spring Boot 4.1.1 / Java 21 / Maven.
- android-app/: Kotlin / Jetpack Compose (Activity provisoria).
- .github/workflows/ci.yml: validacao automatizada futura.
- docs/DEVELOPMENT.md: executar builds e testes.

A configuracao Neon/JPA/Security entra na etapa F01; interfaces do mockup na F02.
GitHub e Trello so serao alterados apos autorizacao especifica.
