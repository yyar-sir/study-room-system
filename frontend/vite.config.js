import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  build: {
    // ⚠️ 不要只依赖 emptyOutDir 来清理产物。
    // 实测在本机环境（Windows + 含中文的项目路径）中，即使把 emptyOutDir 显式设为 true，
    // dist 也不会被清空（Vite 内部会走到 emptyDir，但前置的 fs.existsSync(outDir) 判定失败），
    // 结果是旧版本的按需分包一直累积 —— 曾累积到 1300 个文件、23 份 Login-*.js，
    // 造成 ①部署包逐次变大 ②已下线代码仍可通过 /assets/<旧哈希>.js 公开访问。
    // 因此真正负责清空目录的是 package.json 里的 prebuild 脚本，这里保留声明仅为表达意图。
    outDir: 'dist',
    emptyOutDir: true,
    rollupOptions: {
      output: {
        // 手动分包：把体积大且长期不变的第三方库拆成独立 chunk。
        // 收益是双重的：①首屏只需下载业务代码，框架库走长效缓存；
        // ②避免出现"改一行业务代码，用户就要重新下载整个 1.2MB 包"的情况。
        // 只声明真正被页面引用的重库。若在这里写了实际未被使用的包名，
        // Rollup 会生成 0 字节的空 chunk（曾经 gsap 就是这种情况）——
        // 无用文件、无意义的请求，属于构建噪音。
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia'],
          'element-plus': ['element-plus'],
          echarts: ['echarts']
        }
      }
    }
  },
  server: {
    port: 5173,
    open: false,
    host: '127.0.0.1',
    watch: {
      // 必须忽略「原子写临时目录」，否则 dev server 会随机崩溃。
      //
      // 原因：编辑器与代码工具保存文件时普遍采用「写临时文件 → 重命名」的原子写策略，
      // 过程中会在同目录下产生形如
      //     .main.css.20404.db44c314-....tmpdir/main.css.tmp
      // 的隐藏临时目录。Vite 默认监听整个项目目录，会把它一并纳入监听，
      // 而该临时文件在写入瞬间处于锁定状态，于是 FSWatcher 抛
      //     Error: EBUSY: resource busy or locked, watch '...tmpdir/main.css.tmp'
      // 这是个未捕获的 error 事件，会直接终止 node 进程 —— dev server 静默退出。
      //
      // 本次开发中确实踩到了：改一次 src/styles/main.css 就把前端服务打挂。
      ignored: [
        '**/.*.tmpdir/**',
        '**/*.tmpdir/**',
        '**/*.tmp',
        '**/.git/**',
        '**/node_modules/**'
      ]
    },
    proxy: {
      // 后端 API 代理
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
})
