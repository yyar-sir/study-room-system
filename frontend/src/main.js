import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// Element Plus 的暗色变量（选择器是 html.dark，由 useTheme 切换）
// 必须排在 main.css 之前——main.css 里的深色覆盖要在它之后才能生效
import 'element-plus/theme-chalk/dark/css-vars.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
// 注1：原脚手架的 '@fontsource/zcool-kuaile'（站酷快乐体）已移除。
//      那是面向少儿的卡通标题字体，与管理类系统不匹配；
//      字体栈已改为系统中文黑体（见 styles/main.css 的 --font-title）。
//
// 注2：原脚手架在这里 `import * as Icons` 并循环注册了全部 200+ 个图标组件。
//      那会让整包体积额外增加数百 KB —— 实测产物 index.js 达到 1.21 MB。
//      本项目页面并未直接使用任何 <el-icon><Xxx /></el-icon> 图标组件，
//      Element Plus 内部组件（下拉箭头、日期图标等）会自行引入所需图标，
//      因此这里不再做全量注册。若将来某个页面确实要用图标，请在该页面内
//      按需 import（例如 import { Search } from '@element-plus/icons-vue'），
//      不要退回全局注册。

import App from './App.vue'
import router from './router'
import './styles/main.css'
import { initTheme } from './composables/useTheme'

// 挂载前定好主题：index.html 的内联脚本已防住闪烁，
// 这里再同步一次模块内的 ref，避免首帧按钮图标与实际主题不一致
initTheme()

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
