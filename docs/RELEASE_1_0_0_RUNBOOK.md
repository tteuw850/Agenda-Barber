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
