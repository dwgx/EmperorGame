<template>
  <div class="authPage">
    <div id="toastLayerMain" class="toastLayer"></div>
    <div class="card authCard">
      <div class="header">
        <div class="titleRow">
          <div class="logoDot"></div>
          <div>
            <div class="title">EmperorGame 登录 / 注册</div>
            <div class="subtitle">个人站，请妥善保管密码</div>
          </div>
        </div>
        <div class="authTabs">
          <button :class="{ active: tab === 'login' }" type="button" @click="setTab('login')">登录</button>
          <button :class="{ active: tab === 'register' }" type="button" @click="setTab('register')">注册</button>
        </div>
      </div>

      <div class="paneWrap">
        <div class="pane" :class="{ active: tab === 'login' }">
          <h3>登录</h3>
          <div class="hint">无需复杂认证，但会记录 IP 以便风控</div>
          <form @submit.prevent="submitLogin">
            <div class="field">
              <label class="label" for="loginNick">昵称</label>
              <input class="input" id="loginNick" v-model="login.nickname" placeholder="输入你的昵称" required />
            </div>
            <div class="field">
              <label class="label" for="loginPass">密码</label>
              <input class="input" id="loginPass" v-model="login.password" type="password" placeholder="输入密码" required />
            </div>
            <div class="row">
              <button class="btn" type="submit">登录</button>
            </div>
          </form>
          <div class="alert warn">
            提醒：本站为个人站，请勿填写与其他平台相同的密码；密码会被记录为安全哈希，仍建议使用独立密码。
          </div>
        </div>

        <div class="pane" :class="{ active: tab === 'register' }">
          <h3>注册</h3>
          <div class="hint">同一 IP 一天内仅允许注册一次</div>
          <form @submit.prevent="submitRegister">
            <div class="field">
              <label class="label" for="regNick">昵称</label>
              <input class="input" id="regNick" v-model="register.nickname" placeholder="例如：旅人XXXX（可随机）" required />
            </div>
            <div class="field">
              <label class="label" for="regPass">密码</label>
              <input class="input" id="regPass" v-model="register.password" type="password" placeholder="设置一个独立密码" required />
            </div>
            <div class="row">
              <button class="btn" type="submit">注册</button>
              <button class="btn ghost" type="button" @click="NeteastrandomNick">随机昵称</button>
            </div>
          </form>
          <div class="alert bad">
            温馨提示：
            <ul style="margin:6px 0 0 14px; padding:0;">
              <li>若同一 IP 24 小时内重复注册，后端应拒绝。</li>
              <li>昵称将被记录，违规定内容可能导致账户删除。</li>
              <li>勿在本站填写你在其他平台使用的密码。</li>
            </ul>
          </div>
        </div>
      </div>

      <div class="subtitle mono footer">DB: EmperorGame @ 127.0.0.1:3306</div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from "vue";

const MAX_NICK = 32;
const MAX_PASS = 64;

const tab = ref("login");
const login = ref({ nickname: "", password: "" });
const register = ref({ nickname: "", password: "" });
const status = reactive({
  login: { msg: "", tone: "muted" },
  register: { msg: "", tone: "muted" }
});

const heads = ["狂笑的","暴躁的","社恐的","内向的","摸鱼的","勇敢的","碎嘴的","怕黑的","唱情歌的","打字的","修仙的","顶风作案的","社会牛的","软萌的","吐槽的"];
const middles = ["蛇将","修勾","牛头人","蘑菇骑士","木头人","海胆王","卷猫侠","帝王","法师","刺客","吟游诗人","菜刀","键盘侠","爆米花","滑雪面具","咸鱼","矿工","火枪","小黑","弹幕侠"];
const tails = ["写代码","喝奶茶","打lol","追剧","吃西瓜","数星星","吹牛","打坐","念诗","吃鸡","吃土","飞车","做梦","唱rap","打雪仗","拍脑袋","围观","吹口哨"];

function setTab(mode) {
  tab.value = mode;
}

function NeteastrandomNick() {
  const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
  const base = pick(heads) + pick(middles) + (Math.random() > 0.25 ? pick(tails) : "");
  const name = base + (Math.random() > 0.3 ? Math.floor(1 + Math.random() * 99) : "");
  register.value.nickname = name;
  if (!login.value.nickname) login.value.nickname = name;
}

function rememberUser(nickname, password, accountId, token) {
  if (nickname) localStorage.setItem("emperor_user", nickname.trim());
  if (password) localStorage.setItem("emperor_pass", password);
  if (accountId) localStorage.setItem("emperor_account", accountId);
  if (token) localStorage.setItem("emperor_token", token);
}

function toast(title, msg = "", tone = "info") {
  const layer = document.getElementById("toastLayerMain") || document.getElementById("toastLayer") || document.body;
  const div = document.createElement("div");
  div.className = `toast ${tone}`;
  div.innerHTML = `<div class="toastInner"><div class="toastTitle">${title}</div><div class="toastMsg">${msg}</div></div>`;
  layer.appendChild(div);
  setTimeout(() => div.remove(), 3200);
}

function sanitize(text, max) {
  if (typeof text !== "string") return "";
  const trimmed = text.trim().slice(0, max);
  // 去除控制字符，避免注入/脏数据
  return trimmed.replace(/[\u0000-\u001F\u007F]/g, "");
}

function setStatus(which, msg, ok) {
  status[which].msg = msg || "";
  status[which].tone = ok ? "good" : "bad";
}

function translateError(raw) {
  const msg = (raw || "").toString();
  const lower = msg.toLowerCase();
  if (lower.includes("不存在") || lower.includes("未注册")) return "未注册/昵称不存在";
  if (lower.includes("已存在") || lower.includes("使用")) return "昵称已存在";
  if (lower.includes("密码") && lower.includes("错")) return "账户昵称或密码错误";
  if (lower.includes("ip") && lower.includes("注册")) return "同 IP 当天仅允许注册一次";
  if (lower.includes("在线") && lower.includes("登录")) return "该账户已在线，不能重复登录";
  return msg || "请求失败";
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

async function submitLogin() {
  const nickname = sanitize(login.value.nickname, MAX_NICK);
  const password = sanitize(login.value.password, MAX_PASS);
  if (!nickname || !password) {
    setStatus("login", "请输入昵称和密码", false);
    return toast("提示", "请填写完整信息", "warn");
  }
  try {
    const res = await callApi("/api/login", { nickname, password });
    rememberUser(nickname, password, res.accountId, res.token);
    setStatus("login", res.message || "登录成功", true);
    toast("登录成功", `欢迎回来 ${nickname}`, "success");
    window.location.href = "index.html";
  } catch (err) {
    const msg = translateError(err?.message);
    setStatus("login", msg, false);
    toast("登录失败", msg, "error");
  }
}

async function submitRegister() {
  const nickname = sanitize(register.value.nickname, MAX_NICK);
  const password = sanitize(register.value.password, MAX_PASS);
  if (!nickname || !password) {
    setStatus("register", "请输入昵称和密码", false);
    return toast("提示", "请填写完整信息", "warn");
  }
  try {
    const res = await callApi("/api/register", { nickname, password });
    rememberUser(nickname, password, res.accountId, res.token);
    setStatus("register", res.message || "注册成功", true);
    toast("注册成功", `已自动登录，欢迎 ${nickname}`, "success");
    window.location.href = "index.html";
  } catch (err) {
    const msg = translateError(err?.message);
    setStatus("register", msg, false);
    toast("注册失败", msg, "error");
  }
}

// 初始化本地缓存
(() => {
  const u = localStorage.getItem("emperor_user");
  const p = localStorage.getItem("emperor_pass");
  if (u) {
    login.value.nickname = u;
    register.value.nickname = u;
  }
  if (p) {
    login.value.password = p;
    register.value.password = p;
  }
  if (u && p) tab.value = "login";
})();

</script>

<style scoped>
.authPage{
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 12px 48px;
  background: transparent;
}
.authCard{
  width: min(960px, 100%);
  border-radius: 22px;
  border: 1px solid var(--stroke);
  background: var(--panel);
  box-shadow: var(--shadow);
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.header{
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:10px;
}
.titleRow{ display:flex; align-items:center; gap:10px; }
.logoDot{
  width:16px; height:16px; border-radius:999px;
  background:linear-gradient(135deg, var(--a), var(--a2));
  box-shadow:0 0 0 6px rgba(108,178,255,0.12);
}
.title{ font-weight:900; letter-spacing:-0.01em; }
.subtitle{ color:var(--muted); font-size:13px; }
.authTabs{
  display:inline-flex;
  border:1px solid var(--stroke);
  border-radius:14px;
  overflow:hidden;
  background:rgba(255,255,255,0.04);
}
.authTabs button{
  border:none;
  background:transparent;
  color:var(--text);
  padding:10px 14px;
  font-weight:900;
  cursor:pointer;
  transition: background var(--t), color var(--t);
}
.authTabs button.active{
  background:linear-gradient(135deg, rgba(108,178,255,0.28), rgba(138,199,255,0.18));
  color:#061022;
}
.paneWrap{
  display:grid;
  grid-template-columns: 1fr 1fr;
  gap:14px;
}
@media (max-width: 820px){
  .paneWrap{ grid-template-columns: 1fr; }
}
.pane{
  background: rgba(255,255,255,0.04);
  border:1px solid var(--stroke);
  border-radius:16px;
  padding:14px;
  box-shadow: var(--shadow);
  opacity:0;
  transform: translateY(8px);
  transition: opacity var(--t), transform var(--t);
  display:none;
}
.pane.active{
  display:block;
  opacity:1;
  transform: translateY(0);
}
.pane h3{ margin:0 0 6px 0; font-weight:900; }
.pane .hint{ color:var(--muted); font-size:13px; margin-bottom:10px; }
.field{ display:flex; flex-direction:column; gap:6px; margin-bottom:10px; }
.label{ color:var(--muted); font-size:13px; }
.input{
  padding:12px 12px;
  border-radius:12px;
  border:1px solid var(--stroke);
  background:rgba(0,0,0,0.18);
  color:var(--text);
  outline:none;
}
.input:focus{
  border-color: rgba(108,178,255,0.35);
  box-shadow:0 0 0 4px rgba(108,178,255,0.12);
}
.btn{
  border:none;
  cursor:pointer;
  font-weight:900;
  color:#061022;
  padding:10px 14px;
  border-radius:12px;
  background:linear-gradient(135deg, var(--a), var(--a2));
  transition: transform 160ms ease, box-shadow 160ms ease, opacity 160ms ease;
}
.btn:hover{ transform: translateY(-1px); box-shadow:0 12px 30px rgba(108,178,255,0.18); }
.btn.ghost{
  background: rgba(255,255,255,0.06);
  color: var(--text);
  border: 1px solid var(--stroke);
}
.row{ display:flex; gap:8px; flex-wrap:wrap; align-items:center; }
.alert{
  border-radius:12px;
  padding:10px 12px;
  border:1px solid rgba(255,255,255,0.12);
  background: rgba(0,0,0,0.22);
  font-size:13px;
  line-height:1.5;
  margin-top:10px;
}
.alert.warn{ border-color: rgba(247,201,72,0.45); }
.alert.bad{ border-color: rgba(255,107,125,0.45); }
.footer{ text-align:right; }
</style>
