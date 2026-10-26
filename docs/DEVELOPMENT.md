# Desenvolvimento Agenda Barber - F00-01

## Base do projeto
- backend/: Spring Boot 4.1.1 e Java 21; sem Neon/CRUD nesta etapa.
- android-app/: Kotlin e Jetpack Compose; Activity provisoria.
- .github/workflows/: CI (apos push futuro aprovado).
- docs/HANDOFF.md e docs/NEON_RESILIENCE.md preservados.
- .idea/ preservada e ignorada pelo Git.

## Backend local
Pre-requisitos: JDK 21 e Maven 3.9+.
Na raiz: mvn -f backend/pom.xml test
Build: mvn -f backend/pom.xml package
Executar: mvn -f backend/pom.xml spring-boot:run
Health: http://localhost:8080/actuator/health
Sem acesso a banco na F00. -o impede baixar dependencias.

## Android local
Pre-requisitos: Android SDK API 37, Gradle 9.4.1 e JDK 21.
AGP 9.2.0, Kotlin Compose Compiler 2.4.20, Compose BOM 2026.09.00.
Na pasta android-app: gradle :app:testDebugUnitTest
Build: gradle :app:assembleDebug
Lint: gradle :app:lintDebug
APK previsto: app/build/outputs/apk/debug/app-debug.apk

Gradle global e Android SDK nao localizados neste computador.
Nao foram instaladas ferramentas ou gerado wrapper nesta etapa.

## Seguranca, Git e CI
main local acompanha origin/main; sem commit nem push.
Nao versionar .env real, secrets, JWT, senhas do Neon, APK keys.
.env.example contem apenas exemplos; Neon sera integrado na F01.
UX de 10 telas e nav Agenda/Clientes/+/Estatisticas/Mais na F02.
CI sera executada em runners GitHub somente apos push autorizado.
