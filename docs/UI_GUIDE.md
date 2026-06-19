# Guia do Painel de Controle

> **Antes** o painel era um formulário simples para enviar mensagens manuais e ver um histórico bruto. **Agora** o `/dashboard` é uma central operacional: cartões com estatísticas em tempo real, status de cada fonte de monitoramento, ações rápidas e a lista dos alertas recentes. Você passa a ver, na mesma tela, "o que está ligado", "o que aconteceu nas últimas horas" e "o que posso disparar agora".

Acesse: [http://localhost:2500/dashboard](http://localhost:2500/dashboard) (após login em `/login`).

---

## 1. Cartões de estatísticas (topo)

Linha de cards numéricos com indicadores agregados.

| Cartão | O que mostra |
|---|---|
| **Alertas hoje** | Quantidade de alertas registrados desde 00:00. |
| **Alertas 7 dias** | Volume da última semana, para enxergar tendência. |
| **Fontes ativas** | Quantos monitores estão habilitados no momento (ex.: 3 de 3). |
| **Chats configurados** | Total de chat IDs em `TELEGRAM_CHAT_IDS` recebendo alertas. |

Os números refletem o estado em memória + banco no momento do carregamento da página.

---

## 2. Fontes de Monitoramento

Painel central com uma linha por fonte:

- **Defesa Civil RS** — varredura a cada 10 min (`defesacivil.fixedRate=600000`).
- **INMET** — varredura a cada 30 min (`inmet.fixedRate=1800000`).
- **Gmail — Urgência Renal** — varredura a cada 5 min (`gmail.fixedRate=300000`).

Cada linha exibe:

- **Status**: `ativo` (verde) ou `inativo` (cinza), conforme as flags `*.enabled`.
- **Intervalo**: período do agendador para aquela fonte.
- **Último ciclo**: horário da última varredura bem-sucedida.

Se uma fonte que você espera ver "ativa" aparece "inativa", revise as variáveis de ambiente correspondentes (ver `DEPLOYMENT.md`).

---

## 3. Ações Rápidas

Botões para tarefas operacionais comuns:

- **Enviar mensagem manual** — abre formulário (`/`) para enviar texto livre ao Telegram.
- **Ver histórico completo** — leva para `/historico`, com todos os alertas armazenados.
- **Gerenciar chats** — atalho para `/chats`, lista e configuração dos `TELEGRAM_CHAT_IDS`.
- **Inspecionar Defesa Civil** — abre `/defesacivil` com a última leitura bruta da fonte (útil para debug).

Os botões são confirmados via diálogo inline antes de qualquer ação que dispare mensagem real.

---

## 4. Alertas Recentes

Tabela com os últimos alertas (em ordem cronológica decrescente):

| Coluna | Descrição |
|---|---|
| **Horário** | Timestamp local. |
| **Fonte** | Defesa Civil, INMET ou Gmail. |
| **Nível** | Baixo / Médio / Alto / Crítico (campo `NivelAlerta`). |
| **Resumo** | Primeira linha da mensagem encaminhada. |

Clique numa linha para ver o conteúdo completo em modal.

---

## 5. Cabeçalho e navegação

- **Logo / título** — volta para `/dashboard`.
- **Menu** — links para `Dashboard`, `Histórico`, `Chats`, `Defesa Civil`, `Sair`.
- **Indicador de usuário logado** — mostra `APP_USERNAME`.

---

## 6. Dicas

- O dashboard **não** faz polling em tempo real — recarregue a página (ou use F5) para atualizar os cards.
- Se "Fontes ativas" mostrar menos do que você esperava, é a primeira pista para investigar antes dos logs.
- Em telas pequenas (Bootstrap 5 responsivo), os cards empilham na vertical mantendo todos os controles acessíveis.
