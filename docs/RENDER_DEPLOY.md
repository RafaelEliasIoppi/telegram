# Deploy no Render

Guia direto para colocar o **telegram-bot-hub** rodando no [Render](https://render.com) — usa o `render.yaml` (Blueprint) na raiz do repositório. Cria automaticamente o Postgres + serviço Web a partir do `Dockerfile`.

> **Custo**: tudo no plano free do Render. O Postgres free expira em 90 dias (é só recriar). O serviço Web free "dorme" após 15 min ocioso e acorda em ~30 s no primeiro hit.

---

## 1. Pré-requisitos

- Conta no Render: https://dashboard.render.com
- Repositório deste projeto **no GitHub** (Render lê do GitHub/GitLab)
- Bot do Telegram criado e seu `TELEGRAM_BOT_TOKEN`
- Seu `chat_id` do Telegram (descubra mandando `/start` ao bot e olhando o `update` do webhook, ou via `@userinfobot`)
- **Senha de app** do Gmail: https://myaccount.google.com/apppasswords (precisa de 2FA ativa em https://myaccount.google.com/security)

---

## 2. Subir o repositório

```bash
git add render.yaml docs/RENDER_DEPLOY.md teste/src/main/resources/application-prod.properties
git commit -m "chore(render): blueprint para deploy"
git push origin main
```

---

## 3. Criar o Blueprint no Render

1. Dashboard Render → **New +** → **Blueprint**
2. Conecte o repositório GitHub que contém este projeto
3. Render lê o `render.yaml` e mostra:
   - 1 Postgres: `telegram-bot-db`
   - 1 Web Service: `telegram-bot-hub`
4. Clique **Apply**

Render começa o build a partir do `Dockerfile`. O primeiro deploy demora ~5–8 min (build Maven + push da imagem).

---

## 4. Preencher os segredos

Os env vars sensíveis estão marcados `sync: false` — você preenche manualmente no painel Render. Vá em **telegram-bot-hub → Environment**:

| Variável | Valor a colocar |
|---|---|
| `APP_USERNAME` | login do painel web (ex.: `admin`) |
| `APP_PASSWORD` | senha do painel web (use algo forte) |
| `TELEGRAM_BOT_TOKEN` | token do @BotFather |
| `TELEGRAM_CHAT_IDS` | IDs autorizados separados por vírgula (ex.: `7648003850`) |
| `GMAIL_USER` | e-mail monitorado (ex.: `rafaelioppi@gmail.com`) |
| `GMAIL_APP_PASSWORD` | senha de app do Google (sem espaços) |
| `TELEGRAM_WEBHOOK_URL` | (opcional) `https://telegram-bot-hub.onrender.com/webhook` — só se quiser receber comandos no bot |

Variáveis já preenchidas pelo Blueprint (não precisa mexer):

- `SPRING_PROFILES_ACTIVE=prod`
- `PGHOST` / `PGPORT` / `PGDATABASE` / `PGUSER` / `PGPASSWORD` — vêm do Postgres `telegram-bot-db`
- `GMAIL_ENABLED=true`, `GMAIL_IMAP_HOST=imap.gmail.com`, `GMAIL_IMAP_PORT=993`
- `GMAIL_SUBJECT_FILTER="Notificação de novo e-mail – Urgência Renal"`
- `GMAIL_FIXED_RATE=300000`
- `DEFESACIVIL_ENABLED=true`, `INMET_ENABLED=true`
- `TELEGRAM_WEBHOOK_ENABLED=false`

Após salvar os segredos, Render redeploya automaticamente.

---

## 5. Validar

1. Acesse `https://telegram-bot-hub.onrender.com/login`
2. Login com `APP_USERNAME` / `APP_PASSWORD`
3. No painel `/dashboard`, confira:
   - Status **OPERACIONAL** (verde)
   - Painel **Fontes de Monitoramento**: 3 fontes listadas como `ativo`, com a linha do **Gmail — Urgência Renal**
4. Mande um e-mail teste para a conta `GMAIL_USER` com o assunto exato `Notificação de novo e-mail – Urgência Renal`
5. Em até 10 minutos (ciclo do `MonitorScheduler`) o Telegram recebe a notificação formatada

Para acelerar o teste, clique **Verificar Agora** no painel.

---

## 6. Webhook do Telegram (opcional, para comandos)

Se você quiser que o bot responda a `/start`, `/help`, etc. via webhook:

1. Defina `TELEGRAM_WEBHOOK_ENABLED=true` e `TELEGRAM_WEBHOOK_URL=https://telegram-bot-hub.onrender.com/webhook` nos envs
2. Salve → Render redeploya
3. No painel `/bot`, clique **Registrar Webhook**

Sem webhook o bot só envia alertas (não recebe comandos) — suficiente para o caso "Urgência Renal".

---

## 7. Logs / troubleshooting

- **Logs em tempo real**: aba **Logs** do serviço no Render
- **Falha de IMAP** → revise `GMAIL_USER` e `GMAIL_APP_PASSWORD` (senha de app, 16 chars sem espaços; 2FA precisa estar ativa)
- **Postgres connection refused** → o banco demorou para subir; o serviço web tenta reconectar; aguarde o próximo restart automático
- **Filtro não casa** → confira o caractere "–" do assunto (en-dash, não hífen comum) em `GMAIL_SUBJECT_FILTER`
- **Free dorme** → o monitor não roda quando o serviço está dormindo. Para evitar isso: faça uma chamada a `/login` a cada 14 min (ex.: cron-job.org → `https://telegram-bot-hub.onrender.com/login`) ou faça upgrade para o plano Starter

---

## 8. Rollback

Aba **Deploys** do serviço → clique no deploy anterior → **Rollback**. Postgres não é tocado.
