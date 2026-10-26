# Agenda Barber — Design System oficial (F00-02)

## Fonte de verdade
Mockup original com dez telas enviado em 24/10/2026 e especificado no Trello: https://trello.com/c/ZeBZlvH7/34-00-refer%C3%AAncia-visual-obrigat%C3%B3ria-10-telas-imagem-do-usu%C3%A1rio
A implementação F02 deve reproduzir a referência visual, não reinventar o layout. Os códigos abaixo são aproximações iniciais sujeitas à comparação por screenshot.

## Identidade e paleta
Estética premium de barbearia, tipografia sans-serif, fundos grafite, superfícies em camadas, texto claro e acentos dourados. UX-09 Estatísticas é a exceção CLARA (fundo branco), mantendo a barra de navegação inferior ESCURA.

| Token Kotlin | Valor HEX | Uso |
|---|---|---|
| BarberBackground | #101112 | Fundo principal de UX-01..08 e UX-10 |
| BarberSurface | #1D1F23 | Cards e campos escuros |
| BarberSurfaceRaised | #282B30 | Superfícies destacadas/controles |
| BarberOutline | #414348 | Linhas, separadores, bordas |
| BarberGold | #D9B165 | Botão primário, seleção, ícones ativos |
| BarberOnGold | #171411 | Texto/ícone em fundo dourado |
| BarberTextPrimary | #F5F5F3 | Texto principal dark |
| BarberTextSecondary | #A8A8AB | Labels, descrições, datas |
| BarberScheduled | #9A9DA4 | Status agendado |
| BarberInProgress | #83BFE5 | Status em atendimento |
| BarberCompleted | #75C8A0 | Status concluído |
| BarberStartAction | #2AAB76 | Ação iniciar atendimento |
| BarberCancelAction | #E66A71 | Cancelar/destrutivo |
| StatsBackground | #FCFDFC | Fundo da UX-09 |
| StatsSurface | #FFFFFF | Cards da UX-09 |
| StatsRevenueCard | #E3F3E9 | KPI faturamento |
| StatsCutsCard | #E6F0FD | KPI cortes |
| StatsTicketCard | #F1E8FA | KPI ticket |
| StatsCancellationsCard | #FCEAEC | KPI cancelamentos |
| StatsChartBlue | #84B9F5 | Gráficos de faturamento/serviços |

## Tipografia
Sem instalar fontes. FontFamily.SansSerif na F00-02; na F02 ajustar peso, tracking e caixa para o print.
- headlineLarge: 28sp, bold, altura 34sp
- headlineMedium: 23sp, semibold, altura 30sp
- titleLarge: 20sp, semibold, altura 27sp
- titleMedium/bodyLarge: 16sp
- bodyMedium/labelLarge: 14sp
- labelSmall: 11sp, com bom contraste

## Espaçamento, formas e componentes
Medidas-alvo iniciais em dp, conferir visualmente antes de aprovar:
- Margens laterais de conteúdo 16 a 20dp; gaps de cards 10 a 16dp.
- Cards e caixas de texto com raio 12 a 16dp, borda discreta grafite.
- Botão primário cheio dourado com rótulo escuro, raio 12dp e altura >=48dp.
- Inputs com label acima, ícone coerente, contorno discreto, placeholder cinza.
- Avatar redondo (foto opcional, iniciais como fallback); ícones simples e alinhados.
- CTA secundário contornado escuro/claro; iniciar VERDE; cancelar contorno VERMELHO.
- Seleções de dias/tabs e segmentos destacados em dourado, conteúdo não ativo grafite.
- Linha de tempo da agenda com horários alinhados à esquerda e cards à direita.

## Barra de navegação
Agenda | Clientes | botão central (+) | Estatísticas | Mais. Ícone ativo e label dourados; itens inativos discretos. O (+) é maior e abre Novo Agendamento. Em UX-09, a barra continua ESCURA mesmo com conteúdo claro.

## Tema Compose
Color.kt contém tokens; Type.kt define a hierarquia; Theme.kt expõe AgendaBarberTheme(statisticsScreen = false). Aplicar statisticsScreen=true apenas no conteúdo de UX-09. Esta etapa não substitui MainActivity nem adiciona telas navegáveis.

## Acessibilidade e fidelidade
Área de toque >=48dp, contraste e TalkBack, Dynamic Type/fontScale, teclado sem sobreposição, conteúdo rolável em telas menores, estado offline legível. Evitar dependência de fotos remotas; placeholders coerentes com mockup.
Não alegar pixel-perfect até cotejar captura Android x imagem original na F02. A foto original precisa permanecer acessível para essa conferência.
