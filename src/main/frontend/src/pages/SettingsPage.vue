<template>
  <div class="settingsPage">
    <div id="toastLayerSettings" class="toastLayer"></div>
    <div class="card settingsCard">
      <header class="topbar">
        <div class="brand">
          <div class="logoDot"></div>
          <div class="brandText">
            <div class="title">设置</div>
            <div class="subtitle">连接 / 个人资料 / 开关</div>
          </div>
        </div>
        <div class="topbarRight">
          <a class="btn ghost" href="index.html">返回</a>
        </div>
      </header>

      <main class="settingsGrid">
        <section class="panel">
          <div class="panelTitleRow">
            <div class="panelTitle">后端连接</div>
            <div class="panelHint">主机/端口</div>
          </div>

          <label class="field">
            <span class="fieldLabel">主机 Host</span>
            <input id="sHost" class="input" v-model="prefs.host" type="text" placeholder="localhost 或域名"/>
          </label>

          <label class="field">
            <span class="fieldLabel">端口 Port</span>
            <input id="sPort" class="input" v-model.number="prefs.port" type="number" min="1" step="1" placeholder="13337"/>
          </label>

          <div class="row">
            <div class="help inline">修改即自动保存；若出错可点右侧恢复默认。</div>
            <button id="sResetConn" class="btn ghost" @click="resetConn">恢复默认</button>
          </div>

          <div class="help">
            建议：如果你用 https 打开页面，WebSocket 需要 wss；本项目默认 http + ws。
          </div>
        </section>

        <section class="panel">
          <div class="panelTitleRow">
            <div class="panelTitle">个人资料</div>
            <div class="panelHint">昵称/会话</div>
          </div>

          <label class="field">
            <span class="fieldLabel">昵称（连接后自动设置）</span>
            <input id="sNick" class="input" v-model="prefs.nick" type="text" placeholder="旅人XXXX / 自定义"/>
          </label>

          <label class="field">
            <span class="fieldLabel">固定会话 ID（可选，不填则自动分配）</span>
            <input id="sSession" class="input" v-model="prefs.desiredSessionId" type="text" placeholder="例如 S10086"/>
          </label>

          <div class="row">
            <div class="help inline">改动即刻生效，下次连接会使用；可随时清空。</div>
            <button id="sClearProfile" class="btn ghost" @click="clearProfile">清空</button>
          </div>

          <div class="help">
            注意：在线对局中不能改会话 ID；改会话会在下次连接时生效。
          </div>
        </section>

        <section class="panel">
          <div class="panelTitleRow">
            <div class="panelTitle">体验开关</div>
            <div class="panelHint">勾选框</div>
          </div>

          <label class="toggle">
            <input id="sAutoReconnect" type="checkbox" v-model="prefs.autoReconnect"/>
            <span class="toggleUI"></span>
            <span class="toggleText">
            <span class="toggleTitle">自动重连</span>
            <span class="toggleSub">断线后自动重试连接（避免重试风暴）</span>
          </span>
          </label>

          <label class="toggle">
            <input id="sAutoConnect" type="checkbox" v-model="prefs.autoConnect"/>
            <span class="toggleUI"></span>
            <span class="toggleText">
            <span class="toggleTitle">进入主页自动连接</span>
            <span class="toggleSub">打开 index.html 自动发起连接</span>
          </span>
          </label>

          <label class="toggle">
            <input id="sImpactFx" type="checkbox" v-model="prefs.impactFx"/>
            <span class="toggleUI"></span>
            <span class="toggleText">
            <span class="toggleTitle">碰撞特效</span>
            <span class="toggleSub">中间能量线冲击波效果</span>
          </span>
          </label>

          <label class="toggle">
            <input id="sReduceMotion" type="checkbox" v-model="prefs.reduceMotion"/>
            <span class="toggleUI"></span>
            <span class="toggleText">
            <span class="toggleTitle">减少动画</span>
            <span class="toggleSub">更“静态”，适合录屏/低性能</span>
          </span>
          </label>

          <label class="toggle">
            <input id="sDefaultExpandArena" type="checkbox" v-model="prefs.defaultExpandArena"/>
            <span class="toggleUI"></span>
            <span class="toggleText">
            <span class="toggleTitle">默认放大战场</span>
            <span class="toggleSub">进入页面时自动聚焦战场区域</span>
          </span>
          </label>

          <label class="toggle">
            <input id="sDimOthers" type="checkbox" v-model="prefs.dimOthers"/>
            <span class="toggleUI"></span>
            <span class="toggleText">
            <span class="toggleTitle">放大战场时弱化其他面板</span>
            <span class="toggleSub">聚焦对战时淡出操作/在线/日志</span>
          </span>
          </label>

          <div class="help">所有勾选变更会自动保存，无需手动保存。</div>
        </section>
      </main>
    </div>
  </div>
</template>

<script setup>
import { reactive, watch } from "vue";
let saveNoticeTimer;

const PREF_KEY_BASE = "emperor_prefs";
const PREF_DEFAULTS = {
  host: window.location.host || window.location.hostname || "localhost",
  port: "",
  nick: "",
  desiredSessionId: "",
  autoReconnect: true,
  autoConnect: false,
  impactFx: true,
  reduceMotion: false,
  defaultExpandArena: false,
  dimOthers: true
};

function currentUserKey(){
  const raw = (localStorage.getItem("emperor_user") || "").trim();
  return raw || "guest";
}
function prefsKey(){
  return `${PREF_KEY_BASE}:${currentUserKey()}`;
}
function loadPrefs(){
  try{
    return { ...PREF_DEFAULTS, ...(JSON.parse(localStorage.getItem(prefsKey()) || "{}")) };
  }catch{
    return { ...PREF_DEFAULTS };
  }
}
function savePrefs(p){
  localStorage.setItem(prefsKey(), JSON.stringify(p));
}

function toast(title, msg = "", tone = "info"){
  const layer = document.getElementById("toastLayerSettings") || document.body;
  const div = document.createElement("div");
  div.className = `toast ${tone}`;
  div.innerHTML = `<div class="toastInner"><div class="toastTitle">${title}</div><div class="toastMsg">${msg}</div></div>`;
  layer.appendChild(div);
  setTimeout(() => div.remove(), 3200);
}

function notifySaved(){
  if (saveNoticeTimer) clearTimeout(saveNoticeTimer);
  saveNoticeTimer = setTimeout(() => {
    toast("已保存更改", "设置已更新", "success");
    saveNoticeTimer = null;
  }, 300);
}

const prefs = reactive(loadPrefs());

watch(prefs, (v) => {
  savePrefs(v);
  document.body.classList.toggle("reduceMotion", !!v.reduceMotion);
  notifySaved();
}, { deep: true });

function resetConn(){
  prefs.host = PREF_DEFAULTS.host;
  prefs.port = PREF_DEFAULTS.port;
  toast("已重置", "主机/端口已恢复默认", "info");
}
function clearProfile(){
  prefs.nick = "";
  prefs.desiredSessionId = "";
  toast("已清空", "昵称和会话ID已清除", "info");
}
</script>

<style scoped>
.settingsPage{
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 12px 48px;
}
.settingsCard{
  width: min(1200px, 100%);
  border-radius: 22px;
  border: 1px solid var(--stroke);
  background: var(--panel);
  box-shadow: var(--shadow);
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.topbar{
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:16px;
  margin-bottom: 8px;
}
.brand{ display:flex; align-items:center; gap:12px; }
.logoDot{
  width:14px;height:14px;border-radius:999px;
  background: linear-gradient(135deg, var(--a), var(--a2));
  box-shadow: 0 0 0 6px rgba(108,178,255,0.12);
}
.brandText .title{ font-weight:800; letter-spacing:-0.02em; }
.brandText .subtitle{ color:var(--muted); font-size: 13px; margin-top:2px; }
.topbarRight{ display:flex; align-items:center; gap:12px; }
</style>
