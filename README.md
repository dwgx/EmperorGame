# EmperorGame

> 皇帝牌对战 — 基于 Java WebSocket + HTTP 的实时卡牌对战游戏，配 Vue 3 前端。
> A real-time card battle game built with Java WebSocket + HTTP, with a Vue 3 frontend.

## Overview / 概述

EmperorGame is a browser-playable real-time card battle game. The backend is a single Java process providing both WebSocket game service and an embedded HTTP server for static assets and a JSON API. The frontend is Vue 3 + Vite, compiled to static pages served directly by the embedded HTTP server or deployable behind Nginx/Apache. Gameplay revolves around counter relationships between role cards: Emperor / Citizen / Slave / Traitor / Madman.

EmperorGame 是一个可在浏览器中运行的实时卡牌对战小游戏。后端为单体 Java 应用，同时提供 WebSocket 对战服务与内置 HTTP 静态资源/接口服务；前端使用 Vue 3 + Vite 构建，产物为静态页面，可由内置 HTTP 服务直接托管，也可单独部署。玩法围绕「皇帝 / 平民 / 奴隶 / 叛徒 / 疯子」等角色卡的克制关系展开。

## Features / 功能

- **Real-time battles** — Online matching and gameplay via [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket). Queue up with `match_online`, get paired into a `GameRoom`, lock cards and broadcast results with `play_online`.
- **Account system** — `/api/register` and `/api/login` endpoints returning `accountId` + signed `token` (HMAC-SHA256, 24h expiry, stored in `session_tokens`).
- **Embedded HTTP server** — JDK built-in HttpServer serving `/api/*` (POST only) and static assets, with CORS echoing Origin.
- **MySQL persistence** — Users, registration logs, login logs, and session tokens stored in MySQL; tables auto-created on startup.
- **Single-player AI** — Play against the computer via `AiGame`.
- **Deck modes** — `standard` (1 Emperor / 4 Citizens / 1 Slave / 1 Traitor / 1 Madman) and `random` (randomized counts). Toggle with console command `/set randomcard on|off` or env var `DECK_MODE`.
- **Optional WSS** — Enable WebSocket over TLS via keystore environment variables.
- **CI** — GitHub Actions builds backend (Maven) and frontend (npm) separately.

---

- **实时对战** — 基于 Java-WebSocket 的在线匹配与对局，`match_online` 排队配对，`play_online` 锁牌判定并广播结果。
- **账户系统** — 注册/登录接口，返回 `accountId` 与签名 `token`（HMAC-SHA256，24 小时有效）。
- **内置 HTTP 服务** — JDK HttpServer 提供 `/api/*` 接口与静态资源托管，CORS 回显 Origin。
- **数据持久化** — MySQL 存储用户、注册日志、登录日志与会话 token，启动时自动建表。
- **单人练习** — 包含对战 AI（`AiGame`），可与电脑对局。
- **牌堆模式** — `standard` 与 `random` 两种模式，通过控制台命令或环境变量切换。
- **可选 WSS** — 支持通过 keystore 环境变量启用 WebSocket over TLS。
- **CI** — GitHub Actions 分别构建后端（Maven）与前端（npm）。

## Tech Stack / 技术栈

**Backend / 后端:**
- Java 17 (Maven, `maven-shade-plugin` fat jar)
- Java-WebSocket `1.6.0`
- Gson `2.14.0`
- MySQL Connector/J `8.4.0`

**Frontend / 前端:**
- Vue `^3.4.21`
- Vite `^5.1.0` + `@vitejs/plugin-vue`

**Database / 数据库:** MySQL (`utf8mb4`)

## Project Structure / 项目结构

```
EmperorGame/
├── pom.xml                      # Maven build config (fat jar, main class: work.emperor.Main)
├── db_schema.sql                # MySQL schema script
├── gamelogic.md                 # Architecture notes & game rules
├── LICENSE                      # MIT
├── .github/workflows/ci.yml     # Backend + Frontend CI
├── EZ/                          # Tutorial screenshots (1-5.png)
└── src/main/
    ├── java/work/emperor/
    │   ├── Main.java            # Entry: WS server + embedded HTTP + console commands
    │   ├── manager/             # Game logic: GameRoom, RoomManager, Deck, Card, AiGame...
    │   ├── model/               # PlayerContext, Status
    │   └── util/                # Database, ServerLogger
    ├── resources/icons/         # Role SVG icons
    ├── frontend/                # Vue 3 + Vite source
    │   ├── src/App.vue
    │   ├── src/pages/           # LoginPage / RulesPage / SettingsPage
    │   └── vite.config.js       # Build output → ../web
    └── web/                     # Pre-built static pages (frontend build output)
```

## Getting Started / 快速开始

### Prerequisites / 环境要求
- JDK 17+
- Maven 3.8+
- MySQL (or compatible)
- Node.js 20+ & npm (only if rebuilding the frontend)

### Database / 数据库

Run the schema script, or just let the backend auto-create tables on startup:

可以先执行建表脚本，也可以直接依赖后端启动时自动建表：

```bash
mysql -u root -p < db_schema.sql
```

Configure connection via environment variables:

通过环境变量配置连接：

| Variable | Default |
| --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/emperorgame?useSSL=false&serverTimezone=UTC` |
| `DB_USER` | `root` |
| `DB_PASS` | (empty) |

Auto-created tables: `users` / `registration_log` / `login_log` / `session_tokens`

### Build & Run / 构建与运行

```bash
mvn clean package
java -jar target/EmperorGame-1.0-SNAPSHOT-shaded.jar

# Custom ports / 自定义端口:
# WS_PORT=80 HTTP_PORT=81 java -jar target/EmperorGame-1.0-SNAPSHOT-shaded.jar
```

Default ports / 默认端口:
- WebSocket: `ws://localhost:13337`
- HTTP / Static / API: `http://localhost:13338`

> Run the `-shaded` jar; the plain jar has no main manifest.
> 请运行带 `-shaded` 后缀的 jar，否则会提示 "no main manifest"。

### Rebuild Frontend / 前端重建 (optional)

Only needed if you modify `src/main/frontend`:

仅当修改了前端源码时需要：

```bash
cd src/main/frontend
npm install
npm run build   # Output goes to src/main/web, served by embedded HTTP
```

## Usage / 使用方法

1. Open `http://localhost:13338` in a browser.
2. Register / login. The frontend caches `accountId` and `token` in localStorage.
3. WebSocket connects to `ws(s)://<host>/ws/` based on current page origin, then sends `set_profile`.
4. Match online, lock cards, wait for both players ready, server judges and broadcasts result.

### API (POST JSON)

- `POST /api/register` — `{ nickname, password }`
- `POST /api/login` — `{ nickname, password }`
- Response: `{ ok: boolean, message: string, accountId?: string, token?: string }`

### Console Commands / 控制台命令

- `/set randomcard on|off` — Toggle deck mode (random / standard).

## Configuration / 配置

| Variable | Purpose / 用途 |
| --- | --- |
| `WS_PORT` | WebSocket port (default 13337) |
| `HTTP_PORT` | HTTP/API port (default 13338) |
| `DB_URL` / `DB_USER` / `DB_PASS` | MySQL connection |
| `DECK_MODE` | Deck mode: `random` / `standard` (also `-Ddeck.mode=`) |
| `AUTH_SECRET` | Token signing key (HMAC) <!-- TODO: confirm fallback behavior when unset --> |
| `ALLOWED_API_ORIGINS` | Allowed API origins (CORS) |
| `WS_SSL_KEYSTORE` / `WS_SSL_PASSWORD` / `WS_SSL_KEY_PASSWORD` / `WS_SSL_PORT` | Enable WSS |

All can be overridden via JVM system properties (e.g. `ws.ssl.keystore`).

所有变量均可通过对应 JVM 系统属性覆盖。

## Deployment / 部署

Recommended: same-origin reverse proxy — `/` for static, `/api/` → HTTP port, `/ws/` → WS port; use WSS under HTTPS. Full Nginx example in [`gamelogic.md`](gamelogic.md).

推荐同域反向代理部署，详细 Nginx 配置示例见 [`gamelogic.md`](gamelogic.md)。

## Game Rules / 玩法规则

- **Counter chain / 克制链:** Emperor > Citizen > Slave > Emperor. Traitor only beats Emperor, loses to everything else. Madman forces a draw and triggers a random event.
- **Win condition / 胜负:** Emperor loses one hand → game over. Slave defeats Emperor → instant win. Game ends when deck is exhausted.
- **Reveal rounds / 明牌回合:** Traitor / Madman triggers immediate card reveal next round.

Full rules in [`gamelogic.md`](gamelogic.md).

## Status / 状态

Personal project, functional and actively evolving. Version `1.0-SNAPSHOT` (backend), `0.1.0` (frontend).

个人项目，功能可用，持续演进中。

## License / 许可证

MIT — see [`LICENSE`](LICENSE).
<!-- TODO: LICENSE copyright line is blank ("Copyright (c) 2025"), author name can be added -->
