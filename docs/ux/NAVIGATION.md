# Agenda Barber — Navegação UX (F00-02)

## Fluxo principal após autenticação
A barra inferior da referência possui cinco posições: Agenda | Clientes | (+) | Estatísticas | Mais.
- Agenda: UX-02, destino inicial, exibe calendário dia/semana/mês.
- Clientes: UX-06, pesquisa, recentes, favoritos e ficha.
- (+) central: abre formulário UX-04 Novo Agendamento; não é aba de destino.
- Estatísticas: UX-09, conteúdo CLARO com navegação inferior ESCURA.
- Mais: UX-10, perfil, horário, notificações, backup/sync, tema, ajuda e sair.
Clientes e Mais são módulos auxiliares do barbeiro; não são acesso público para clientes.

## Rotas conceituais propostas para F02
login -> UX-01; agenda -> UX-02; appointments/{id} -> UX-03;
appointments/new -> UX-04; clients/new -> UX-05; clients -> UX-06;
services -> UX-07; blocked-times/new -> UX-08;
statistics -> UX-09; more -> UX-10.
Subtelas secundárias (editar perfil, serviços, histórico, despesas) devem existir apenas quando exigidas pela funcionalidade; manter o padrão visual da referência.

## Ações e retorno
- UX-01 Entrar autenticado -> UX-02; falha permanece na UX-01.
- UX-02 tocar atendimento -> UX-03; clicar no (+) -> UX-04.
- UX-03 Voltar -> UX-02; Editar -> formulário preenchido; Cancelar requer confirmação.
- UX-04 pesquisar cliente -> selecionar UX-06 em modo seleção; (+) do campo -> UX-05.
- UX-05 salvar -> retorna ao UX-04 com cliente selecionado quando aberto de lá.
- UX-06 selecionar cliente -> ficha/histórico; estrela alterna favorito.
- UX-07 serviços abre pelo Mais ou pelo seletor do formulário, sem novo item de bottom nav.
- UX-08 bloquear horário -> atualizar disponibilidade e voltar à Agenda.
- UX-09 filtros mês/período atualizam cards e gráficos sem mudar para dark.
- UX-10 Perfil/Horário/Backup/Tema/Suporte/Sair abrem ações ou telas correspondentes.

## Barras e comportamento Android
UX-01 login sem bottom bar. UX-02, UX-06, UX-09 e UX-10 mostram bottom bar. UX-03, UX-04, UX-05, UX-07 e UX-08 exibem toolbar de retorno como no mockup. Conservar seleção e posição de rolagem quando voltar. Botão físico/gesto Android deve voltar sem descartar alterações silenciosamente.

## Estados transversais
- Loading: indicador sobre a região que carrega, preservando hierarquia visual.
- Vazio: mensagem/CTA dentro do card ou timeline, sem áreas fantasmas.
- Offline: agenda Room já sincronizada em modo somente leitura, selo offline e última sincronização; ações de escrita não são confirmadas.
- Erro: mensagem acionável e retry somente em leitura segura.
- Teclado: campos mantêm foco, scroll/insets evitam cobertura do CTA; validar touch targets.
- Sessão expirada: redirecionar ao login sem expor tokens; formulário não deve simular salvamento.

## Restrições
Nenhuma conta de cliente, reserva pública, marketplace ou tela de pagamento online. Backend Java / Neon são acessados apenas por API autenticada; documento define UX, não a implementação de dados.
