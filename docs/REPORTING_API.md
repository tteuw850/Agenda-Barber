# F03-02 — API de relatórios e indicadores

Os endpoints exigem `Authorization: Bearer <JWT>` no perfil `neon`, usando exclusivamente o `barber_id` assinado no token. Não há entrada de `barberId` na URL ou parâmetros.

| GET | Retorno |
| --- | --- |
| `/api/v1/reports/dashboard?from=2030-04-01&to=2030-04-30` | Resumo, série diária e serviços populares (top 10) |
| `/api/v1/reports/summary?from=...&to=...` | KPIs agregados |
| `/api/v1/reports/daily?from=...&to=...` | Série por data local, incluindo dias zerados |
| `/api/v1/reports/popular-services?from=...&to=...&limit=10` | Serviços concluídos por contagem (limite de 1 a 50) |

- Datas são inclusivas, ISO `yyyy-MM-dd`, exigidas nos endpoints. Máximo **366 dias** por requisição; parâmetros inválidos retornam **400 ProblemDetail**.
- Atendimentos são selecionados por `completed_at` no fuso cadastrado do barbeiro, intervalo de instantes `[início, fim)`. Não são considerados agendamentos futuros, cancelados, ausências nem serviços ainda em execução.
- Faturamento é **soma de `received_amount` conhecido**, nunca do `price_snapshot`. Atendimentos concluídos anteriores à F03-01 com `received_amount = NULL` contam em `completedAppointments` e `missingReceipts`, mas não fabricam recebimento.
- `averageTicket = revenue / receiptedAppointments` (2 casas, HALF_UP), evitando distorção pelo legado com valor desconhecido. Recebimento de R$0,00 é válido e conta no denominador. Sem recibos conhecidos, ticket é R$0,00.
- `haircuts`: concluídos associados a serviços atualmente classificados como `is_haircut=true`. Alterar essa classificação altera contagens históricas: um snapshot histórico dessa flag não existe no esquema atual.
- Despesas são agrupadas por `occurred_on` e apenas `deleted_at IS NULL` entra no total. `estimatedProfit = revenue - expenses`: não é lucro contábil/fiscal.
- Serviços populares usam o nome snapshot de cada agendamento; renomeações podem resultar em linhas distintas para o mesmo serviço. A classificação é por quantidade concluída, com desempate determinístico.
- Não há migração nova. Requer esquema versionado até V7 da F03-01, **sem executar nada no Neon**.
- A UI de Estatísticas e seus estados (UX-09) serão ligados aos dados na F03-03, sem alterações de layout nesta etapa.
