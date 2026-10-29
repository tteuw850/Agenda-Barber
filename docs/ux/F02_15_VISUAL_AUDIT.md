# F02-15 — Auditoria visual comparativa

Referência: Mockup de App para Barbearia em Português.png, enviado em 24/10/2026, e lista Trello "UX/UI — Mockup Oficial (10 telas)".

## Ajustes feitos por inspeção estática
- [x] UX-01: incorporar F02-09 por cherry-pick; foto vertical CC BY 2.0 com gradiente, crédito em IMAGE_CREDITS.md; configuração HTTPS recolhida por padrão.
- [x] UX-02: cartões dos agendamentos claros e textos escuros contra o fundo grafite.
- [x] UX-06: opções Todos/Recentes/Favoritos no seletor segmentado grafite/dourado.
- [x] UX-08: opções Único/Recorrente segmentadas, botão fechar X e dia em português.
- [x] UX-09: ícones nos KPIs e tendências rotuladas APENAS na demonstração fictícia; valores reais aguardam F03-03.
- [x] UX-10: perfil e recursos sem backend informativos, sem backup/sync fictício.

## Testes pendentes com APK e Android real

**Todos os dez screenshots e respectivos aceites definitivos estão PENDENTES.**

| Tela | Screenshot | Fluxo | Conferir |
| --- | --- | --- | --- |
| UX-01 Login | Pendente | Pendente | Fotografia, contraste, JWT e URL HTTPS |
| UX-02 Agenda | Pendente | Pendente | Cartões claros e visão Dia/Semana/Mês |
| UX-03 Detalhes | Pendente | Pendente | Abas, telefone, iniciar, editar, cancelar |
| UX-04 Novo Agendamento | Pendente | Pendente | Teclado, scroll, cliente, serviço e valor |
| UX-05 Novo Cliente | Pendente | Pendente | Campos e avatar; foto ainda não persistida |
| UX-06 Clientes | Pendente | Pendente | Filtros, busca e favoritos |
| UX-07 Serviços | Pendente | Pendente | Ícones, preços, edição e desativação |
| UX-08 Bloqueios | Pendente | Pendente | Recorrência, datas e conflito 409 |
| UX-09 Estatísticas | Pendente | Pendente | Tema claro, gráficos e prévia identificada |
| UX-10 Mais | Pendente | Pendente | Perfil genérico, expediente e saída |

## Restrições e dependências

- CI/build não substituem screenshot ou testes de UX.
- O computador não tem Android SDK/ADB configurados e não se autorizou instalação.
- Testes autenticados ponta a ponta exigem API HTTPS e ambiente de desenvolvimento permitido. Não foram feitos.
- Nenhum merge na main, deploy, alteração Neon ou instalação local.
- D:\appeber mantido intacto, D:\appeber-review continua a referência F02-14; desenvolvimento isolado em D:\appeber-f02-15.
- As dez telas devem ser avaliadas no aparelho com fonte ampliada, teclado e resolução menor, comparando diretamente o mockup original.