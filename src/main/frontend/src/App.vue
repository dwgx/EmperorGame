<template>
  <div class="shell" :class="{ 'arena-focused': ui.arenaExpanded }">
    <div id="toastLayerMain" class="toastLayer">
      <div v-for="t in toasts" :key="t.id" class="toast" :class="t.tone">
        <div class="toastInner">
          <div class="toastTitle">{{ t.title }}</div>
          <div class="toastMsg">{{ t.msg }}</div>
        </div>
      </div>
    </div>

    <header class="topbar">
      <div class="brand">
        <div class="logoDot" :class="connClass"></div>
        <div class="brandText">
          <div class="title">EmperorGame</div>
          <div class="subtitle">在线匹配 / 人机对局</div>
        </div>
      </div>

      <div class="topbarRight">
        <div class="modePills" role="tablist" aria-label="模式切换">
          <button class="pill" :class="{ active: state.mode === 'online' }" @click="setMode('online')" role="tab" aria-selected="true">在线对局</button>
          <button class="pill" :class="{ active: state.mode === 'ai' }" @click="setMode('ai')" role="tab" aria-selected="false">人机对局</button>
        </div>
        <a class="btn ghost" href="rules.html" title="规则">规则</a>
        <a class="btn ghost" href="settings.html" title="设置">设置</a>
        <a class="btn ghost" href="login.html" title="登录/切换账号">登录/切换账号</a>
      </div>
    </header>

    <section class="statusRow">
      <div class="statusCard">
        <div class="statusLeft">
          <div class="dot" :class="connClass"></div>
          <div class="statusText">
            <div class="statusTitle">{{ connTitle }}</div>
            <div class="statusSub">{{ connSub }}</div>
          </div>
        </div>
        <div class="statusRight">
          <div class="kv">
            <div class="k">在线</div>
            <div class="v">{{ state.onlineCount }}</div>
          </div>
          <div class="kv">
            <div class="k">会话</div>
            <div class="v mono">{{ state.sessionId }}</div>
          </div>
          <div class="kv">
            <div class="k">昵称</div>
            <div class="v">{{ state.nickname }}</div>
          </div>
          <div class="actions">
            <button class="btn" @click="connect()">连接</button>
            <button class="btn ghost" @click="disconnect(true)">断开</button>
          </div>
        </div>
      </div>
    </section>

    <div class="tipBar">
      <div class="tipText">
        提示：前端为无权限状态机，逻辑仅靠 WebSocket `online_round` 广播推进；操作前请先连接并完成匹配。弱网可考虑内网/代理，加速滚动节奏与设置页保持同步。
      </div>
    </div>

    <main class="mainGrid">
      <section class="panel opPanel">
        <div class="panelTitleRow">
          <div class="panelTitle">操作</div>
          <div class="panelHint" id="opHint">{{ hintText }}</div>
        </div>
        <div class="opGrid">
          <button class="btn" @click="startMatch">开始匹配</button>
          <button class="btn" @click="startAi">人机对局</button>
          <button class="btn ghost" @click="leaveRoom">离开房间</button>
          <button class="btn ghost" @click="resetGame">重置</button>
        </div>
        <div class="noteBox">
          <div class="noteTitle">提示</div>
          <div class="noteText">规则：皇帝 ＞ 平民 ＞ 奴隶 ＞ 皇帝；叛徒顶权，疯子同归于尽。在线需双方出牌，详情见右上角规则。</div>
        </div>
      </section>

      <section class="panel arenaPanel" :class="{ expanded: ui.arenaExpanded }">
        <div class="arenaHeader">
          <div class="roundPill">第 {{ state.round || '—' }} 回合</div>
          <div class="scoreRow">
            <div class="scoreChip">
              <span class="name">{{ opponentName }}</span>
              <span class="score">{{ state.scores.opp }}</span>
            </div>
            <div class="scoreChip self">
              <span class="name">{{ selfName }}</span>
              <span class="score">{{ state.scores.self }}</span>
            </div>
            <button class="btn ghost tiny" @click="toggleArena" title="放大/收起战场">{{ ui.arenaExpanded ? "收起战场" : "放大战场" }}</button>
          </div>
        </div>

        <div class="arena">
          <div class="lane top">
            <div class="laneTitle">对手手牌</div>
            <div class="hand">
              <div v-for="card in orderedCards" :key="card.key" class="cardBtn disabled">
                <img class="cardIcon" :src="card.icon" :alt="card.label" />
                <div class="cardMeta">
                  <div class="cardName">{{ card.label }}</div>
                  <div class="cardDesc">{{ card.desc }}</div>
                </div>
                <div class="cardCount">x{{ opponentRemaining[card.countKey] ?? 0 }}</div>
              </div>
            </div>
          </div>

          <div class="duel">
            <div class="duelFrame">
              <div class="duelSide left">
                <div class="slotLabel">你</div>
                <div class="slot">
                  <div v-if="!state.cards.self" class="cardDesc">等待出牌</div>
                  <div v-else class="slotCard show" :class="state.cardOutcomeSelf">
                    <img class="cardIcon" :src="CARD_META[state.cards.self].icon" :alt="prettyCard(state.cards.self)" />
                    <div class="cardMeta">
                      <div class="cardName">{{ prettyCard(state.cards.self) }}</div>
                      <div class="cardDesc">{{ CARD_META[state.cards.self].desc }}</div>
                    </div>
                  </div>
                </div>
              </div>

              <div class="duelCenter" :class="clashClass">
                <div class="pulseLine"></div>
                <div class="impactRing"></div>
                <div class="resultBadge" :class="state.resultTone" ref="resultBadgeEl">{{ state.resultText }}</div>
              </div>

              <div class="duelSide right">
                <div class="slotLabel">对手</div>
                <div class="slot">
                  <div v-if="!state.cards.opp" class="cardDesc">对手出牌待揭晓</div>
                  <div v-else class="slotCard show" :class="state.cardOutcomeOpp">
                    <img class="cardIcon" :src="CARD_META[state.cards.opp].icon" :alt="prettyCard(state.cards.opp)" />
                    <div class="cardMeta">
                      <div class="cardName">{{ prettyCard(state.cards.opp) }}</div>
                      <div class="cardDesc">{{ CARD_META[state.cards.opp].desc }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="lane bottom">
            <div class="laneTitle">你的手牌（点击出牌）</div>
            <div class="hand">
              <div
                  v-for="card in orderedCards"
                  :key="card.key"
                  class="cardBtn"
                  :class="playable(card.key) ? 'playable' : 'disabled'"
                  @click="() => playable(card.key) && playCard(card.key)"
              >
                <img class="cardIcon" :src="card.icon" :alt="card.label" />
                <div class="cardMeta">
                  <div class="cardName">{{ card.label }}</div>
                  <div class="cardDesc">{{ card.desc }}</div>
                </div>
                <div class="cardCount">x{{ selfRemaining[card.countKey] ?? 0 }}</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="panel matchPanel" v-if="state.roomId">
        <div class="panelTitleRow">
          <div class="panelTitle">当前对局</div>
          <div class="panelHint">本地可见</div>
        </div>
        <div class="matchGrid">
          <div class="matchField"><span class="label">房间</span><span class="value mono">{{ state.roomId }}</span></div>
          <div class="matchField"><span class="label">模式</span><span class="value">{{ state.mode === 'online' ? '在线对局' : '人机' }}</span></div>
          <div class="matchField"><span class="label">对手</span><span class="value">{{ opponentName }}</span></div>
          <div class="matchField"><span class="label">回合</span><span class="value">{{ state.round || '-' }}</span></div>
          <div class="matchField"><span class="label">比分</span><span class="value">{{ state.scores.self }} : {{ state.scores.opp }}</span></div>
        </div>
        <div class="matchHands">
          <div class="handCol">
            <div class="handTitle">我方剩余</div>
            <div class="handChips">
              <div v-for="c in orderedCards" :key="'self-'+c.key" class="chip">{{ c.label }} x{{ selfRemaining[c.countKey] ?? 0 }}</div>
            </div>
          </div>
          <div class="handCol">
            <div class="handTitle">对手剩余</div>
            <div class="handChips">
              <div v-for="c in orderedCards" :key="'opp-'+c.key" class="chip">{{ c.label }} x{{ opponentRemaining[c.countKey] ?? 0 }}</div>
            </div>
          </div>
        </div>
      </section>

      <section class="panel onlinePanel">
        <div class="panelTitleRow">
          <div class="panelTitle">在线玩家</div>
          <div class="panelHint">{{ state.onlineCount }} 人</div>
        </div>
        <div class="onlineList">
          <div v-if="onlinePlayers.length === 0" class="onlineItem">
            <div class="statusDot idle"></div>
            <div class="name">暂无在线玩家</div>
            <div class="session">等待新连接</div>
            <div class="onlineActions"></div>
          </div>
          <div v-for="p in onlinePlayers" :key="p.sessionId" class="onlineItem">
            <div class="statusDot" :class="{ idle: p.status === 'IDLE', busy: p.status !== 'IDLE' }"></div>
            <div class="name">{{ p.nickname || '玩家' }}</div>
            <div class="session">{{ p.sessionId }}</div>
            <div class="onlineActions">
              <button class="btn tiny ghost" :disabled="p.sessionId === state.sessionId || p.status !== 'IDLE'" @click="invite(p)">邀请</button>
            </div>
          </div>
        </div>
      </section>

      <section class="panel logPanel">
        <div class="panelTitleRow">
          <div class="panelTitle">日志</div>
          <div class="panelHint">滚动查看</div>
        </div>
        <div class="logBox">
          <div v-for="(l, idx) in logs" :key="idx" class="logLine" :class="l.tone">{{ l.text }}</div>
        </div>
      </section>
    </main>

    <div class="modal" v-if="ui.matchModal">
      <div class="modalCard">
        <div class="modalTitle">匹配中…</div>
        <div class="modalSub">{{ ui.matchText || '正在寻找对手' }}</div>
        <div class="modalActions">
          <button class="btn ghost" @click="cancelMatch">退出匹配</button>
        </div>
      </div>
    </div>

    <div class="modal" v-if="ui.finalModal">
      <div class="modalCard big">
        <div class="finalTitle">{{ ui.final.title }}</div>
        <div class="finalSub">{{ ui.final.sub }}</div>
        <div class="modalActions">
          <button class="btn" @click="playAgain">再来一局</button>
          <button class="btn ghost" @click="ui.finalModal = false">关闭</button>
        </div>
      </div>
    </div>

    <div class="modal" v-if="ui.inviteModal && pendingInvite">
      <div class="modalCard">
        <div class="modalTitle">对战邀请</div>
        <div class="modalSub">来自 {{ pendingInvite.fromNickname || pendingInvite.fromSessionId }}</div>
        <div class="modalActions">
          <button class="btn" @click="replyInvite(true)">接受</button>
          <button class="btn ghost" @click="replyInvite(false)">拒绝</button>
        </div>
      </div>
    </div>

    <!-- 独立的 login/settings 页面，不再弹出 modal -->
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from "vue";

const PREF_KEY_BASE = "emperor_prefs";
const PREF_DEFAULTS = {
  // 默认沿用当前页面的 host（含端口），port 留空表示“随页面”，确保同域/WSS 不再硬编码 13337
  host: window.location.host || window.location.hostname || "localhost",
  port: "",
  nick: "",
  desiredSessionId: "",
  autoReconnect: false,
  autoConnect: false,
  impactFx: true,
  reduceMotion: false,
  defaultExpandArena: false,
  dimOthers: true
};

const CARD_META = {
  EMPEROR: { label: "皇帝", icon: "/icons/emperor.svg", desc: "压制平民，但会被奴隶反杀", countKey: "emperor" },
  CITIZEN: { label: "平民", icon: "/icons/citizen.svg", desc: "克制奴隶，但输给皇帝", countKey: "citizen" },
  SLAVE: { label: "奴隶", icon: "/icons/slave.svg", desc: "反杀皇帝，但输给平民", countKey: "slave" },
  TRAITOR: { label: "叛徒", icon: "/icons/traitor.svg", desc: "顶权不输皇帝，平民平局，怕奴隶", countKey: "traitor" },
  MADMAN: { label: "疯子", icon: "/icons/madman.svg", desc: "对撞同归于尽，不计胜负", countKey: "madman" }
};
const CARD_ORDER = ["EMPEROR", "CITIZEN", "SLAVE", "TRAITOR", "MADMAN"];
const DEFAULT_HAND = { emperor: 1, citizen: 4, slave: 1, traitor: 1, madman: 1 };

const prefs = reactive(loadPrefs());
const state = reactive({
  mode: "online",
  wsState: "disconnected",
  sessionId: "--",
  nickname: "--",
  onlineCount: 0,
  roomId: null,
  opponent: null,
  round: 0,
  publicRound: false,
  locked: false,
  gameOver: false,
  cards: { self: null, opp: null },
  cardOutcomeSelf: "",
  cardOutcomeOpp: "",
  resultText: "等待开始",
  resultTone: "info",
  scores: { self: 0, opp: 0 },
  remainingSelf: { ...DEFAULT_HAND },
  remainingOpp: { ...DEFAULT_HAND },
  nextRoundTimer: null
});

const ui = reactive({
  matchModal: false,
  matchText: "",
  finalModal: false,
  final: { title: "", sub: "" },
  inviteModal: false,
  settingsOpen: false,
  arenaExpanded: prefs.defaultExpandArena
});

const onlinePlayers = ref([]);
const logs = ref([]);
const toasts = ref([]);
let toastSeed = 1;
let lastConnToastAt = 0;
let heartbeatTimer = null;

const wsRef = shallowRef(null);
const reconnect = reactive({
  allowReconnect: true,
  reconnecting: false,
  delay: 500,
  timer: null,
  failures: 0
});

const pendingInvite = ref(null);
const resultBadgeEl = ref(null);

const connTitle = computed(() => {
  if (state.wsState === "connected") return "已连接";
  if (state.wsState === "connecting") return "正在连接…";
  return "未连接";
});
const connSub = computed(() => {
  if (state.wsState === "connected") return wsUrl.value;
  if (state.wsState === "connecting") return "正在握手";
  return "点击连接或在设置页开启自动连接";
});
const connClass = computed(() => {
  if (state.wsState === "connected") return "ok";
  if (state.wsState === "connecting") return "warn";
  return "bad";
});

const selfName = computed(() => state.nickname || "我");
const opponentName = computed(() => state.opponent?.nickname || "对手");
const orderedCards = computed(() => CARD_ORDER.map((k) => ({ key: k, ...CARD_META[k] })));
const selfRemaining = computed(() => state.remainingSelf || DEFAULT_HAND);
const opponentRemaining = computed(() => state.remainingOpp || DEFAULT_HAND);
const hintText = computed(() => {
  const base = state.mode === "online" ? "在线：先匹配，再出牌" : "人机：点击手牌立即结算";
  return state.publicRound ? `${base} · 本回合公开出牌` : base;
});
const wsUrl = computed(() => {
  const isHttps = window.location.protocol === "https:";
  const proto = isHttps ? "wss" : "ws";
  const hostFromPrefs = (prefs.host || "").trim();
  const host = hostFromPrefs || window.location.host || window.location.hostname || "localhost";
  const hasPort = host.includes(":");
  const portPart = !hasPort && prefs.port ? `:${prefs.port}` : "";
  return `${proto}://${host}${portPart}/ws/`;
});
const clashClass = computed(() => {
  if (state.cardOutcomeSelf === "win") return "clash-left";
  if (state.cardOutcomeSelf === "lose") return "clash-right";
  if (!state.cardOutcomeSelf && !state.cardOutcomeOpp) return "";
  return "clash-draw";
});

watch(
    () => ({ ...prefs }),
    (p) => localStorage.setItem(prefsKey(), JSON.stringify(p)),
    { deep: true }
);

watch(
    () => prefs.reduceMotion,
    (flag) => {
      document.body.classList.toggle("reduceMotion", !!flag);
    },
    { immediate: true }
);

watch(
    () => prefs.defaultExpandArena,
    (flag) => (ui.arenaExpanded = !!flag)
);

function currentUserKey() {
  const raw = (localStorage.getItem("emperor_user") || "").trim();
  return raw || "guest";
}

function prefsKey() {
  return `${PREF_KEY_BASE}:${currentUserKey()}`;
}

function inviteTsKey() {
  return `emperor_invite_ts:${currentUserKey()}`;
}

function loadPrefs() {
  try {
    const raw = JSON.parse(localStorage.getItem(prefsKey()) || "{}");
    return { ...PREF_DEFAULTS, ...raw };
  } catch {
    return { ...PREF_DEFAULTS };
  }
}

function prettyCard(key) {
  return CARD_META[key]?.label || "--";
}

function isMadmanClash(a, b) {
  return a === "MADMAN" || b === "MADMAN";
}

function applyPrefs() {
  localStorage.setItem(prefsKey(), JSON.stringify(prefs));
  document.body.classList.toggle("reduceMotion", !!prefs.reduceMotion);
  ui.arenaExpanded = !!prefs.defaultExpandArena;
  ui.settingsOpen = false;
  ensureTokenAndAccount().then(() => {
    const accountId = getAccountId();
    if (prefs.nick || accountId) {
      send({ type: "set_profile", nickname: prefs.nick, accountId, accountToken: getAccountToken() });
    }
  });
  if (!getAccountId() && prefs.desiredSessionId) {
    send({ type: "change_session", sessionId: prefs.desiredSessionId });
  }
}

function resetConn() {
  prefs.host = PREF_DEFAULTS.host;
  prefs.port = PREF_DEFAULTS.port;
}

function clearProfile() {
  prefs.nick = "";
  prefs.desiredSessionId = "";
}

function getAccountId() {
  return (localStorage.getItem("emperor_account") || "").trim();
}

function getAccountToken() {
  return (localStorage.getItem("emperor_token") || "").trim();
}

async function ensureTokenAndAccount() {
  if (getAccountToken() && getAccountId()) return;
  const user = (localStorage.getItem("emperor_user") || "").trim();
  const pass = localStorage.getItem("emperor_pass") || "";
  if (!user || !pass) return;
  try {
    const data = await callApi("/api/login", { nickname: user, password: pass });
    if (data.ok !== false) {
      if (data.accountId) localStorage.setItem("emperor_account", data.accountId);
      if (data.token) localStorage.setItem("emperor_token", data.token);
      prefs.nick = prefs.nick || user;
    }
  } catch {
    /* ignore */
  }
}

async function checkAuth() {
  const user = localStorage.getItem("emperor_user") || "";
  const pass = localStorage.getItem("emperor_pass") || "";
  if (!user || !pass) {
    window.location.href = "login.html";
    return;
  }
  try {
    // 优先使用已有 token/账号，缺失时用登录刷新
    let data;
    if (getAccountId() && getAccountToken()) {
      data = { accountId: getAccountId(), token: getAccountToken(), ok: true };
    } else {
      data = await callApi("/api/login", { nickname: user, password: pass });
      if (data.ok === false) throw new Error(data.message || "login failed");
    }
    state.nickname = user;
    if (data.accountId) localStorage.setItem("emperor_account", data.accountId);
    if (data.token) localStorage.setItem("emperor_token", data.token);
    prefs.nick = prefs.nick || user;
    if (prefs.autoConnect) connect();
  } catch {
    window.location.href = "login.html";
  }
}

const API_CANDIDATES = buildApiBases();
function buildApiBases() {
  const bases = new Set();
  const { protocol, hostname, port } = window.location;
  const stored = localStorage.getItem("emperor_api_base");
  if (stored) bases.add(stored);
  bases.add(window.location.origin);
  bases.add(`${protocol}//${hostname}:13338`);
  if (port && port !== "80" && port !== "443") {
    bases.add(`${protocol}//${hostname}`);
  }
  bases.add(`${protocol}//localhost:13338`);
  bases.add(`${protocol}//127.0.0.1:13338`);
  return Array.from(bases).filter(Boolean);
}

async function callApi(path, payload) {
  let lastErr = null;
  for (const base of API_CANDIDATES) {
    try {
      const res = await fetch(`${base}${path}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json().catch(() => null);
      if (!json || json.ok !== true) throw new Error((json && json.message) || "API 返回异常");
      localStorage.setItem("emperor_api_base", base);
      return json;
    } catch (err) {
      lastErr = err;
      if (localStorage.getItem("emperor_api_base") === base) {
        localStorage.removeItem("emperor_api_base");
      }
    }
  }
  throw lastErr || new Error("API 不可用");
}

function log(line, tone = "info") {
  logs.value.push({ text: line, tone });
  if (logs.value.length > 120) logs.value.shift();
}

function toast(title, msg = "", tone = "info") {
  const id = toastSeed++;
  toasts.value.push({ id, title, msg, tone });
  setTimeout(() => {
    toasts.value = toasts.value.filter((t) => t.id !== id);
  }, 3200);
}

function bumpBadge() {
  if (!prefs.impactFx || prefs.reduceMotion) return;
  const el = resultBadgeEl.value;
  if (!el) return;
  el.classList.remove("fxPulse");
  void el.offsetWidth;
  el.classList.add("fxPulse");
}

function connect() {
  if (state.wsState === "connecting" || state.wsState === "connected") return;
  if (reconnect.timer) {
    clearTimeout(reconnect.timer);
    reconnect.timer = null;
  }
  reconnect.allowReconnect = !!prefs.autoReconnect;
  state.wsState = "connecting";
  log(`CONNECT ${wsUrl.value}`, "muted");

  try {
    const ws = new WebSocket(wsUrl.value);
    wsRef.value = ws;

    ws.onopen = async () => {
      if (reconnect.timer) {
        clearTimeout(reconnect.timer);
        reconnect.timer = null;
      }
      state.wsState = "connected";
      reconnect.reconnecting = false;
      reconnect.delay = 500;
      reconnect.failures = 0;
      reconnect.allowReconnect = false; // 一旦连上就停止自动重连，避免循环
      log("WebSocket 已连接", "info");

      await ensureTokenAndAccount();
      const accountId = getAccountId();
      if (!accountId && prefs.desiredSessionId) send({ type: "change_session", sessionId: prefs.desiredSessionId });
      if (prefs.nick || accountId) send({ type: "set_profile", nickname: prefs.nick, accountId, accountToken: getAccountToken() });
      if (state.mode === "ai") send({ type: "start_ai" });
    };

    ws.onmessage = (e) => {
      let msg;
      try {
        msg = JSON.parse(e.data);
      } catch {
        return log("收到无法解析的消息", "warn");
      }
      handleMessage(msg);
    };

    ws.onclose = () => {
      const wasConnected = state.wsState === "connected";
      state.wsState = "disconnected";
      wsRef.value = null;
      state.roomId = null;
      state.opponent = null;
      state.publicRound = false;
      state.locked = false;
      state.gameOver = false;
      ui.matchModal = false;
      log("连接已关闭", "muted");

      if (prefs.autoReconnect && reconnect.allowReconnect) {
        scheduleReconnect(wasConnected);
      }
    };

    ws.onerror = () => {
      log("WebSocket 连接异常", "warn");
    };
  } catch (err) {
    state.wsState = "disconnected";
    log("连接失败：" + (err?.message || "未知错误"), "lose");
    if (prefs.autoReconnect) scheduleReconnect(false);
  }
}

function disconnect(manual = false) {
  if (reconnect.timer) {
    clearTimeout(reconnect.timer);
    reconnect.timer = null;
  }
  reconnect.reconnecting = false;
  reconnect.delay = 500;
  reconnect.failures = 0;
  reconnect.allowReconnect = !manual;
  if (manual) {
    prefs.autoReconnect = false;
    prefs.autoConnect = false;
  }

  if (wsRef.value) {
    try {
      wsRef.value.close();
    } catch {}
    wsRef.value = null;
  }
  state.wsState = "disconnected";
  log(manual ? "手动断开" : "已断开", "muted");
}

function scheduleReconnect(wasConnected) {
  if (reconnect.reconnecting) return;
  if (!prefs.autoReconnect) return;
  reconnect.reconnecting = true;
  reconnect.failures += 1;
  if (reconnect.failures > 3) {
    log("重连已停止（多次失败）", "warn");
    reconnect.reconnecting = false;
    return;
  }
  const jitter = Math.floor(Math.random() * 200);
  const delay = Math.min(10000, reconnect.delay + jitter);
  reconnect.delay = Math.min(10000, Math.floor(reconnect.delay * 1.6));

  toast("断线重连中…", `将在 ${delay}ms 后重试（第 ${reconnect.failures} 次）`, "warn");
  reconnect.timer = setTimeout(() => {
    reconnect.reconnecting = false;
    connect();
  }, delay);
}

function send(payload) {
  if (!wsRef.value || wsRef.value.readyState !== WebSocket.OPEN) {
    log("未连接，无法发送消息", "warn");
    return false;
  }
  wsRef.value.send(JSON.stringify(payload));
  return true;
}

function setMode(mode) {
  state.mode = mode;
  state.publicRound = false;
  if (mode === "ai") {
    connectIfNeeded(true).then(() => send({ type: "start_ai" }));
  } else {
    if (state.roomId) send({ type: "leave_room" });
    clearGame("已切回在线模式");
  }
}

async function connectIfNeeded(fromModeSwitch = false) {
  if (state.wsState === "connected") return true;
  if (state.wsState === "connecting") return false;
  if (!prefs.autoConnect && !fromModeSwitch) return false;
  connect();
  return true;
}

function startMatch() {
  if (state.wsState !== "connected") {
    toast("未连接", "请先连接服务器", "warn");
    return;
  }
  if (state.mode !== "online") setMode("online");
  send({ type: "match_online" });
}

function startAi() {
  if (state.wsState !== "connected") {
    toast("未连接", "请先连接服务器", "warn");
    return;
  }
  setMode("ai");
  send({ type: "start_ai" });
}

function leaveRoom() {
  if (state.wsState !== "connected") return;
  if (state.roomId) send({ type: "leave_room" });
  clearGame("已离开房间");
}

function resetGame() {
  if (state.wsState !== "connected") return;
  if (state.mode === "ai") {
    send({ type: "start_ai" });
  } else {
    if (state.roomId) send({ type: "leave_room" });
    clearGame("已重置，可重新匹配");
  }
}

function toggleArena() {
  ui.arenaExpanded = !ui.arenaExpanded;
}

function playable(cardKey) {
  const remaining = selfRemaining.value[cardKey.toLowerCase()] ?? 0;
  return remaining > 0 && !state.gameOver && !state.locked;
}

function playCard(cardKey) {
  if (state.wsState !== "connected") {
    toast("未连接", "请先连接服务器", "warn");
    return;
  }
  if (state.gameOver) {
    toast("本局已结束", "", "warn");
    return;
  }

  if (state.mode === "online") {
    if (!state.roomId) return toast("未进入房间", "请先匹配", "warn");
    if (state.locked) return toast("你已出牌", "等待对手", "warn");

    state.cards.self = cardKey;
    state.cardOutcomeSelf = "";
    state.resultText = "已出牌，等待对手…";
    state.resultTone = "warn";
    state.locked = true;
    send({ type: "play_online", roomId: state.roomId, card: cardKey });
  } else {
    state.cards.self = cardKey;
    state.cardOutcomeSelf = "";
    state.resultText = "你已出牌…";
    state.resultTone = "info";
    send({ type: "play_ai", card: cardKey });
  }
}

function handleMessage(msg) {
  switch (msg.type) {
    case "hello":
      return handleHello(msg);
    case "presence":
      return handlePresence(msg);
    case "matching":
      return handleMatching(msg);
    case "match_canceled":
      return handleMatchCanceled(msg);
    case "match_found":
      return handleMatchFound(msg);
    case "card_locked":
      return handleCardLocked(msg);
    case "card_revealed":
      return handleCardRevealed(msg);
    case "online_round":
      return handleOnlineRound(msg);
    case "opponent_left":
      return handleOpponentLeft(msg);
    case "ai_ready":
      return handleAiReady(msg);
    case "ai_round":
      return handleAiRound(msg);
    case "notice":
      return handleNotice(msg);
    case "invite_offer":
      return handleInviteOffer(msg);
    case "invite_result":
      return handleInviteResult(msg);
    case "invite_busy":
      return handleInviteBusy(msg);
    case "pong":
      return;
    case "error":
      toast("错误", msg.message || "未知错误", "error");
      log("错误：" + (msg.message || "未知"), "lose");
      return;
    default:
      log("未知消息：" + (msg.type || "(no type)"), "muted");
  }
}

function handleHello(msg) {
  state.sessionId = msg.sessionId || state.sessionId;
  state.nickname = msg.nickname || state.nickname || prefs.nick || "我";
  state.onlineCount = msg.online ?? state.onlineCount;
  const now = Date.now();
  if (now - lastConnToastAt > 1500) {
    toast("已连接", msg.message || "连接成功", "success");
    lastConnToastAt = now;
  }
}

function handleNotice(msg) {
  const title = msg.title || "通知";
  const body = msg.message || "";
  const tone = msg.tone || "info";
  toast(title, body, tone);
  log(`[通知] ${title}: ${body}`, tone === "success" ? "info" : tone);
}

function handlePresence(msg) {
  state.onlineCount = msg.online ?? state.onlineCount;
  if (Array.isArray(msg.players)) {
    onlinePlayers.value = msg.players.map((p) => ({
      sessionId: p.sessionId,
      nickname: p.nickname,
      status: (p.status || (p.inGame ? "BUSY" : "IDLE")).toString().toUpperCase()
    }));
  }
}

function handleMatching(msg) {
  ui.matchModal = true;
  ui.matchText = msg.message || "正在寻找对手…";
  state.resultText = "匹配中…";
  state.resultTone = "warn";
  log("匹配中…", "muted");
}

function handleMatchCanceled(msg) {
  ui.matchModal = false;
  state.resultText = msg.message || "已取消匹配";
  state.resultTone = "info";
  log("已取消匹配", "muted");
}

function handleMatchFound(msg) {
  ui.matchModal = false;
  state.roomId = msg.roomId;
  state.opponent = msg.opponent || null;
  state.round = msg.round || 1;
  state.publicRound = !!msg.publicRound;
  state.locked = false;
  state.gameOver = false;
  state.cards = { self: null, opp: null };
  state.cardOutcomeSelf = "";
  state.cardOutcomeOpp = "";

  const scores = msg.scores || {};
  state.scores.self = scores[state.sessionId] ?? 0;
  state.scores.opp = scores[state.opponent?.sessionId] ?? 0;

  const rem = msg.remaining || {};
  state.remainingSelf = rem[state.sessionId] || { ...DEFAULT_HAND };
  state.remainingOpp = rem[state.opponent?.sessionId] || { ...DEFAULT_HAND };

  state.resultText = "请选择出牌";
  state.resultTone = "info";
  log(`匹配成功：房间 ${state.roomId} | 模式 ${state.mode === 'online' ? '在线' : '人机'} | 对手 ${opponentName.value} | 比分 ${state.scores.self}:${state.scores.opp}`, "info");
}

function handleCardLocked(msg) {
  if (msg.roomId && msg.roomId !== state.roomId) return;
  state.resultText = "已出牌，等待对手…";
  state.resultTone = "warn";
}

function handleCardRevealed(msg) {
  if (msg.roomId && msg.roomId !== state.roomId) return;
  const sessionId = msg.sessionId;
  const card = msg.card;
  if (!sessionId || !card) return;
  if (sessionId === state.sessionId) {
    state.cards.self = card;
    return;
  }
  if (sessionId === state.opponent?.sessionId) {
    state.cards.opp = card;
    if (!state.locked && !state.gameOver) {
      state.resultText = "对手已公开出牌";
      state.resultTone = "warn";
    }
  }
}

function handleOnlineRound(msg) {
  if (msg.roomId && msg.roomId !== state.roomId) return;
  if (state.nextRoundTimer) {
    clearTimeout(state.nextRoundTimer);
    state.nextRoundTimer = null;
  }

  const cards = msg.cards || {};
  const scores = msg.scores || {};
  const remaining = msg.remaining || {};
  const nextRoundPublic = !!msg.nextRoundPublic;

  state.cards.self = cards[state.sessionId];
  state.cards.opp = cards[state.opponent?.sessionId];

  const madmanClash = isMadmanClash(state.cards.self, state.cards.opp);
  const winner = msg.outcome?.winner;
  let text = "本回合结束";
  let tone = "info";
  state.cardOutcomeSelf = "";
  state.cardOutcomeOpp = "";

  if (madmanClash) {
    text = "本回合同归于尽";
  } else if (winner === "DRAW") {
    text = "本回合平局";
  } else if (winner === state.sessionId) {
    text = "本回合你赢了";
    tone = "win";
    state.cardOutcomeSelf = "win";
    state.cardOutcomeOpp = "lose";
  } else {
    text = "本回合你输了";
    tone = "lose";
    state.cardOutcomeSelf = "lose";
    state.cardOutcomeOpp = "win";
  }

  state.resultText = text;
  state.resultTone = tone;
  state.scores.self = scores[state.sessionId] ?? state.scores.self;
  state.scores.opp = scores[state.opponent?.sessionId] ?? state.scores.opp;

  log(`${state.nickname} 出 ${prettyCard(state.cards.self)}，${opponentName.value} 出 ${prettyCard(state.cards.opp)} → ${text} | 回合 ${state.round || msg.round || '-'} | 比分 ${state.scores.self}:${state.scores.opp}`, tone);
  bumpBadge();

  if (msg.gameOver) {
    state.gameOver = true;
    state.publicRound = false;
    state.round = msg.round || state.round;
    showFinal(msg.finalResult, scores);
    return;
  }

  state.nextRoundTimer = setTimeout(() => {
    state.round = (msg.round || state.round) + 1;
    state.locked = false;
    state.cards = { self: null, opp: null };
    state.cardOutcomeSelf = "";
    state.cardOutcomeOpp = "";
    state.resultText = "请选择出牌";
    state.resultTone = "info";
    state.remainingSelf = remaining[state.sessionId] || state.remainingSelf;
    state.remainingOpp = remaining[state.opponent?.sessionId] || state.remainingOpp;
    state.publicRound = nextRoundPublic;
    state.nextRoundTimer = null;
  }, 1400);
}

function handleOpponentLeft(msg) {
  ui.matchModal = false;
  state.roomId = null;
  state.opponent = null;
  state.locked = false;
  state.gameOver = false;
  state.round = 0;
  state.publicRound = false;
  state.cards = { self: null, opp: null };
  state.cardOutcomeSelf = "";
  state.cardOutcomeOpp = "";
  state.resultText = msg.message || "对手离开，请重新匹配";
  state.resultTone = "warn";
  state.remainingSelf = { ...DEFAULT_HAND };
  state.remainingOpp = { ...DEFAULT_HAND };
  log(msg.message || "对手离开", "muted");
}

function handleAiReady(msg) {
  state.roomId = null;
  state.opponent = { sessionId: "AI", nickname: "电脑" };
  state.round = msg.round || 1;
  state.publicRound = false;
  state.locked = false;
  state.gameOver = false;
  state.cards = { self: null, opp: null };
  state.cardOutcomeSelf = "";
  state.cardOutcomeOpp = "";
  state.scores.self = msg.playerScore ?? 0;
  state.scores.opp = msg.opponentScore ?? 0;
  state.remainingSelf = msg.playerRemaining || { ...DEFAULT_HAND };
  state.remainingOpp = msg.opponentRemaining || { ...DEFAULT_HAND };
  state.resultText = "请选择出牌";
  state.resultTone = "info";
  log("人机对局已准备", "info");
}

function handleAiRound(msg) {
  state.cards.self = msg.playerCard;
  state.cards.opp = msg.opponentCard;
  const madmanClash = isMadmanClash(state.cards.self, state.cards.opp);
  const outcome = msg.outcome;
  state.cardOutcomeSelf = "";
  state.cardOutcomeOpp = "";

  if (madmanClash) {
    state.resultText = "本回合同归于尽";
    state.resultTone = "info";
  } else if (outcome === "WIN") {
    state.resultText = "本回合你赢了";
    state.resultTone = "win";
    state.cardOutcomeSelf = "win";
    state.cardOutcomeOpp = "lose";
  } else if (outcome === "LOSE") {
    state.resultText = "本回合你输了";
    state.resultTone = "lose";
    state.cardOutcomeSelf = "lose";
    state.cardOutcomeOpp = "win";
  } else {
    state.resultText = "本回合平局";
    state.resultTone = "info";
  }

  state.scores.self = msg.playerScore ?? state.scores.self;
  state.scores.opp = msg.opponentScore ?? state.scores.opp;
  state.remainingSelf = msg.playerRemaining || state.remainingSelf;
  state.remainingOpp = msg.opponentRemaining || state.remainingOpp;

  log(`你出 ${prettyCard(state.cards.self)}，电脑出 ${prettyCard(state.cards.opp)} → ${state.resultText}`, state.resultTone === "win" ? "win" : state.resultTone === "lose" ? "lose" : "info");
  log(`你出 ${prettyCard(state.cards.self)}，电脑出 ${prettyCard(state.cards.opp)} → ${state.resultText} | 回合 ${state.round || msg.round || '-'} | 比分 ${state.scores.self}:${state.scores.opp}`, state.resultTone === "win" ? "win" : state.resultTone === "lose" ? "lose" : "info");

  if (msg.gameOver) {
    state.gameOver = true;
    showFinal(msg.finalResult, {
      PLAYER: msg.playerScore,
      AI: msg.opponentScore,
      [state.sessionId]: msg.playerScore
    });
  } else {
    state.round = (msg.round || state.round) + 1;
  }
}

function showFinal(finalResult, scores) {
  let title = "平局";
  const my = parseInt(state.scores.self ?? 0, 10);
  const op = parseInt(state.scores.opp ?? 0, 10);
  const sub = `最终比分 ${my} : ${op}`;

  if (finalResult === "DRAW") {
    title = "平局";
  } else if (finalResult === state.sessionId || finalResult === "PLAYER") {
    title = "你赢了";
  } else {
    title = "你输了";
  }

  ui.final = { title, sub };
  ui.finalModal = true;
  state.resultText = "对局结束：" + title;
  state.resultTone = title === "你赢了" ? "win" : title === "你输了" ? "lose" : "info";
}

function handleInviteOffer(msg) {
  pendingInvite.value = {
    fromSessionId: msg.fromSessionId,
    fromNickname: msg.fromNickname || msg.fromSessionId,
    inviteId: msg.inviteId
  };
  ui.inviteModal = true;
  toast("收到邀请", pendingInvite.value.fromNickname, "info");
}

function handleInviteResult(msg) {
  if (msg.accept) {
    toast("邀请已被接受", "准备开始对战", "success");
  } else {
    toast("邀请被拒绝", msg.reason || "对方拒绝了邀请", "warn");
  }
}

function handleInviteBusy(msg) {
  toast("无法邀请", msg.message || "对方正在对局中", "warn");
}

function replyInvite(accept) {
  if (!pendingInvite.value) return;
  send({ type: "invite_reply", inviteId: pendingInvite.value.inviteId, accept });
  ui.inviteModal = false;
  pendingInvite.value = null;
}

function invite(player) {
  if (!player?.sessionId) return;
  if (!canInvite(player.sessionId)) {
    toast("邀请过于频繁", "同一玩家 1 分钟内只能邀请一次", "warn");
    return;
  }
  setInviteCooldown(player.sessionId);
  send({ type: "invite_request", targetSessionId: player.sessionId });
  toast("邀请已发送", `已邀请 ${player.nickname || player.sessionId}`, "info");
}

function getInviteCooldownMap() {
  try {
    return JSON.parse(localStorage.getItem(inviteTsKey()) || "{}");
  } catch {
    return {};
  }
}
function setInviteCooldown(targetSessionId) {
  const map = getInviteCooldownMap();
  map[targetSessionId] = Date.now();
  localStorage.setItem(inviteTsKey(), JSON.stringify(map));
}
function canInvite(targetSessionId) {
  const map = getInviteCooldownMap();
  const last = map[targetSessionId] || 0;
  return Date.now() - last > 60000;
}

function cancelMatch() {
  if (state.wsState === "connected") send({ type: "cancel_match" });
  ui.matchModal = false;
  state.resultText = "已退出匹配";
  state.resultTone = "info";
}

function refreshPresence() {
  if (state.wsState === "connected") send({ type: "ping" });
  toast("刷新在线列表", "等待服务器推送", "info");
}

function playAgain() {
  ui.finalModal = false;
  if (state.mode === "ai") {
    send({ type: "start_ai" });
  } else {
    if (state.roomId) send({ type: "leave_room" });
    clearGame("准备重新匹配");
    send({ type: "match_online" });
  }
}

function clearGame(reason) {
  state.roomId = null;
  state.opponent = null;
  state.round = 0;
  state.publicRound = false;
  state.locked = false;
  state.gameOver = false;
  state.cards = { self: null, opp: null };
  state.cardOutcomeSelf = "";
  state.cardOutcomeOpp = "";
  state.scores = { self: 0, opp: 0 };
  state.remainingSelf = { ...DEFAULT_HAND };
  state.remainingOpp = { ...DEFAULT_HAND };
  state.resultText = reason || "等待开始";
  state.resultTone = "info";
}

function clearLog() {
  logs.value = [];
}

onMounted(() => {
  checkAuth();
  clearGame("准备就绪");
  heartbeatTimer = setInterval(() => {
    if (state.wsState === "connected") send({ type: "ping" });
  }, 8000);
});

onBeforeUnmount(() => {
  if (state.nextRoundTimer) clearTimeout(state.nextRoundTimer);
  if (heartbeatTimer) clearInterval(heartbeatTimer);
  disconnect();
});
</script>
