# F03-03 — Indicadores financeiros na UX-09

- A tela Estatísticas mantém o fundo branco, quatro KPIs, gráfico azul, ranking e navegação dark/dourada do mockup oficial. O seletor de mês usa limites inclusivos (dia 1 até último dia); a tela não contém dados fictícios por padrão.
- A fonte é `GET /api/v1/reports/dashboard?from=YYYY-MM-DD&to=YYYY-MM-DD`, autenticada pelo token. O backend calcula faturamento real, cortes, despesas, lucro estimado, ticket médio, cancelamentos por eventos de cancelamento e serviços concluídos no fuso do barbeiro.
- Cancelamentos aparecem no quarto KPI sem alterar a composição visual. Despesas e lucro estimado são mostrados na faixa de informações já existente (não acrescentam cards nem modificam o layout do mockup).
- Valores monetários permanecem `BigDecimal`; os dias do gráfico representam `daily[].date`; o ranking usa `popularServices[].completedAppointments`. Não somar agendamento futuro/cancelado à receita.
- Períodos sem movimentação retornam zeros reais; carregamento, sessão expirada, erro e botão Atualizar usam estados explícitos. Demonstração é opcional e sempre identificada como fictícia.
- Cache offline de **somente leitura** reutiliza `agenda_snapshots` do Room v1, sem migração. Chave SHA-256 por token e período evita compartilhamento entre usuários; somente falhas de rede e HTTP 503 permitem leitura do snapshot, com data da última sincronização. HTTP 401/403 não exibe dado cacheado. Logout usa o `AgendaRepository.clear()` existente, que limpa a mesma tabela.
- Sem alterações no Neon, sem migrações Flyway e sem instalação local. A revisão visual das telas continua separada.
