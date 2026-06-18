# Guia de Deploy

Aplicação **Spring Boot 3 + Java 17 + Thymeleaf + Bootstrap 5 + JPA**. Em desenvolvimento usa H2 em arquivo; em produção, PostgreSQL. Expõe a porta **3000**.

Este guia cobre execução local, Docker, ambiente de produção, smoke-test e rollback.

---

## 1. Execução local

### Pré-requisitos

- **JDK 17** (Temurin recomendado).
- **Maven 3.9+** (ou use o wrapper `./mvnw` em `teste/`).
- Token do Telegram (BotFather) e ao menos um `chat_id`.

### Variáveis mínimas

Crie `/workspaces/telegram/teste/.env`:

```dotenv
TELEGRAM_BOT_TOKEN=123456:ABCDEF...
TELEGRAM_CHAT_IDS=7648003850
APP_USERNAME=admin
APP_PASSWORD=admin123
```

### Subir

```bash
./start.sh           # foreground
./start.sh bg        # background, log em start.log, PID em start.pid
```

O `start.sh` faz `mvn clean package` se não houver `target/teste-0.0.1-SNAPSHOT.jar` e depois roda `java -jar`.

Acesse [http://localhost:3000/login](http://localhost:3000/login).

---

## 2. Execução com Docker

```bash
docker compose up -d --build
docker compose logs -f app
```

O `docker-compose.yml` sobe dois serviços: `app` (Spring Boot) e `db` (PostgreSQL 16).

### Exemplo completo de `.env` (raiz do projeto)

```dotenv
# --- Telegram ---
TELEGRAM_BOT_TOKEN=123456:ABCDEF...
TELEGRAM_CHAT_IDS=7648003850,1122334455
TELEGRAM_WEBHOOK_URL=
TELEGRAM_WEBHOOK_ENABLED=false

# --- Segurança UI ---
APP_USERNAME=admin
APP_PASSWORD=trocar-em-producao

# --- Banco (produção / Docker) ---
DATABASE_URL=jdbc:postgresql://db:5432/botdb
DATABASE_USER=postgres
DATABASE_PASSWORD=postgres
SPRING_PROFILES_ACTIVE=prod

# --- Monitor Gmail (Urgência Renal) ---
GMAIL_ENABLED=true
GMAIL_IMAP_HOST=imap.gmail.com
GMAIL_IMAP_PORT=993
GMAIL_USER=rafaelioppi@gmail.com
GMAIL_APP_PASSWORD=abcdefghijklmnop
GMAIL_SUBJECT_FILTER=Notificação de novo e-mail – Urgência Renal
GMAIL_FIXED_RATE=300000
```

### Tabela completa de variáveis

| Variável | Obrigatório? | Padrão | Descrição |
|---|---|---|---|
| `TELEGRAM_BOT_TOKEN` | **Sim** | `changeme` | Token do BotFather. |
| `TELEGRAM_CHAT_IDS` | **Sim** | `0` | IDs autorizados, separados por vírgula. |
| `TELEGRAM_WEBHOOK_URL` | Não | vazio | URL pública HTTPS para receber updates. |
| `TELEGRAM_WEBHOOK_ENABLED` | Não | `false` | Liga modo webhook (senão usa polling). |
| `APP_USERNAME` | Não | `admin` | Usuário do `/login`. |
| `APP_PASSWORD` | Não | `admin123` | Senha do `/login`. **Trocar em produção.** |
| `SPRING_PROFILES_ACTIVE` | Não | `dev` (H2) | Em produção use `prod` para ativar PostgreSQL. |
| `DATABASE_URL` | Em prod | — | Ex.: `jdbc:postgresql://db:5432/botdb`. |
| `DATABASE_USER` | Em prod | — | Usuário Postgres. |
| `DATABASE_PASSWORD` | Em prod | — | Senha Postgres. |
| `GMAIL_ENABLED` | Não | `true` | Liga/desliga monitor Gmail. |
| `GMAIL_IMAP_HOST` | Não | `imap.gmail.com` | Host IMAP. |
| `GMAIL_IMAP_PORT` | Não | `993` | Porta IMAP SSL. |
| `GMAIL_USER` | Se Gmail on | — | E-mail monitorado. |
| `GMAIL_APP_PASSWORD` | Se Gmail on | — | App Password de 16 chars, sem espaços. |
| `GMAIL_SUBJECT_FILTER` | Não | `Notificação de novo e-mail – Urgência Renal` | Substring no assunto. |
| `GMAIL_FIXED_RATE` | Não | `300000` | Intervalo de varredura (ms). |

### Monitores adicionais (configurados em `application.properties`)

| Variável (properties) | Padrão | Descrição |
|---|---|---|
| `defesacivil.enabled` | `true` | Liga monitor da Defesa Civil RS. |
| `defesacivil.fixedRate` | `600000` (10 min) | Intervalo de varredura. |
| `inmet.enabled` | `true` | Liga monitor INMET. |
| `inmet.fixedRate` | `1800000` (30 min) | Intervalo de varredura. |

Para sobrescrever em runtime, exporte como variável Java, ex.: `-Ddefesacivil.enabled=false` ou via `SPRING_APPLICATION_JSON`.

---

## 3. Produção

### PostgreSQL

Use `SPRING_PROFILES_ACTIVE=prod` para carregar `application-prod.properties`. As três variáveis `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` são obrigatórias nesse perfil.

### Volume persistente

O Compose monta `./data:/app/data`. Se houver arquivos de estado fora do banco (ex.: `ultimo_assunto.txt` gerado pelo monitor da Defesa Civil), garanta que estejam em `/app/data` para sobreviver a redeploys. Verifique antes do upgrade:

```bash
docker compose exec app ls -l /app/data
```

### TLS / proxy

A aplicação não termina TLS — use Nginx, Traefik ou Caddy na frente, encaminhando para `app:3000`.

### Webhook Telegram

Se usar `TELEGRAM_WEBHOOK_ENABLED=true`, exponha `https://seu-dominio/telegram/webhook` e registre no Bot:

```bash
curl -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/setWebhook" \
  -d "url=https://seu-dominio/telegram/webhook"
```

---

## 4. Smoke-test pós-deploy

1. **Login** — `curl -i http://localhost:3000/login` deve retornar 200.
2. **Autenticar** no navegador com `APP_USERNAME` / `APP_PASSWORD` e abrir `/dashboard`.
3. **Painel "Fontes de Monitoramento"** — verifique que cada monitor habilitado aparece como **ativo**:
   - Defesa Civil RS
   - INMET
   - Gmail — Urgência Renal
4. **Teste Telegram** — em **Ações Rápidas**, dispare uma mensagem manual e confirme que chega aos `TELEGRAM_CHAT_IDS`.
5. **Teste Gmail** — envie e-mail para `GMAIL_USER` com o assunto filtrado; em até 5 min o alerta deve chegar ao Telegram.
6. **Logs** — `docker compose logs -f app` (ou `tail -f start.log`) sem stacktrace recorrente.

---

## 5. Rollback

```bash
# 1. Identifique o commit estável anterior
git log --oneline -n 10

# 2. Reverta (gera um novo commit, mais seguro que reset)
git revert <hash-quebrado>
git push

# 3. Redeploy
docker compose up -d --build
# ou local:
./start.sh bg
```

Se a regressão for em schema do banco em produção, restaure dump do Postgres antes do redeploy:

```bash
docker compose exec -T db pg_restore -U postgres -d botdb < backup.dump
```

---

## 6. CI

Workflow em `.github/workflows/CI - Spring Boot Gmail Monitor.yml` roda a cada 15 minutos via cron e em pushes. Sirva como canário básico de build/start.
