# Agenda Barber

App nativo Android (APK) EXCLUSIVO PARA O BARBEIRO. Apenas duas abas principais: **Agenda** e **Resultados**. Cliente nao acessa sistema; o barbeiro cadastra os agendamentos manualmente.

**Tecnologias:** Java 21 + Spring Boot 4.1.x + JPA + Spring Security + Flyway; Kotlin + Jetpack Compose + MVVM + Retrofit + Room; Neon PostgreSQL.

**Status:** especificacao inicial documentada; funcionalidades ainda nao implementadas.

## Documentacao
- [Handoff e arquitetura](docs/HANDOFF.md)
- [Recuperacao da suspensao do Neon](docs/NEON_RESILIENCE.md)
- [Trello APPBARBEARIA](https://trello.com/b/p6yR1n5x/appbarbearia)

## Organizacao futura
backend/ - API Java; android-app/ - aplicativo Kotlin; docs/ - especificacoes; .github/workflows/ - testes.

**Seguranca:** APK nunca se conecta ao banco diretamente; nao versionar senhas nem chaves.
