<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <el-icon><Microphone /></el-icon>
        <span>噪声证据链</span>
      </div>
      <el-menu :default-active="route.name as string" router class="menu" background-color="#001529"
               text-color="#b7c0cd" active-text-color="#fff">
        <el-menu-item index="records" :route="{ name: 'records' }">
          <el-icon><Document /></el-icon><span>采样记录</span>
        </el-menu-item>
        <el-menu-item index="anomalies" :route="{ name: 'anomalies' }">
          <el-icon><Warning /></el-icon><span>异常处理</span>
        </el-menu-item>
        <el-menu-item index="imports" :route="{ name: 'imports' }">
          <el-icon><Upload /></el-icon><span>批量导入</span>
        </el-menu-item>
        <el-menu-item index="trend" :route="{ name: 'trend' }">
          <el-icon><TrendCharts /></el-icon><span>分贝趋势</span>
        </el-menu-item>
        <el-menu-item index="sensors" :route="{ name: 'sensors' }">
          <el-icon><Cpu /></el-icon><span>传感器与批次</span>
        </el-menu-item>
        <el-menu-item index="audit" :route="{ name: 'audit' }">
          <el-icon><Lock /></el-icon><span>审计日志</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="title">{{ route.meta.title || '城市噪声采样证据链管理系统' }}</div>
        <el-dropdown @command="onCommand">
          <span class="user">
            <el-icon><UserFilled /></el-icon>
            {{ auth.user?.realName || auth.user?.username }}
            <el-tag size="small" type="info" effect="plain" class="role-tag">
              {{ auth.user?.role }}
            </el-tag>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

async function onCommand(command: string) {
  if (command === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    auth.logout()
    router.replace('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background: #001529;
  display: flex;
  flex-direction: column;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 1px;
}
.menu {
  border-right: none;
  flex: 1;
}
.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.user {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #303133;
}
.role-tag {
  margin: 0 2px;
}
.main {
  background: #f0f2f5;
  padding: 0;
  overflow-y: auto;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
