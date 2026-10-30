# F00-02 — Plano de aceite UX e validação por screenshots

## Pré-condições
Fonte: prancha original de 10 telas enviada em 24/10/2026 e documentada no cartão https://trello.com/c/ZeBZlvH7/34-00-refer%C3%AAncia-visual-obrigat%C3%B3ria-10-telas-imagem-do-usu%C3%A1rio
Na F02 anexar ou manter o mockup original acessível para conferir lado a lado. Cores de Color.kt são valores iniciais aproximados até a comparação visual.

## Matriz UX → implementação e evidências
| Ordem | Tela | Task F02 | Evidência de aprovação |
|---|---|---|---|
| 01 | Login | F02-09 | Foto dark, campos, logo, Entrar, recuperar senha |
| 02 | Agenda | F02-06 | Dias, Hoje/Semana/Mês, timeline, cards, chips, nav |
| 03 | Detalhes | F02-07 | Avatar, guias, ícones, botões verde/claro/vermelho |
| 04 | Novo Agendamento | F02-07 | Campos ordenados, buscar/+, CTA dourado |
| 05 | Novo Cliente | F02-08 | Foto opcional, nome, telefone, opcionais e CTA |
| 06 | Clientes | F02-10 | Busca, filtros, estrela, última visita, nav |
| 07 | Serviços | F02-11 | Lista de cards com duração, valor e + Novo |
| 08 | Bloquear Horário | F02-12 | Único/Recorrente, data, intervalos, motivo, CTA |
| 09 | Estatísticas | F02-13; dados F03-03 | Fundo grafite, texto branco/cinza legível, KPIs escuros com detalhes dourados/coloridos, nav escura |
| 10 | Mais | F02-14 | Perfil e 7 opções na ordem, Sair vermelho, nav |

## Critérios de fidelidade
- [ ] Screenshots Android das 10 telas comparados à referência em tamanho equivalente (sem esticar imagens).
- [ ] Cores, contraste, posição das áreas, proporções, tipografia e raio de bordas coerentes com print.
- [ ] Bottom-nav Agenda | Clientes | + central | Estatísticas | Mais; destaque dourado nas abas.
- [ ] Estatísticas DARK como as demais telas; barra inferior DARK, títulos e KPIs de alto contraste.
- [ ] Avatares, ícones, chips, campos e CTAs na ordem e posição da referência.
- [ ] Os botões nunca são apenas decorativos: navegam, executam a ação ou mostram indisponibilidade explicada.

## Estados obrigatórios, por tela aplicável
- [ ] Loading sem deslocar radicalmente a composição da tela.
- [ ] Conteúdo vazio com explicação e CTA adequado.
- [ ] Erro HTTP/conexão: feedback legível e retry apenas de operação segura.
- [ ] Offline com dados cacheados Room e timestamp de última sincronização visível.
- [ ] Offline sem cache: estado vazio distinto de agenda vazia.
- [ ] Escrita sem confirmação: nunca apresentar "salvo" como sucesso.
- [ ] Formulários com required/opcional, máscara e validação, tela rolável com teclado aberto.
- [ ] Sessão expirada com retorno ao login; nenhuma chave do Neon dentro do APK.

## Dispositivos e acessibilidade
- [ ] Aparelho Android físico ou emulador; comparar captura em dimensões padronizadas e validar telas pequenas.
- [ ] FontScale >=1.3 sem corte de conteúdo essencial; ajustes responsivos de altura.
- [ ] Áreas de toque acessíveis (objetivo >=48dp), descrições TalkBack para ícones.
- [ ] Contraste de textos e ícones verificável; mensagens não dependem somente de cor.
- [ ] Teclado não oculta o campo focado nem o botão primário; suportar insets.
- [ ] Navegação Voltar não descarta edição sem aviso.

## Limite de aceite nesta fase
F00-02 entrega especificação visual, matriz UX/F02 e três arquivos de tema Kotlin. Ainda NÃO entrega telas navegáveis, screenshots aprovados, integração de dados ou APK. A revisão visual final fica em F02-15.
Nenhuma instalação, commit, push ou alteração de Trello foi autorizada nesta execução.
