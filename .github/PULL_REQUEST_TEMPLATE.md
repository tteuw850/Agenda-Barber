## Objetivo e Trello
<!-- Informe o cartão (ex.: F00-03) e o motivo da alteração. -->

## Arquivos alterados
<!-- Resuma as mudanças principais e seus efeitos. -->

## Qualidade
- [ ] `mvn -f backend/pom.xml verify` (JUnit + Checkstyle).
- [ ] `gradle :app:ktlintCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` em `android-app/`.
- [ ] Falhas ou verificações não executadas estão justificadas.

## Segurança e privacidade
- [ ] Nenhum token, senha, string Neon real, JWT ou keystore incluído.
- [ ] Novos logs não imprimem credenciais ou dados de clientes.
- [ ] Dependências e permissões extras foram justificadas.

## Interface (se aplicável)
- [ ] Referência UX-01 até UX-10 respeitada.
- [ ] Capturas de tela e estados loading/offline/erro avaliados.

## Evidências e pendências
<!-- Resultado dos testes, prints, limitações. -->

## Aprovação de publicação
- [ ] Modificações autorizadas no chat antes do commit/push.