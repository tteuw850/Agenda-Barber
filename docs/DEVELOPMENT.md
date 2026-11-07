# Desenvolvimento e builds — Agenda Barber 1.0.0

[← README](../README.md) · [Arquitetura](ARCHITECTURE.md) · [Runbook](RELEASE_1_0_0_RUNBOOK.md)

> Documentação do estado da aplicação Android e API da versão 1.0.0 em 07/11/2026. Não usar instruções antigas da fase F00 como roteiro de deploy.

## Pré-requisitos

| Ferramenta | Versão de referência |
|---|---|
| JDK | 21 (backend e Gradle) |
| Maven | 3.9+ |
| Gradle | 9.4.1 |
| Android SDK / Build Tools | API 36 / 36.0.0 |
| Android minSdk | 26 |
| Kotlin / Compose | conforme versões fixadas em `android-app` |
| Docker | necessário para Testcontainers PostgreSQL 17 |
| Banco de dados | PostgreSQL 17 em Neon, com ambiente e credenciais isolados |

A compilação local Android não exige acesso ao Neon ou às credenciais PROD. Um APK de debug NÃO serve como APK oficial.

## API Java

```bash
# No diretório raiz:
mvn -f backend/pom.xml test
mvn -f backend/pom.xml verify
mvn -f backend/pom.xml package -DskipTests

# Com Docker ativo, os testes de integração sobem PostgreSQL temporário.
# Não utilizam Neon production.
```

A API usa perfil `neon` para execução real. As variáveis de conexão, TLS e JWT devem vir de configuração protegida no host, nunca de arquivos versionados. O runtime de produção não executa Flyway automaticamente; as migrações só devem ser aplicadas por processo autorizado.

## Android: build de desenvolvimento

```bash
cd android-app
gradle :app:ktlintCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK QA: `android-app/app/build/outputs/apk/debug/app-debug.apk`. O `applicationIdSuffix = ".qa"` isola a instalação QA do pacote oficial `com.agendabarber.app`.

## Android: release oficial assinada

O `assembleRelease` exige uma **URL HTTPS de produção** distinta do ambiente DEV e uma **keystore de produção**. Na máquina responsável, fornecer as cinco variáveis de ambiente:

```text
AB_PROD_API_URL=https://<host-https-da-api-prod>/
AB_RELEASE_KEYSTORE=<caminho-privado-do-arquivo.jks>
AB_RELEASE_STORE_PASSWORD=<senha-privada>
AB_RELEASE_KEY_ALIAS=<alias-oficial>
AB_RELEASE_KEY_PASSWORD=<senha-da-chave>
```

Depois executar `gradle :app:assembleRelease`, **sem incluir as senhas no histórico de shell nem em logs**. A assinatura de teste gerada em GitHub Actions é descartável, não pode ser distribuída.

Verificação com SDK Build Tools:

```bash
apksigner verify --verbose --print-certs android-app/app/build/outputs/apk/release/app-release.apk
aapt dump badging android-app/app/build/outputs/apk/release/app-release.apk
sha256sum android-app/app/build/outputs/apk/release/app-release.apk
```

**Identidade de distribuição v1.0.0 já verificada:**

- Package `com.agendabarber.app`, `versionCode=100`, `versionName=1.0.0`.
- Assinatura v2 válida; certificado `CN=Agenda Barber, O=Agenda Barber, C=BR`.
- SHA-256 certificado: `be0d7648686c2c0143bf3aa8035521da4fbfe93f4b7eabbc563193c11135a806`.
- SHA-256 APK assinado em 07/11/2026: `AD717BB8AEE8CE80B4AB4B10C3F84EBCDB794A0FE023A12B2F67FC42BE83AE66`. Este hash corresponde a **esse binário**, não às futuras versões.

Para futuras atualizações preserve o certificado privado de assinatura e aumente `versionCode`. Se a assinatura mudar, a atualização sobre a instalação oficial falhará.

## Instalação e teste em dispositivo

```bash
adb devices -l
adb -s <SERIAL_AUTORIZADO> install -r <CAMINHO_DO_APK_RELEASE>
adb -s <SERIAL_AUTORIZADO> shell monkey -p com.agendabarber.app -c android.intent.category.LAUNCHER 1
```

O dispositivo físico homologado foi um **Samsung Galaxy Tab A7 Lite SM-T220**. Em 07/11/2026 o ADB confirmou `Success` ao instalar o APK `AgendaBarber.apk` (mesmo hash oficial) sem desinstalar antes. Uma instalação/abertura via ADB **não comprova** por si só que todos os fluxos do aplicativo foram testados.

Lista mínima de homologação manual: login válido/inválido; restaurar sessão com Lembrar conta e reiniciar; criação/favoritos/remoção de cliente de teste; serviço e expediente; agendamento e prevenção de conflito; recorrência; pagamentos/devedores; estatísticas; modo offline e volta da rede; logout e revogação. Não usar clientes reais como massa de teste.

## GitHub Actions

- `.github/workflows/ci.yml`: Java verify, Checkstyle, Android ktlint/testes/lint/build.
- `.github/workflows/security.yml`: verificações de fonte e segurança.
- `.github/workflows/release-smoke.yml`: smoke release com chave efêmera de CI.
- A publicação da API na AWS e a assinatura do APK **não acontecem automaticamente** após um push ou merge.

## Operação/rollback

A implantação de API foi realizada em EC2 com Docker Compose e Caddy, separando os projetos Docker `agenda-barber-prod` (PROD, loopback 8081) e `deploy` (DEV, loopback 8080). Usar `docker compose -p agenda-barber-prod ...` ao atuar em PROD: não omitir o nome do projeto, para evitar afetar o ambiente DEV.

Mantenha a imagem/Compose anteriores para reverter o binário. Não restaurar o banco automaticamente para um backup antigo: isso pode descartar transações válidas. Consulte o [runbook](RELEASE_1_0_0_RUNBOOK.md).

## Segredos e boas práticas

Não versionar ou enviar por chat senhas, tokens, `.env`, certificados privados `.jks`, credenciais Neon, JDBC com senha ou chaves privadas JWT. Nunca desinstalar aplicativos para resolver conflito de assinatura sem antes verificar impacto e autorização; dados locais podem se perder.
