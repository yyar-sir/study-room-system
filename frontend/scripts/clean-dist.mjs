/**
 * 清空构建产物目录（prebuild 钩子调用）。
 *
 * 为什么不用 fs.rmSync：
 *   实测在本机环境（Windows + 含中文的项目路径）下，
 *   `fs.rmSync(dir, { recursive: true, force: true })` 会「不抛异常、也不删除任何东西」——
 *   调用前后目录内文件数完全不变。而 Vite 内部的 emptyDir() 清理产物走的正是这个 API，
 *   所以即使把 build.emptyOutDir 显式设为 true，dist 也从不清空：
 *   旧版本的按需分包会无限累积（曾累积到 1300 个文件、23 份 Login-*.js），
 *   后果是 ①部署包逐次变大 ②已下线的旧代码仍能通过 /assets/<旧哈希>.js 被公开访问。
 *
 *   本脚本改用逐文件 unlinkSync + 逐目录 rmdirSync（自底向上），实测在本机可正常工作。
 *
 * 用法：node scripts/clean-dist.mjs [目录，默认 dist]
 */
import { existsSync, readdirSync, rmdirSync, unlinkSync } from 'node:fs'
import { join } from 'node:path'

const target = process.argv[2] || 'dist'

if (!existsSync(target)) {
  console.log(`[clean] ${target} 不存在，跳过`)
  process.exit(0)
}

let fileCount = 0
let dirCount = 0

function clean(dir) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name)
    if (entry.isDirectory() && !entry.isSymbolicLink()) {
      clean(full)
      rmdirSync(full)
      dirCount += 1
    } else {
      unlinkSync(full)
      fileCount += 1
    }
  }
}

clean(target)
rmdirSync(target)

console.log(`[clean] 已清空 ${target}：删除 ${fileCount} 个文件、${dirCount + 1} 个目录`)
