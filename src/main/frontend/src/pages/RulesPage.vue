<template>
  <div class="rulesPage" :style="{ '--bg-angle': bgAngle + 'deg' }">
    <div class="shell">
      <header class="pageHeader">
        <div>
        <p class="eyebrow">规则 / 导览</p>
          <h1>规则导览 · 互动演示</h1>
          <p class="subtitle">左键/空格/回车/→ 下一张；右键/←/Esc 返回上一张；点击空白亦可前进。</p>
        </div>
        <div class="headerBadges">
          <span class="badge" v-if="isFlowStep(currentIndex)">流程导览</span>
          <span class="badge" v-else-if="isOverviewStep(currentIndex)">总览 · 完整牌组</span>
          <span class="badge" v-else>第 {{ currentIndex }} / {{ rulesCards.length }} 张规则卡</span>
          <a class="ghost linkBtn" href="/index.html" @click.stop>返回首页</a>
          <button class="ghost" @click.stop="skipToOverview" :disabled="isAnimating || currentIndex === overviewIndex">跳到总览</button>
        </div>
      </header>

    <section class="stageWrapper" @click="handleAdvance" @contextmenu.prevent="goPrevious">
      <div class="nebula nebulaOne"></div>
      <div class="nebula nebulaTwo"></div>
      <div class="stageBadge">
        <span v-if="isFlowStep(currentIndex)">对局流程</span>
        <span v-else-if="isOverviewStep(currentIndex)">全卡牌 · 总览</span>
        <span v-else>{{ currentCard?.title }}</span>
      </div>
      <div class="stageContainer">
        <div
          v-for="layer in activeLayers"
          :key="layer.key"
          class="stageCard"
          :class="[stageCardClasses(layer), { 'is-overview': isOverviewStep(layer.index), 'is-flow': isFlowStep(layer.index) }]"
        >
          <div class="stageCardContent">
            <template v-if="isFlowStep(layer.index)">
              <div class="flowHeader">
                <h2>对局如何展开</h2>
                <p>把整场对局想成一条分镜，先记住节奏，再记住每张牌的作用与时机。</p>
              </div>
              <div class="flowTimeline">
                <article v-for="node in flowNodes" :key="node.title" class="flowNode">
                  <div class="nodeIcon">
                    <img :src="node.icon" :alt="node.title" />
                  </div>
                  <div>
                    <h3>{{ node.title }}</h3>
                    <p>{{ node.description }}</p>
                  </div>
                </article>
              </div>
            </template>

            <template v-else-if="isOverviewStep(layer.index)">
              <div class="overviewPanel">
                <div class="overviewIntro">
                  <h2>全卡牌 + 额外资料</h2>
                  <p>快速回顾全部卡牌、观看演示、阅读常见问题与进阶提示。</p>
                  <div class="progressOutline">{{ overviewSummary }}</div>
                </div>

                <div class="overviewGridWrap">
                  <div class="overviewGrid">
                    <article v-for="card in rulesCards" :key="card.id" class="overviewCard">
                      <div class="overviewCardHead">
                        <img :src="card.icon" :alt="card.title" />
                        <h3>{{ card.title }}</h3>
                      </div>
                      <p>{{ card.what }}</p>
                      <ul>
                        <li v-for="(line, idx) in card.bullets" :key="idx">{{ line }}</li>
                      </ul>
                    </article>
                  </div>
                </div>

                <div class="detailList">
                  <article v-for="card in rulesCards" :key="card.id" class="detailRow">
                    <div class="detailTitle">
                      <img :src="card.icon" :alt="card.title" />
                      <strong>{{ card.title }}</strong>
                    </div>
                    <div class="detailBody">
                      <span class="detailTag">要做：{{ card.what }}</span>
                      <span class="detailTag">作用：{{ card.why }}</span>
                      <span v-if="card.tip" class="detailTip">提示：{{ card.tip }}</span>
                    </div>
                  </article>
                </div>

                <div class="demoSection">
                  <div class="demoHeader">
                    <div>
                  <div class="demoLabel">演示视频</div>
                  <h3>快速复盘节奏</h3>
                  <p class="demoSub">循环播放一段核心回合示例，反复看“锁牌→反应层→计分”的速度与停顿。</p>
                </div>
                <div class="demoBadges">
                  <span class="chip">循环播放</span>
                  <span class="chip">含暂停提示</span>
                  <span class="chip">可全屏</span>
                </div>
              </div>
              <div class="videoFrame" @click.stop @mousedown.stop>
                <div class="videoOverlay"></div>
                <video
                  class="demoVideo"
                  controls
                  muted
                  loop
                  playsinline
                  preload="metadata"
                  :poster="demoPoster"
                >
                  <source :src="demoVideoSrc" type="video/mp4" />
                  Your browser does not support the video tag.
                </video>
              </div>
              <p class="demoCaption">停顿时可回退查看弹幕式提示；建议跟随视频节奏在旁边摆放实体牌同步演练。</p>
            </div>

                <div class="notesSection">
                  <article v-for="note in overviewNotes" :key="note.title" class="noteBlock">
                    <h4>{{ note.title }}</h4>
                    <ul>
                      <li v-for="item in note.items" :key="item">{{ item }}</li>
                    </ul>
                  </article>
                </div>

                <div class="overviewLinks">
                  <a v-for="link in overviewLinks" :key="link.label" :href="link.href" class="pill" target="_blank" rel="noreferrer" @click.stop>
                    {{ link.label }}
                  </a>
                </div>
              </div>
            </template>

            <template v-else>
              <div class="cardHeader">
                <div class="cardMeta">
                  <div class="cardIcon">
                    <img :src="layer.card?.icon" :alt="layer.card?.title" />
                  </div>
                  <div>
                    <p class="silverLabel">操作要点</p>
                    <h2>{{ layer.card?.title }}</h2>
                  </div>
                </div>
                <div class="progressBubble">第 {{ layer.index }} / {{ rulesCards.length }} 张</div>
              </div>
              <p class="cardParagraph">{{ layer.card?.what }}</p>
              <p class="cardParagraph highlight">作用：{{ layer.card?.why }}</p>
              <ul class="cardBullets">
                <li v-for="(bullet, idx) in layer.card?.bullets" :key="idx">{{ bullet }}</li>
              </ul>
              <div v-if="layer.card?.tip" class="tipBox">
                <strong>进阶提示</strong>
                <p>{{ layer.card?.tip }}</p>
              </div>
            </template>
          </div>
        </div>
      </div>
      <div class="stageHint">{{ stageHint }}</div>
    </section>

      <div class="navigationBar">
        <button class="ghost" @click.stop="goPrevious" :disabled="isAnimating || currentIndex === 0">返回</button>
        <button
          class="primary"
          @click.stop="handleAdvance"
          :disabled="isAnimating || currentIndex === overviewIndex"
        >
          {{ currentIndex === overviewIndex ? '已在总览' : '下一步' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue';
const demoPoster = 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.jpg';
const demoFallbackMp4 = 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4';
const demoVideoSrc = computed(() => demoFallbackMp4);

const quickOutline = [
  { title: '回合节奏', desc: '锁牌 → (若触发公开则立刻亮牌) → 结算克制 → 计分 → 下一回合。' },
  { title: '克制关系（好玩版）', desc: '皇帝>平民>奴隶>皇帝；皇帝输一手=整场输；奴隶打中皇帝=整场赢；叛徒只打皇帝；疯子=随机事件平局。' },
  { title: '公开局触发', desc: '上一回合有人出了叛徒或疯子，则下一回合锁牌要立即公开，双方在亮牌状态下继续。' },
  { title: '胜负放大', desc: '皇帝输=整场输，奴隶打皇帝=整场赢；平民最安全，叛徒纯压皇帝。' },
  { title: '牌堆模式', desc: '标准：1皇帝/4平民/1奴隶/1叛徒/1疯子；随机模式（服务器开关）：1皇帝/4-7平民/1-2奴隶/1-2叛徒/1-2疯子。' }
];

const rulesCards = [
  {
    id: 'flow-open',
    title: '皇帝 · 别翻车',
    icon: '/icons/emperor.svg',
    what: '正常克制链里最强，但只要这一手输掉，直接输掉整场对局。',
    why: '皇帝的风险被放大到整场，出之前必须锁死对手没有奴隶/叛徒埋伏。',
    bullets: [
      '输这一手 = 直接输整场，慎用爆发开局。',
      '先确认对面没握奴隶；有公开信息更安全。',
      '搭配平民试探，读出对手再亮皇帝。'
    ],
    tip: '皇帝=终局开关，除非确定克制，否则不要主动交出去。'
  },
  {
    id: 'synergy',
    title: '平民 · 最安全',
    icon: '/icons/citizen.svg',
    what: '正常克制链，无副作用，就是稳。',
    why: '用来过牌、探路、拖时间，不会触发“一手定输赢”的高风险。',
    bullets: [
      '克制奴隶，被皇帝克制。',
      '最好的拖节奏与探路手段。',
      '对方高压时用平民降风险。'
    ],
    tip: '想不到路时先打平民，再根据公开信息转攻。'
  },
  {
    id: 'commit',
    title: '奴隶 · 赌命牌',
    icon: '/icons/slave.svg',
    what: '若击败皇帝：直接赢整场；若没撞到皇帝：这一局你输。',
    why: '要么爆赚终局，要么亏掉这一手，典型 all-in 玩法。',
    bullets: [
      '皇帝在场=胜利按钮；没撞到就亏这一手。',
      '需要读对手节奏，配合平民试探后再下。',
      '被平民克制最怕，但赢皇帝就直接收场。'
    ],
    tip: '先用平民诱皇帝，再立刻丢奴隶收尾；失败也只亏一手。'
  },
  {
    id: 'reaction',
    title: '叛徒 · 只盯皇帝',
    icon: '/icons/traitor.svg',
    what: '唯一效果：对手出了皇帝你赢，否则你输；不看公示、不看分数。',
    why: '纯猜拳，纯压迫皇帝心理线，逼迫对手不敢出皇帝。',
    bullets: [
      '没有额外加成，赌对皇帝才有价值。',
      '打不中皇帝就输这一手，亏节奏但能制造恐惧。',
      '不受公开/分数影响，只看皇帝是否出现。'
    ],
    tip: '用平民/假动作诱皇帝，再用叛徒博整场。'
  },
  {
    id: 'madman',
    title: '疯子 · 随机事件',
    icon: '/icons/madman.svg',
    what: '出疯子必平局，并随机触发：①交换一张牌；②各弃一张牌；③这一手作废重打（疯子消耗）。',
    why: '用随机事件打断克制链，重置节奏或偷换牌库。',
    bullets: [
      '交换：双方各随机抽一张互换，可能偷走强牌。',
      '弃牌：双方各随机弃掉一张，削弱牌库。',
      '重打：本手除疯子外不消耗，回到同一回合重出牌。'
    ],
    tip: '皇帝压力大时用疯子掀桌，赌交换/重打把节奏拉回来。'
  },
  {
    id: 'public',
    title: '公开局的压力',
    icon: '/icons/emperor.svg',
    what: '公开局 = 上回合出现叛徒/疯子，下一回合锁牌立即公开给双方看。',
    why: '没有隐藏信息的回合必须用硬克制或诱导，否则节奏被对手掌握。',
    bullets: [
      '触发条件：叛徒或疯子出场，下一回合锁牌直接亮出再结算。',
      '皇帝公开后，下一手用平民稳住，防止被奴隶反杀。',
      '公开局下可以用疯子/平民托底，让对手先犯错。'
    ],
    tip: '如果不想公开，用疯子把这一轮拖平，重置节奏再来。'
  },
  {
    id: 'score',
    title: '计分与收尾',
    icon: '/icons/citizen.svg',
    what: '回合后结算：谁赢、加几分、是否触发突然死亡。',
    why: '小分差会决定决胜局的牌序选择。',
    bullets: [
      '皇帝获胜可记双倍分，平民/奴隶拿稳健分。',
      '早点逼出叛徒，别让他在末回合爆发。',
      '疯子导致的平局不计分，但会重置局面。'
    ],
    tip: '每次计分都复述一次原因，下一回合才不会跑题。'
  },
  {
    id: 'reset',
    title: '动量重置',
    icon: '/icons/emperor.svg',
    what: '收牌、洗牌，重新回到流程分镜。',
    why: '高潮不停止，重置是下一次爆发的起点。',
    bullets: [
      '复盘流程：准备、构筑、摸牌、行动、结算、计分、重置。',
      '只在读懂对手反应后再放出强牌。',
      '想不起节奏时跳到总览页，再回到流程继续。'
    ],
    tip: '对手失误时，复制同样的节奏顺序，把优势放大。'
  }
];

const flowNodes = [
  { title: '准备 / 选模式', description: '定位皇帝，沟通桌面基调，确认玩家就位。', icon: '/icons/emperor.svg' },
  { title: '洗牌 / 构筑', description: '混洗牌组，确认各身份数量，应用额外规则。', icon: '/icons/citizen.svg' },
  { title: '摸牌阶段', description: '每名玩家补齐手牌，准备进入对撞。', icon: '/icons/slave.svg' },
  { title: '行动阶段', description: '锁牌、读信号，等待反应层。', icon: '/icons/traitor.svg' },
  { title: '结算阶段', description: '公开、应用叛徒/疯子效果，判定胜负。', icon: '/icons/madman.svg' },
  { title: '公开提醒', description: '若上一回合出现叛徒或疯子，本回合锁牌会立刻公开后再结算。', icon: '/icons/emperor.svg' },
  { title: '计分阶段', description: '记录分数、调整状态，准备下一回合或突然死亡。', icon: '/icons/emperor.svg' },
  { title: '重置 / 下一轮', description: '收牌、洗牌、喘息一秒，继续循环。', icon: '/icons/citizen.svg' }
];

const overviewNotes = [
  {
    title: '常见失误',
    items: [
      '没确认叛徒是否蓄势就亮皇帝。',
      '把平民当填充而不是节奏锚点。',
      '忘了疯子只能打和局，不计分。'
    ]
  },
  {
    title: 'FAQ（好玩版）',
    items: [
      '问：皇帝输了这一手会怎样？答：直接输整场。',
      '问：奴隶没撞到皇帝呢？答：这一手你输，但还能继续下一回合。',
      '问：叛徒怎么看？答：只管对面有没有皇帝，打不到皇帝就输这一手。',
      '问：为什么会公开？答：上一回合出现叛徒或疯子，下回合锁牌直接公开。'
    ]
  },
  {
    title: '牌堆模式',
    items: [
      '标准：1E / 4C / 1S / 1T / 1M。',
      '随机模式（服务器开关）：1E / 4-7C / 1-2S / 1-2T / 1-2M。',
      '管理员可用 /set randomcard on/off 切换。'
    ]
  },
  {
    title: '进阶提示',
    items: [
      '用慢节奏的平民遮掩，给奴隶或皇帝铺路。',
      '把流程分镜画在脑子里：箭头、关键词、牌序，保持节奏感。'
    ]
  }
];

const overviewLinks = [
  { label: '返回首页', href: '/index.html' },
  { label: '阅读完整手册', href: '/docs/manual.pdf' },
  { label: '观看教学', href: 'https://github.com/dwgx' }
];

const directionCycle = ['top', 'left', 'bottom', 'right'];
const CARD_COUNT = rulesCards.length;
const overviewIndex = CARD_COUNT + 1;
const transitionDuration = 600;
const interruptedDuration = 320;

const currentIndex = ref(0);
const transitionData = ref(null);
const isAnimating = ref(false);
const bgAngle = ref(215);
const currentCard = computed(() => getCard(currentIndex.value));
let transitionTimer;
let pendingTarget = null;

const activeLayers = computed(() => {
  if (!transitionData.value) {
    return [
      {
        key: `step-${currentIndex.value}`,
        index: currentIndex.value,
        role: 'current',
        direction: 'static',
        speed: 'normal',
        card: getCard(currentIndex.value)
      }
    ];
  }
  return [
    {
      key: `out-${transitionData.value.fromIndex}`,
      index: transitionData.value.fromIndex,
      role: 'outgoing',
      direction: transitionData.value.directionOut,
      speed: transitionData.value.speed || 'normal',
      card: getCard(transitionData.value.fromIndex)
    },
    {
      key: `in-${transitionData.value.toIndex}`,
      index: transitionData.value.toIndex,
      role: 'incoming',
      direction: transitionData.value.directionIn,
      speed: transitionData.value.speed || 'normal',
      card: getCard(transitionData.value.toIndex)
    }
  ];
});

const stageHint = computed(() => {
  if (isOverviewStep(currentIndex.value)) {
    return '所有牌已展开，先看视频与提示再进入实战。右键/Esc 返回上一张，左键/空格继续。';
  }
  if (isFlowStep(currentIndex.value)) {
    return '背熟流程后再继续：左键/空格/→ 下一张，右键/←/Esc 返回。';
  }
  return '左键/空格继续，右键/←/Esc 返回；避免快速连点以保持顺畅动画。';
});

const overviewSummary = computed(() => {
  return `${CARD_COUNT} 张规则卡 + 演示视频 + 场上笔记`;
});

function resolveDirections(fromIndex, toIndex) {
  if (isOverviewStep(toIndex) || isOverviewStep(fromIndex)) {
    return { directionIn: 'fade', directionOut: 'fade' };
  }
  const baseIn = directionCycle[toIndex % directionCycle.length];
  const baseOut = directionCycle[fromIndex % directionCycle.length];
  return {
    directionIn: maybeRandomDirection(baseIn),
    directionOut: maybeRandomDirection(baseOut)
  };
}

function maybeRandomDirection(base) {
  if (Math.random() < 0.28) {
    const options = directionCycle.filter((dir) => dir !== base);
    return options[Math.floor(Math.random() * options.length)];
  }
  return base;
}

function oppositeDirection(dir) {
  if (dir === 'top') return 'bottom';
  if (dir === 'bottom') return 'top';
  if (dir === 'left') return 'right';
  if (dir === 'right') return 'left';
  return 'fade';
}

function restartTimer(duration, nextIndex) {
  if (transitionTimer) window.clearTimeout(transitionTimer);
  isAnimating.value = true;
  transitionTimer = window.setTimeout(() => {
    currentIndex.value = nextIndex;
    transitionData.value = null;
    isAnimating.value = false;
    rotateBackground();
    if (pendingTarget !== null) {
      const target = pendingTarget;
      pendingTarget = null;
      transitionToIndex(target);
    }
  }, duration);
}

function transitionToIndex(targetIndex) {
  if (targetIndex === currentIndex.value || targetIndex < 0 || targetIndex > overviewIndex) {
    return;
  }

  if (isAnimating.value && transitionData.value) {
    const active = transitionData.value;
    // 加速完成当前方向
    if (targetIndex === active.toIndex) {
      transitionData.value = { ...active, key: `fast-${active.toIndex}-${Date.now()}`, speed: 'fast' };
      restartTimer(interruptedDuration, active.toIndex);
      return;
    }
    // 倒放回上一张
    if (targetIndex === active.fromIndex) {
      const revIn = oppositeDirection(active.directionOut);
      const revOut = oppositeDirection(active.directionIn);
      transitionData.value = {
        key: `reverse-${active.fromIndex}-${Date.now()}`,
        fromIndex: active.toIndex,
        toIndex: active.fromIndex,
        directionIn: revIn,
        directionOut: revOut,
        speed: 'fast'
      };
      isAnimating.value = true;
      restartTimer(interruptedDuration, active.fromIndex);
      return;
    }
    // 其他目标：先加速当前，再衔接队列
    pendingTarget = targetIndex;
    transitionData.value = { ...active, key: `queue-${active.toIndex}-${Date.now()}`, speed: 'fast' };
    restartTimer(interruptedDuration, active.toIndex);
    return;
  }

  const directions = resolveDirections(currentIndex.value, targetIndex);
  transitionData.value = {
    key: `step-${currentIndex.value}-${targetIndex}-${Date.now()}`,
    fromIndex: currentIndex.value,
    toIndex: targetIndex,
    directionIn: directions.directionIn,
    directionOut: directions.directionOut,
    speed: 'normal'
  };
  isAnimating.value = true;
  restartTimer(transitionDuration, targetIndex);
}

function rotateBackground() {
  bgAngle.value = (bgAngle.value + 22 + Math.random() * 18) % 360;
}

function handleAdvance() {
  if (currentIndex.value === overviewIndex) return;
  const next = Math.min(currentIndex.value + 1, overviewIndex);
  transitionToIndex(next);
}

function goPrevious() {
  const prev = Math.max(currentIndex.value - 1, 0);
  transitionToIndex(prev);
}

function skipToOverview() {
  transitionToIndex(overviewIndex);
}

function handleKeydown(event) {
  if (event.defaultPrevented) return;
  const target = event.target;
  const tag = target?.tagName?.toLowerCase();
  if (target?.isContentEditable || ['input', 'textarea', 'select', 'option', 'button', 'video', 'audio'].includes(tag)) {
    return;
  }
  if ([' ', 'Spacebar', 'Enter', 'ArrowRight'].includes(event.key)) {
    event.preventDefault();
    handleAdvance();
    return;
  }
  if (event.key === 'ArrowLeft') {
    event.preventDefault();
    goPrevious();
    return;
  }
  if (event.key === 'Escape') {
    event.preventDefault();
    goPrevious();
  }
}

function isFlowStep(index) {
  return index === 0;
}

function isOverviewStep(index) {
  return index === overviewIndex;
}

function getCard(index) {
  if (index <= 0 || index > CARD_COUNT) return null;
  return rulesCards[index - 1];
}

function stageCardClasses(layer) {
  const classes = [];
  if (layer.direction === 'static') {
    classes.push('direction-static');
  } else {
    const dirClass = layer.role === 'incoming' ? `direction-in-${layer.direction}` : `direction-out-${layer.direction}`;
    classes.push(dirClass);
  }
  classes.push(layer.role);
  if (layer.speed === 'fast') classes.push('speed-fast');
  return classes;
}

function resolveEmbedUrl(source) {
  if (!source || !source.url) return '';
  const url = source.url;
  if (source.type === 'mp4') return '';
  const bvid = extractBilibiliBvid(url);
  if (bvid) return `https://player.bilibili.com/player.html?bvid=${bvid}&autoplay=0&high_quality=1&danmaku=0`;
  const yt = extractYoutubeId(url);
  if (yt) return `https://www.youtube.com/embed/${yt}?rel=0&modestbranding=1`;
  return '';
}

function extractBilibiliBvid(url) {
  if (!url) return '';
  const match = url.match(/BV([a-zA-Z0-9]+)/);
  return match ? `BV${match[1]}` : '';
}

function extractYoutubeId(url) {
  if (!url) return '';
  const shortMatch = url.match(/youtu\.be\/([\w-]{6,})/);
  if (shortMatch) return shortMatch[1];
  const vParam = url.match(/[?&]v=([\w-]{6,})/);
  return vParam ? vParam[1] : '';
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown);
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown);
  if (transitionTimer) {
    clearTimeout(transitionTimer);
  }
});
</script>

<style scoped>
.rulesPage {
  min-height: 100vh;
  padding: 32px 0 48px;
  color: var(--text);
  position: relative;
  overflow: visible;
  user-select: none;
}

.rulesPage .shell {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.pageHeader {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
  flex-wrap: wrap;
}

.eyebrow {
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.7);
  letter-spacing: 0.4em;
  font-size: 13px;
  margin: 0;
}

.pageHeader h1 {
  margin: 4px 0 6px;
  font-size: clamp(2rem, 3vw, 2.6rem);
  letter-spacing: 0.01em;
}

.subtitle {
  margin: 0;
  color: rgba(255, 255, 255, 0.72);
  font-size: 14px;
  max-width: 38rem;
}

.headerBadges {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.badge {
  padding: 6px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.2);
  font-size: 13px;
}

.ghost {
  padding: 6px 16px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  background: transparent;
  color: inherit;
  cursor: pointer;
  transition: background 0.3s ease, transform 0.3s ease;
}

.ghost:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.08);
  transform: translateY(-1px);
}

.ghost:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.linkBtn {
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.stageWrapper {
  position: relative;
  border-radius: 36px;
  padding: 32px 32px 52px;
  background: linear-gradient(var(--bg-angle, 215deg), rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.01)),
              radial-gradient(160% 120% at 20% 10%, rgba(108,178,255,0.08), transparent 48%),
              radial-gradient(140% 120% at 80% 20%, rgba(255,107,125,0.08), transparent 50%),
              var(--panel);
  border: 1px solid var(--stroke);
  box-shadow: var(--shadow);
  overflow: visible;
  min-height: 70vh;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  cursor: pointer;
  isolation: isolate;
  will-change: transform;
}

.nebula {
  position: absolute;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.35;
  pointer-events: none;
}

.nebulaOne {
  top: -120px;
  left: -80px;
  background: #4f46ff;
  animation: floatGlow 14s ease-in-out infinite alternate;
}

.nebulaTwo {
  bottom: -90px;
  right: 20px;
  background: #1ea7ff;
  animation: floatGlow 16s ease-in-out infinite alternate-reverse;
}

.stageBadge {
  font-size: 13px;
  text-transform: uppercase;
  letter-spacing: 0.4em;
  color: rgba(255, 255, 255, 0.42);
  margin-bottom: 18px;
}

.stageContainer {
  position: relative;
  flex: 1;
  min-height: 640px;
  overflow: visible;
  perspective: 1400px;
}

.stageCard {
  position: absolute;
  inset: 0;
  border-radius: 28px;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.05), rgba(255, 255, 255, 0.018));
  border: 1px solid var(--stroke);
  backdrop-filter: blur(18px);
  padding: clamp(32px, 4vw, 64px);
  box-shadow: 0 25px 70px rgba(0, 0, 0, 0.6);
  pointer-events: auto;
  display: flex;
  align-items: stretch;
  justify-content: center;
  overflow: visible;
  will-change: transform, opacity, filter;
  transform: translateZ(0);
  transform-style: preserve-3d;
}

.stageCard.current { z-index: 1; }
.stageCard.outgoing { z-index: 2; }
.stageCard.incoming { z-index: 3; }
.stageCard.is-overview {
  position: relative;
  inset: auto;
  min-height: unset;
  box-shadow: 0 28px 76px rgba(0, 0, 0, 0.55);
}
.stageCard.is-flow {
  box-shadow: 0 22px 64px rgba(0, 0, 0, 0.52);
}

.stageCardContent {
  width: 100%;
  max-width: 1240px;
  margin: 0 auto;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.flowHeader h2 { margin: 0; font-size: 32px; }
.flowHeader p { margin: 4px 0 0; color: rgba(255, 255, 255, 0.65); }

.flowTimeline {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 18px;
  margin-top: 16px;
}

.flowNode {
  display: flex;
  gap: 12px;
  padding: 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.nodeIcon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
}

.nodeIcon img { width: 28px; height: 28px; }
.flowNode h3 { margin: 0; font-size: 16px; }
.flowNode p { margin: 4px 0 0; color: rgba(255, 255, 255, 0.6); font-size: 14px; }

.cardHeader {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.cardMeta { display: flex; align-items: center; gap: 16px; }

.cardIcon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.08);
  display: grid;
  place-items: center;
}

.cardIcon img { width: 36px; height: 36px; }

.silverLabel { color: rgba(255, 255, 255, 0.6); font-size: 13px; margin: 0 0 4px; }

.cardHeader h2 { margin: 0; font-size: clamp(1.8rem, 2.6vw, 2.4rem); }

.progressBubble {
  font-size: 12px;
  letter-spacing: 0.2em;
  color: var(--muted);
}

.cardParagraph { margin: 0; color: var(--text); opacity: 0.85; font-size: 16px; }
.cardParagraph.highlight { color: var(--a2); font-weight: 600; }

.cardBullets { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }

.cardBullets li {
  padding-left: 22px;
  position: relative;
  color: var(--text);
  opacity: 0.9;
}

.cardBullets li::before {
  content: '';
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff8a00, #ff3c79);
  position: absolute;
  left: 6px;
  top: 7px;
}

.tipBox {
  margin-top: 12px;
  padding: 14px;
  border-radius: 16px;
  background: rgba(38, 132, 255, 0.18);
  border: 1px solid rgba(38, 132, 255, 0.45);
}

.tipBox strong { display: block; margin-bottom: 6px; }

.overviewPanel { display: flex; flex-direction: column; gap: 18px; max-width: 1200px; margin: 0 auto; }
.overviewIntro h2 { margin: 0; }
.overviewIntro p { margin: 4px 0 0; color: rgba(255, 255, 255, 0.65); }

.progressOutline {
  padding: 10px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 12px;
  letter-spacing: 0.2em;
}

.overviewGridWrap {
  max-height: 54vh;
  overflow: auto;
  padding: 4px 2px;
  mask-image: linear-gradient(180deg, transparent 0, rgba(255,255,255,0.9) 10%, rgba(255,255,255,0.9) 90%, transparent 100%);
}

.overviewGrid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 18px;
  padding-right: 6px;
}

.overviewCard {
  padding: 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 160px;
}

.overviewCardHead { display: flex; align-items: center; gap: 10px; }
.overviewCardHead img { width: 32px; height: 32px; }
.overviewCard h3 { margin: 0; font-size: 18px; }
.overviewCard p { margin: 0; font-size: 14px; color: rgba(255, 255, 255, 0.68); }
.overviewCard ul { margin: 0; padding-left: 16px; color: var(--muted); font-size: 13px; line-height: 1.4; }

.demoSection { display: flex; flex-direction: column; gap: 12px; margin-top: 12px; }
.demoHeader { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; align-items: center; }
.demoLabel { font-size: 12px; letter-spacing: 0.3em; text-transform: uppercase; color: rgba(255, 255, 255, 0.5); }
.demoHeader h3 { margin: 4px 0 2px; }
.demoSub { margin: 0; color: rgba(255,255,255,0.68); max-width: 520px; }
.demoBadges { display: flex; gap: 8px; flex-wrap: wrap; }
.demoSourceTabs { display: flex; gap: 8px; flex-wrap: wrap; }
.sourceTab{
  padding: 6px 12px;
  border-radius: 12px;
  border: 1px solid rgba(255,255,255,0.18);
  background: rgba(255,255,255,0.06);
  color: var(--text);
  cursor: pointer;
}
.sourceTab.active{
  border-color: rgba(108,178,255,0.45);
  box-shadow: 0 0 0 4px rgba(108,178,255,0.12);
}
.chip {
  padding: 6px 10px;
  border-radius: 12px;
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.12);
  font-size: 12px;
  color: var(--text);
}

.videoFrame {
  position: relative;
  border-radius: 22px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  overflow: hidden;
  background: linear-gradient(135deg, rgba(108,178,255,0.18), rgba(13,20,38,0.9));
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.55);
}
.demoEmbed{
  width: 100%;
  height: 260px;
  border: 0;
  background: #000;
}
.videoOverlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(120% 120% at 10% 10%, rgba(255,255,255,0.12), transparent 50%),
              radial-gradient(140% 140% at 80% 10%, rgba(255,135,160,0.12), transparent 58%);
  mix-blend-mode: screen;
  opacity: 0.65;
}
.demoVideo { width: 100%; height: 260px; object-fit: cover; background: #000; display: block; }
.demoCaption { margin: 0; color: rgba(255,255,255,0.7); }

.notesSection { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; }

.noteBlock { padding: 14px; border-radius: 16px; background: rgba(255, 255, 255, 0.02); border: 1px solid rgba(255, 255, 255, 0.08); }
.noteBlock h4 { margin: 0 0 6px; }
.noteBlock ul { margin: 0; padding-left: 18px; font-size: 13px; color: rgba(255, 255, 255, 0.7); line-height: 1.6; }

.overviewLinks { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 8px; }
.pill { padding: 10px 20px; border-radius: 999px; border: 1px solid rgba(255, 255, 255, 0.4); background: rgba(255, 255, 255, 0.08); text-decoration: none; color: inherit; font-size: 14px; }

.detailList {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 6px;
}
.detailRow {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.detailTitle {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 180px;
}
.detailTitle img { width: 26px; height: 26px; }
.detailBody { display: flex; flex-wrap: wrap; gap: 8px; }
.detailTag, .detailTip {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 12px;
  color: var(--muted);
}
.detailTip { color: var(--text); border-color: rgba(108,178,255,0.25); }

.stageHint { margin-top: 18px; font-size: 13px; color: rgba(255, 255, 255, 0.7); }

.navigationBar { display: flex; justify-content: space-between; gap: 12px; align-items: center; }

button.primary {
  background: linear-gradient(135deg, var(--a), var(--a2));
  border: none;
  border-radius: 999px;
  color: #08070d;
  font-weight: 700;
  padding: 10px 24px;
  cursor: pointer;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  box-shadow: 0 10px 30px rgba(255, 111, 145, 0.35);
}

button.primary:disabled { opacity: 0.5; cursor: not-allowed; transform: none; box-shadow: none; }
.navigationBar button:not(:disabled):hover { transform: translateY(-2px); }

.stageCard.direction-static { transform: translate3d(0, 0, 0) scale(1); opacity: 1; }

.stageCard {
  --anim-in: 0.62s;
  --anim-out: 0.6s;
  --anim-in-fast: 0.32s;
  --anim-out-fast: 0.28s;
}

.stageCard.incoming.direction-in-top { animation: slideInFromTop var(--anim-in) forwards cubic-bezier(0.16, 0.8, 0.24, 1); }
.stageCard.incoming.direction-in-bottom { animation: slideInFromBottom var(--anim-in) forwards cubic-bezier(0.16, 0.8, 0.24, 1); }
.stageCard.incoming.direction-in-left { animation: slideInFromLeft var(--anim-in) forwards cubic-bezier(0.16, 0.8, 0.24, 1); }
.stageCard.incoming.direction-in-right { animation: slideInFromRight var(--anim-in) forwards cubic-bezier(0.16, 0.8, 0.24, 1); }

.stageCard.outgoing.direction-out-top { animation: slideOutToTop var(--anim-out) forwards cubic-bezier(0.65, 0, 0.35, 1); }
.stageCard.outgoing.direction-out-bottom { animation: slideOutToBottom var(--anim-out) forwards cubic-bezier(0.65, 0, 0.35, 1); }
.stageCard.outgoing.direction-out-left { animation: slideOutToLeft var(--anim-out) forwards cubic-bezier(0.65, 0, 0.35, 1); }
.stageCard.outgoing.direction-out-right { animation: slideOutToRight var(--anim-out) forwards cubic-bezier(0.65, 0, 0.35, 1); }

.stageCard.incoming.direction-in-fade { animation: fadeInSoft var(--anim-in) forwards ease-out; }
.stageCard.outgoing.direction-out-fade { animation: fadeOutSoft var(--anim-out) forwards ease-in; }

.stageCard.speed-fast.incoming { animation-duration: var(--anim-in-fast); }
.stageCard.speed-fast.outgoing { animation-duration: var(--anim-out-fast); }

@keyframes slideInFromTop {
  from { transform: translate3d(0, -115%, 12px) scale(0.97) rotate(-0.8deg); opacity: 0.25; filter: blur(6px); }
  to { transform: translate3d(0, 0, 0) scale(1) rotate(0deg); opacity: 1; filter: blur(0); }
}

@keyframes slideInFromBottom {
  from { transform: translate3d(0, 115%, 12px) scale(0.97) rotate(0.8deg); opacity: 0.25; filter: blur(6px); }
  to { transform: translate3d(0, 0, 0) scale(1) rotate(0deg); opacity: 1; filter: blur(0); }
}

@keyframes slideInFromLeft {
  from { transform: translate3d(-115%, 0, 12px) scale(0.97) rotate(-0.9deg); opacity: 0.25; filter: blur(6px); }
  to { transform: translate3d(0, 0, 0) scale(1) rotate(0deg); opacity: 1; filter: blur(0); }
}

@keyframes slideInFromRight {
  from { transform: translate3d(115%, 0, 12px) scale(0.97) rotate(0.9deg); opacity: 0.25; filter: blur(6px); }
  to { transform: translate3d(0, 0, 0) scale(1) rotate(0deg); opacity: 1; filter: blur(0); }
}

@keyframes slideOutToTop {
  from { transform: translate3d(0, 0, 0) scale(1); opacity: 1; }
  to { transform: translate3d(0, -115%, 0) scale(0.96) rotate(-1.2deg); opacity: 0; filter: blur(5px); }
}

@keyframes slideOutToBottom {
  from { transform: translate3d(0, 0, 0) scale(1); opacity: 1; }
  to { transform: translate3d(0, 115%, 0) scale(0.96) rotate(1.2deg); opacity: 0; filter: blur(5px); }
}

@keyframes slideOutToLeft {
  from { transform: translate3d(0, 0, 0) scale(1); opacity: 1; }
  to { transform: translate3d(-115%, 0, 0) scale(0.96) rotate(-1.2deg); opacity: 0; filter: blur(5px); }
}

@keyframes slideOutToRight {
  from { transform: translate3d(0, 0, 0) scale(1); opacity: 1; }
  to { transform: translate3d(115%, 0, 0) scale(0.96) rotate(1.2deg); opacity: 0; filter: blur(5px); }
}

@keyframes fadeInSoft {
  from { opacity: 0; transform: translateY(10px) scale(0.96); filter: blur(8px); }
  to { opacity: 1; transform: translateY(0) scale(1); filter: blur(0); }
}

@keyframes fadeOutSoft {
  from { opacity: 1; transform: translateY(0) scale(1); filter: blur(0); }
  to { opacity: 0; transform: translateY(-12px) scale(0.96); filter: blur(6px); }
}

@keyframes floatGlow {
  from { transform: translateY(0) scale(1); opacity: 0.35; }
  to { transform: translateY(-16px) scale(1.04); opacity: 0.48; }
}

@media (max-width: 768px) {
  .stageWrapper { padding: 20px 18px 32px; min-height: 56vh; }
  .stageContainer { min-height: 520px; }
  .stageCard { padding: 22px; }
  .navigationBar { justify-content: space-between; }
  .overviewGridWrap { max-height: none; mask-image: none; }
}
</style>
