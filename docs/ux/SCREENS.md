# Agenda Barber — Catálogo visual de dez telas (F00-02)

**Referência obrigatória:** prancha original fornecida pelo usuário em 24/10/2026. Cartão Trello: https://trello.com/c/ZeBZlvH7/34-00-refer%C3%AAncia-visual-obrigat%C3%B3ria-10-telas-imagem-do-usu%C3%A1rio
A UX é exclusiva do barbeiro, sem login/agendamento de clientes externos. Componentes devem seguir enquadramento, cores, proporções e ordem da imagem, validados depois por screenshots Android.

## UX-01 | Login (F02-09)
Primeira tela superior. Fundo foto de barbearia/cliente recebendo corte em camada escura; ícone de barbearia e marca BARBER AGENDA, slogan "Sua agenda, seus resultados". Formulário E-mail, Senha com olho de visibilidade, botão dourado Entrar e link Esqueci minha senha. Sem bottom-nav. Estados: teclado e insets, validação, erro de login, loading, falta de rede. Somente o profissional pode autenticar.

## UX-02 | Agenda do Dia (F02-06)
Segunda tela superior. Fundo dark, saudação "Olá, [barbeiro]!", data/ícone calendário; faixa horizontal de dias com dia selecionado dourado, seletor "Hoje | Semana | Mês"; timeline vertical com horários à esquerda e agendamentos à direita. Card com foto/iniciais, nome, serviço, duração, chips Concluído verde, Em atendimento azul, Agendado cinza. Bottom-nav com Agenda ativa. Tocar card abre UX-03; (+) abre UX-04. Estados: agenda vazia, offline Room com última sincronização, conflitos e carregamento.

## UX-03 | Detalhes do Agendamento (F02-07)
Terceira tela superior. Toolbar voltar; avatar circular, nome/telefone, ícone ligar; guias "Detalhes | Histórico" com indicador dourado. Linhas com ícones para data, horário/duração, serviço, valor e observações. Na base botões Iniciar atendimento VERDE, Editar agendamento contorno claro e Cancelar agendamento contorno VERMELHO. Ações sensíveis exigem confirmação do servidor, cancelamento pede confirmação; status de atendimento muda ações disponíveis.

## UX-04 | Novo Agendamento (F02-07)
Quarta tela superior. Toolbar voltar/título "Novo Agendamento"; formulário dark com campos na ordem exata: Cliente com busca e (+) dourado, Data, Horário, Serviço, Valor (R$), Observações(opcional). CTA dourado "Salvar agendamento". Cliente (+) abre UX-05. Serviço sugere duração/preço, mas dados finais obedecem regras de negócio. 409 de conflito informa horário ocupado e oferece nova seleção. Offline não anuncia gravação concluída.

## UX-05 | Novo Cliente (F02-08)
Quinta tela superior. Toolbar voltar, título; círculo para foto com câmera e ação "Adicionar foto"; entradas Nome completo, Telefone/WhatsApp, E-mail(opcional), Data de nascimento(opcional), Observações(opcional), botão dourado "Salvar cliente". Respeitar campos opcionais, máscara/validação de telefone, teclado, permissões de imagem quando usuário solicitar e retorno com cliente selecionado ao UX-04.

## UX-06 | Clientes (F02-10)
Primeira tela inferior. Cabeçalho "Clientes" e ícone menu; busca, filtros "Todos | Recentes | Favoritos" (ativo dourado); lista com avatar, nome, "Último atendimento: [data]", contagem de atendimentos e estrela de favorito. Bottom-nav com Clientes ativa. Pesquisa e filtros reais; toque abre detalhes/histórico, estrela alterna favorito; estados vazios quando filtro não encontra dados.

## UX-07 | Serviços (F02-11)
Segunda tela inferior. Voltar, título "Serviços", ação "+ Novo" dourada. Cards grafite alinhados contendo ícone redondo à esquerda, nome, duração (45 min, 1h), preço BRL e menu de três pontos à direita. Exemplos visuais: Corte Degradê, Corte Social, Corte + Barba, Barba, Sobrancelha, Corte Infantil. Ações criar/editar/desativar. Catálogo somente do barbeiro, com dados reais da API futuramente.

## UX-08 | Bloquear Horário (F02-12)
Terceira tela inferior. Voltar/fechar, título "Bloquear Horário"; alternador "Único | Recorrente" com ativo dourado; Data, Horário de início, Horário de fim lado a lado, Motivo dropdown, Observações(opcional), CTA "Bloquear horário". Bloqueios não podem contrariar reservas vigentes; datas exibidas em America/Sao_Paulo. Bloqueio recorrente apresenta regra compreensível e oferece edição/remoção futura.

## UX-09 | Estatísticas (F02-13, integração F03-03)
Quarta tela inferior: tema DARK/grafite como as demais abas (alteração solicitada pelo usuário em 28/10/2026 para corrigir contraste e legibilidade). Título "Estatísticas", seletor de mês, grade 2x2 de KPIs pastéis: Faturamento (verde), Cortes realizados (azul), Ticket médio (lilás), Cancelamentos (rosa). Gráfico de barras azuis "Faturamento por dia" e ranking de "Serviços mais realizados" em barras horizontais azuis. Bottom-nav ESCURA com Estatísticas ativa dourada. Nunca contar agendamentos futuros como receita. Lucro líquido estimado pode estar em detalhe financeiro, quando implementado.

## UX-10 | Mais (F02-14)
Quinta tela inferior. Título "Mais"; cartão de perfil com foto, nome e papel "Barbeiro". Lista dark com ícones/setas na ordem: Perfil, Horário de funcionamento, Notificações (toggle dourado), Backup e sincronização, Tema (Automático), Ajuda e suporte, Sobre o app; na base "Sair" com borda vermelha. Bottom-nav Mais ativa dourada. Todo item deve executar uma ação ou exibir indisponibilidade explícita, sem promessas falsas de backup.

## Observações comuns
- A navegação padrão é Agenda | Clientes | (+) | Estatísticas | Mais; o botão central não vira nova aba.
- Inputs recebem validação, estado disabled/loading e feedback; nada de botões de faz de conta.
- Evitar usar foto real de pessoas sem licença/permissão; imagem de referência é especificação, não licença dos ativos.
- Navegação e teclado devem funcionar em Android com fontes ampliadas, tela pequena e TalkBack.
- Offline no MVP: exibir dados previamente sincronizados via Room com "Última sincronização", sem confirmar escritas.
- Nesta F00-02 apenas documentamos e estabelecemos os tokens de tema; a implementação integral fica nas tarefas F02.
