<template>
  <div class="login-page">
    <!-- 登录页也放一个：深色偏好用户不该被迫先登录才能换主题 -->
    <ThemeToggle class="login-theme" />
    <div class="brand-side">
      <div class="brand-inner">
        <div class="brand-logo">
          <BrandMark class="logo-mark" :size="44" />
          <h1>静学共享自习室</h1>
          <p>在线选座 · 扫码签到 · 套餐计费，一站式自习室预约与管理平台。</p>
        </div>

        <ul class="brand-points">
          <li>座位状态实时同步，可视化选座</li>
          <li>次卡 / 月卡 / 季卡，到期自动提醒</li>
          <li>AI 智能自习助手，随问随答</li>
        </ul>

        <!--
          核心流程：用来填左半屏下方的大片留白。
          这里刻意不放「门店数 / 服务人次」这类经营数字 —— 演示环境是假数据，
          写上去答辩时容易被追问出处。
        -->
        <ol class="brand-flow">
          <li><b>1</b><span>在线选座</span></li>
          <li><b>2</b><span>扫码签到</span></li>
          <li><b>3</b><span>签到扣次</span></li>
        </ol>
      </div>
    </div>

    <div class="form-side">
      <div class="form-card">
        <h2>{{ isRegister ? '注册账号' : '欢迎回来' }}</h2>
        <p class="sub">
          {{ isRegister ? '使用手机号注册，注册后即可预约座位' : '登录后继续你的自习计划' }}
        </p>

        <div class="tabs" role="tablist" aria-label="登录或注册">
          <button
            type="button"
            role="tab"
            :aria-selected="!isRegister"
            :class="{ active: !isRegister }"
            @click="isRegister = false"
          >登录</button>
          <button
            type="button"
            role="tab"
            :aria-selected="isRegister"
            :class="{ active: isRegister }"
            @click="isRegister = true"
          >注册</button>
        </div>

        <el-form class="login-form" @submit.prevent>
          <div v-if="isRegister" class="field">
            <label for="login-nick">昵称</label>
            <el-input
              id="login-nick"
              v-model="form.nickname"
              aria-label="昵称"
              :prefix-icon="User"
              placeholder="如：小李"
              size="large"
              @keyup.enter="submit"
            />
          </div>

          <div class="field">
            <label for="login-phone">手机号</label>
            <el-input
              id="login-phone"
              v-model="form.phone"
              aria-label="手机号"
              :prefix-icon="Iphone"
              inputmode="tel"
              maxlength="11"
              placeholder="11 位手机号"
              size="large"
              @keyup.enter="submit"
            />
          </div>

          <div class="field">
            <div class="field-head">
              <label for="login-pwd">密码</label>
              <!-- 忘记密码放在「密码」标签这一行，而不是孤立在按钮下方 -->
              <button type="button" class="link-btn" @click="openForgot">忘记密码？</button>
            </div>
            <el-input
              id="login-pwd"
              v-model="form.password"
              type="password"
              show-password
              aria-label="密码"
              :prefix-icon="Lock"
              placeholder="6-20 位"
              size="large"
              @keyup.enter="submit"
            />
          </div>

          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="loading"
            @click="submit"
          >
            {{ isRegister ? '注册' : '登录' }}
          </el-button>
        </el-form>

        <!--
          演示账号来自后端 DemoDataInitializer（仅 local 环境）。
          角色不是登录时选的，而是由账号自身决定 —— 前台账号登不进经营者后台，
          后端 @RequireRole 会直接返回 403。
          做成可点击的 chip：点一下即填入并登录，省去答辩时手敲手机号。
        -->
        <div class="demo-tip">
          <div class="demo-head">
            <span class="demo-title">演示账号</span>
            <span class="demo-pwd">密码均为 123456 · 点一下直接登录</span>
          </div>
          <div class="demo-chips">
            <button
              v-for="d in DEMO_ACCOUNTS"
              :key="d.phone"
              type="button"
              class="demo-chip"
              :disabled="loading"
              @click="fillDemo(d)"
            >
              <span class="dc-role">{{ d.role }}</span>
              <span class="dc-phone">{{ d.phone }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="forgotDialog" title="找回密码" width="400px">
      <p class="forgot-hint">
        演示环境没有接短信通道：点「获取验证码」后，后端会把验证码直接返回并自动填入下方。
        生产环境应改为短信下发且不回传。
      </p>
      <div class="f-field">
        <label for="forgot-phone">手机号</label>
        <el-input id="forgot-phone" v-model="forgot.phone" aria-label="手机号" :prefix-icon="Iphone" inputmode="tel" maxlength="11" placeholder="注册时使用的手机号" />
      </div>
      <div class="f-field">
        <label for="forgot-code">验证码</label>
        <div class="code-row">
          <el-input id="forgot-code" v-model="forgot.code" aria-label="验证码" inputmode="numeric" maxlength="6" placeholder="6 位验证码" />
          <el-button :loading="sendingCode" @click="doSendCode">获取验证码</el-button>
        </div>
      </div>
      <div class="f-field">
        <label for="forgot-pwd">新密码</label>
        <el-input id="forgot-pwd" v-model="forgot.newPassword" aria-label="新密码" :prefix-icon="Lock" type="password" show-password placeholder="6-20 位" />
      </div>
      <template #footer>
        <el-button @click="forgotDialog = false">取消</el-button>
        <el-button type="primary" :loading="resetting" @click="doReset">重置密码</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
// 按需引入图标，不做全局注册（见 main.js 注2：全量注册会让产物多出数百 KB）
import { Iphone, Lock, User } from '@element-plus/icons-vue'
import request from '@/api/request'
import { sendResetCode, resetPassword } from '@/api/auth'
import ThemeToggle from '@/components/ThemeToggle.vue'
import BrandMark from '@/components/BrandMark.vue'

const router = useRouter()
const route = useRoute()

const isRegister = ref(false)
const loading = ref(false)
const form = reactive({ phone: '', password: '', nickname: '' })

/** 与后端 DemoDataInitializer 保持一致（仅 local 环境） */
const DEMO_PASSWORD = '123456'
const DEMO_ACCOUNTS = [
  { role: '经营者', phone: '13900000000' },
  { role: '前台管理员', phone: '13900000001' },
  { role: '自习室用户', phone: '13900000002' }
]

/** 点演示账号 chip：填入并直接登录，省去演示时手敲手机号 */
async function fillDemo(d) {
  isRegister.value = false
  form.phone = d.phone
  form.password = DEMO_PASSWORD
  await submit()
}

// ---- 忘记密码 ----
const forgotDialog = ref(false)
const sendingCode = ref(false)
const resetting = ref(false)
const forgot = reactive({ phone: '', code: '', newPassword: '' })

function openForgot() {
  Object.assign(forgot, { phone: form.phone || '', code: '', newPassword: '' })
  forgotDialog.value = true
}

async function doSendCode() {
  if (!PHONE_RE.test(forgot.phone)) {
    ElMessage.warning('请输入正确的 11 位手机号')
    return
  }
  sendingCode.value = true
  try {
    const r = await sendResetCode(forgot.phone)
    // 演示环境后端直接回传验证码，这里自动填入省去手动输入；
    // 生产环境拿不到 code，需由用户从短信读取。
    if (r && r.code) {
      forgot.code = r.code
      ElMessage.success('验证码已生成并自动填入（演示环境）')
    } else {
      ElMessage.success('验证码已发送')
    }
  } catch (e) {
    // 拦截器已提示（手机号未注册等）
  } finally {
    sendingCode.value = false
  }
}

async function doReset() {
  if (!PHONE_RE.test(forgot.phone)) {
    ElMessage.warning('请输入正确的 11 位手机号')
    return
  }
  if (!forgot.code || forgot.code.length !== 6) {
    ElMessage.warning('请输入 6 位验证码')
    return
  }
  if (!forgot.newPassword || forgot.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  resetting.value = true
  try {
    await resetPassword({ ...forgot })
    ElMessage.success('密码已重置，请用新密码登录')
    forgotDialog.value = false
    form.phone = forgot.phone
    form.password = ''
  } catch (e) {
    // 拦截器已提示（验证码错误/过期、与原密码相同等）
  } finally {
    resetting.value = false
  }
}

const PHONE_RE = /^1[3-9]\d{9}$/

/** 角色 → 首页。必须与 router/index.js 的 ROLE_HOME 保持一致 */
const ROLE_HOME = { 0: '/owner/dashboard', 1: '/staff/workbench', 2: '/user/home' }

async function submit() {
  if (!PHONE_RE.test(form.phone)) {
    ElMessage.warning('请输入正确的 11 位手机号')
    return
  }
  if (!form.password) {
    ElMessage.warning('请输入密码')
    return
  }
  loading.value = true
  try {
    if (isRegister.value) {
      if (!form.nickname) {
        ElMessage.warning('请输入昵称')
        return
      }
      await request.post('/auth/register', {
        phone: form.phone,
        password: form.password,
        nickname: form.nickname
      })
      ElMessage.success('注册成功，请使用该手机号登录')
      isRegister.value = false
      return
    }

    // ⚠️ 后端 LoginVO 是【扁平】结构 { token, id, phone, role, ... }，
    //    并不是 { token, user: {...} }。早期这里误写成 data.user.role，
    //    导致 role 恒为 undefined → 所有人都被路由到用户端首页。
    const data = await request.post('/auth/login', {
      phone: form.phone,
      password: form.password
    })
    // 登录态统一存 localStorage：request.js 与路由守卫都从这里读
    localStorage.setItem('token', data.token)
    localStorage.setItem('userInfo', JSON.stringify(data))

    const role = Number(data.role ?? 2)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || ROLE_HOME[role] || '/user/home')
  } catch (e) {
    // 错误提示已由 request.js 拦截器统一处理，这里不重复弹
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { min-height: 100vh; display: grid; grid-template-columns: 1.15fr 1fr; position: relative; }
.login-theme { position: absolute; top: 16px; right: 16px; z-index: 10; }

/* ===== 左侧品牌区 ===== */
.brand-side {
  position: relative;
  overflow: hidden;
  background: var(--navy);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}
/* 细网格纹理：整块纯色太空，加一层极淡的网格让它有质感。纯 CSS，不用图片。 */
.brand-side::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.05) 1px, transparent 1px);
  background-size: 44px 44px;
  /* 径向遮罩：让网格在边缘淡出，避免"贴瓷砖"感 */
  -webkit-mask-image: radial-gradient(circle at 50% 42%, #000 0%, transparent 76%);
  mask-image: radial-gradient(circle at 50% 42%, #000 0%, transparent 76%);
  pointer-events: none;
}
/* 品牌蓝光晕：给平面一点纵深 */
.brand-side::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 26% 20%, rgba(74, 108, 232, 0.38), transparent 56%);
  pointer-events: none;
}
.brand-inner { position: relative; z-index: 1; max-width: 430px; padding: 40px; }

.brand-logo .logo-mark { margin-bottom: 16px; }
.brand-logo h1 {
  /* ⚠️ 必须显式声明颜色。全局规则 h1 { color: var(--ink) } 会直接命中，
     而 .brand-side 上的 color: #fff 只是「继承」，继承打不过直接命中 ——
     浅色主题下 --ink 是 #1c2740，压在 #15285c 的深蓝底上对比度只有 1.05:1，
     标题等于隐形。深色主题下 --ink 变白，所以这个 bug 只在浅色下暴露。 */
  color: #fff;
  font-family: var(--font-title);
  font-size: 40px;
  font-weight: 600;
  letter-spacing: 0.04em;
  margin: 0 0 12px;
}
/* text-wrap: balance 让两行长度接近，避免末行只剩几个字（本页副标题原本会把
   「与管理平台。」单独甩到第二行）。注意用 balance 而不是 pretty ——
   pretty 处理的是英文「单词孤行」，对中文这种无空格断行不起作用。 */
.brand-logo p { margin: 0; color: rgba(255, 255, 255, 0.72); line-height: var(--leading-body); text-wrap: balance; }

/* 要点列表：裸「·」换成带底色的对勾圆点 */
.brand-points { list-style: none; padding: 0; margin: 32px 0 0; display: flex; flex-direction: column; gap: 12px; }
.brand-points li { color: rgba(255, 255, 255, 0.86); font-size: var(--fs-sm); padding-left: 24px; position: relative; }
.brand-points li::before {
  content: '✓';
  position: absolute;
  left: 0;
  top: 0.15em;
  width: 16px;
  height: 16px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-pill);
  background: rgba(255, 138, 61, 0.2);
  color: var(--accent);
  font-size: 10px;
  font-weight: 700;
}

/* 核心流程：填空左半屏下方的大片留白 */
.brand-flow {
  list-style: none;
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 40px 0 0;
  padding: 18px 0 0;
  border-top: 1px solid rgba(255, 255, 255, 0.14);
}
.brand-flow li { display: flex; align-items: center; gap: 8px; }
.brand-flow li + li::before {
  content: '';
  width: 18px;
  height: 1px;
  margin-right: 2px;
  background: rgba(255, 255, 255, 0.24);
}
.brand-flow b {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.14);
  font-size: var(--fs-2xs);
  font-weight: 700;
}
.brand-flow span { font-size: var(--fs-sm); color: rgba(255, 255, 255, 0.82); }

/* ===== 右侧表单区 ===== */
.form-side { display: flex; align-items: center; justify-content: center; background: var(--bg); padding: 24px 16px; }
/* min(380px, 100%)：原实现固定 380px，在 360px 宽的手机上会横向溢出 */
.form-card { width: min(380px, 100%); background: var(--card); border: 1px solid var(--line); border-radius: var(--radius-lg); padding: 32px 30px; box-shadow: var(--shadow-card); }
.form-card h2 { margin: 0 0 6px; font-size: var(--fs-h2); }
.sub { color: var(--ink-2); font-size: var(--fs-sm); margin: 0 0 18px; }

/* tab 改成下划线式：实心蓝只留给「登录」这一个主动作，
   否则 tab 和按钮两条蓝带，主次不分 */
.tabs { display: flex; gap: 24px; margin-bottom: 20px; border-bottom: 1px solid var(--line); }
.tabs button {
  position: relative;
  padding: 0 0 10px;
  background: none;
  border: 0;
  cursor: pointer;
  font-family: inherit;
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--ink-3);
  transition: color 0.18s;
}
.tabs button:hover { color: var(--ink-2); }
.tabs button.active { color: var(--brand-ink); }
.tabs button.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  border-radius: 2px;
  background: var(--brand);
}

.login-form { display: flex; flex-direction: column; gap: 14px; }
/* 常驻标签：placeholder 一输入就消失，字段含义也随之丢失 */
.field { display: flex; flex-direction: column; gap: 6px; }
.field-head { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
.field label { font-size: var(--fs-xs); color: var(--ink-3); font-weight: 600; }
.submit-btn { width: 100%; margin-top: 4px; }

.link-btn {
  background: none; border: 0; padding: 0; cursor: pointer; font-family: inherit;
  font-size: var(--fs-xs); color: var(--ink-3);
}
.link-btn:hover { color: var(--brand-ink); }

.forgot-hint { margin: 0 0 14px; color: var(--ink-3); font-size: var(--fs-xs); line-height: 1.8; }
.f-field { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
.f-field label { font-size: var(--fs-xs); color: var(--ink-3); font-weight: 600; }
.code-row { display: flex; gap: 10px; }

/* ===== 演示账号：从一坨灰字改成可点击的 chip ===== */
.demo-tip { margin-top: 22px; padding-top: 16px; border-top: 1px dashed var(--line); }
.demo-head { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; }
.demo-title { font-size: var(--fs-xs); font-weight: 700; color: var(--ink-2); }
.demo-pwd { font-size: var(--fs-2xs); color: var(--ink-3); }
.demo-chips { display: flex; flex-direction: column; gap: 6px; }
.demo-chip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-chip);
  background: var(--surface-soft);
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: border-color 0.18s, background 0.18s;
}
.demo-chip:hover { border-color: var(--brand-line); background: var(--brand-soft); }
.demo-chip:disabled { opacity: 0.6; cursor: not-allowed; }
.dc-role { font-size: var(--fs-xs); font-weight: 600; color: var(--ink-2); }
.dc-phone { font-size: var(--fs-xs); color: var(--brand-deep); font-variant-numeric: tabular-nums; }

/* 窄屏：不要整块隐藏品牌区，留一条紧凑品牌头，手机上仍有识别 */
@media (max-width: 860px) {
  .login-page { grid-template-columns: 1fr; }
  .brand-side { align-items: stretch; }
  .brand-side::before,
  .brand-side::after { display: none; }
  .brand-inner { max-width: none; width: 100%; padding: 18px 60px 18px 20px; }
  .brand-logo { display: flex; align-items: center; gap: 12px; }
  .brand-logo .logo-mark { margin-bottom: 0; }
  .brand-logo h1 { font-size: var(--fs-lg); margin: 0; letter-spacing: 0.02em; }
  .brand-logo p,
  .brand-points,
  .brand-flow { display: none; }
}
</style>
