<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, readableError, type ApiResponse } from '../api'
import { displayLabel, enabledStatusLabels, permissionLabels } from '../labels'

interface UserView { user: { id: number; username: string; displayName: string; status: string }; roleIds: number[]; allProjects: boolean; projectIds: number[] }
interface RoleView { role: { id: number; roleCode: string; roleName: string; status: string }; permissionIds: number[] }
interface Permission { id: number; permissionCode: string; permissionName: string }
interface CacheStats { hits: number; misses: number; invalidations: number }
const users = ref<UserView[]>([])
const roles = ref<RoleView[]>([])
const permissions = ref<Permission[]>([])
const cache = ref<CacheStats>({ hits: 0, misses: 0, invalidations: 0 })
const userDialog = ref(false)
const roleDialog = ref(false)
const editRoleDialog = ref(false)
const editUserDialog = ref(false)
const userForm = reactive({ username: '', displayName: '', password: '' })
const roleForm = reactive({ roleCode: '', roleName: '' })
const editingRole = ref<RoleView>()
const checkedPermissions = ref<number[]>([])
const editingUser = ref<UserView>()
const checkedRoles = ref<number[]>([])
const allProjects = ref(false)
const projectIdsText = ref('')

async function load() {
  try {
    const [u, r, p, c] = await Promise.all([
      api.get<ApiResponse<UserView[]>>('/rbac/users'), api.get<ApiResponse<RoleView[]>>('/rbac/roles'),
      api.get<ApiResponse<Permission[]>>('/rbac/permissions'), api.get<ApiResponse<CacheStats>>('/rbac/cache-stats'),
    ])
    users.value = u.data.data; roles.value = r.data.data; permissions.value = p.data.data; cache.value = c.data.data
  } catch (error: any) { ElMessage.error(readableError(error, '加载权限数据失败')) }
}
async function createUser() {
  try { await api.post('/rbac/users', userForm); userDialog.value = false; ElMessage.success('用户创建成功'); await load() }
  catch (error: any) { ElMessage.error(readableError(error, '创建用户失败')) }
}
async function createRole() {
  try { await api.post('/rbac/roles', roleForm); roleDialog.value = false; ElMessage.success('角色创建成功'); await load() }
  catch (error: any) { ElMessage.error(readableError(error, '创建角色失败')) }
}
function editRole(row: RoleView) { editingRole.value = row; checkedPermissions.value = [...row.permissionIds]; editRoleDialog.value = true }
async function saveRole() {
  if (!editingRole.value) return
  try {
    await api.put(`/rbac/roles/${editingRole.value.role.id}/permissions`, { ids: checkedPermissions.value })
    editRoleDialog.value = false; ElMessage.success('权限已更新，受影响的登录会话已注销'); await load()
  } catch (error: any) { ElMessage.error(readableError(error, '更新角色权限失败')) }
}
function editUser(row: UserView) {
  editingUser.value = row; checkedRoles.value = [...row.roleIds]; allProjects.value = row.allProjects
  projectIdsText.value = row.projectIds.join(','); editUserDialog.value = true
}
async function saveUser() {
  if (!editingUser.value) return
  const id = editingUser.value.user.id
  const projectIds = projectIdsText.value.split(',').map(Number).filter(Boolean)
  try {
    await api.put(`/rbac/users/${id}/roles`, { ids: checkedRoles.value })
    await api.put(`/rbac/users/${id}/data-scope`, { allProjects: allProjects.value, projectIds })
    editUserDialog.value = false; ElMessage.success('访问范围已更新，该用户登录会话已注销'); await load()
  } catch (error: any) { ElMessage.error(readableError(error, '更新用户访问权限失败')) }
}
function roleName(role: RoleView['role']) {
  return role.roleCode === 'PLATFORM_ADMIN' ? '平台管理员' : role.roleCode === 'PROJECT_OPERATOR' ? '项目运维员' : role.roleName
}
onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="eyebrow">授权与数据范围</span><h1>权限管理</h1></div><div><el-button @click="roleDialog = true">新增角色</el-button><el-button type="primary" @click="userDialog = true">新增用户</el-button></div></div>
    <div class="metric-grid compact-metrics"><el-card shadow="never"><span>缓存命中</span><strong>{{ cache.hits }}</strong></el-card><el-card shadow="never"><span>缓存未命中</span><strong>{{ cache.misses }}</strong></el-card><el-card shadow="never"><span>缓存失效</span><strong>{{ cache.invalidations }}</strong></el-card></div>
    <el-tabs>
      <el-tab-pane label="用户"><el-table :data="users" class="data-table"><el-table-column prop="user.username" label="用户名" /><el-table-column prop="user.displayName" label="显示名称" /><el-table-column prop="user.status" label="状态" width="90"><template #default="scope">{{ displayLabel(enabledStatusLabels, scope.row.user.status) }}</template></el-table-column><el-table-column label="角色 ID"><template #default="scope">{{ scope.row.roleIds.join(', ') || '无' }}</template></el-table-column><el-table-column label="数据范围"><template #default="scope">{{ scope.row.allProjects ? '全部项目' : `项目 ${scope.row.projectIds.join(', ')}` }}</template></el-table-column><el-table-column label="操作" width="100"><template #default="scope"><el-button link @click="editUser(scope.row)">编辑权限</el-button></template></el-table-column></el-table></el-tab-pane>
      <el-tab-pane label="角色"><el-table :data="roles" class="data-table"><el-table-column prop="role.roleCode" label="角色编码" /><el-table-column label="角色名称"><template #default="scope">{{ roleName(scope.row.role) }}</template></el-table-column><el-table-column label="权限数"><template #default="scope">{{ scope.row.permissionIds.length }}</template></el-table-column><el-table-column label="操作" width="120"><template #default="scope"><el-button link @click="editRole(scope.row)">配置权限</el-button></template></el-table-column></el-table></el-tab-pane>
    </el-tabs>
    <el-dialog v-model="userDialog" title="创建用户" width="480px"><el-form label-position="top"><el-form-item label="用户名"><el-input v-model="userForm.username" /></el-form-item><el-form-item label="显示名称"><el-input v-model="userForm.displayName" /></el-form-item><el-form-item label="初始密码"><el-input v-model="userForm.password" type="password" show-password /></el-form-item></el-form><template #footer><el-button @click="userDialog = false">取消</el-button><el-button type="primary" @click="createUser">创建</el-button></template></el-dialog>
    <el-dialog v-model="roleDialog" title="创建角色" width="480px"><el-form label-position="top"><el-form-item label="角色编码"><el-input v-model="roleForm.roleCode" /></el-form-item><el-form-item label="角色名称"><el-input v-model="roleForm.roleName" /></el-form-item></el-form><template #footer><el-button @click="roleDialog = false">取消</el-button><el-button type="primary" @click="createRole">创建</el-button></template></el-dialog>
    <el-dialog v-model="editRoleDialog" title="配置角色权限" width="650px"><el-checkbox-group v-model="checkedPermissions" class="permission-grid"><el-checkbox v-for="permission in permissions" :key="permission.id" :value="permission.id">{{ permissionLabels[permission.permissionCode] ?? permission.permissionName }}（{{ permission.permissionCode }}）</el-checkbox></el-checkbox-group><template #footer><el-button @click="editRoleDialog = false">取消</el-button><el-button type="primary" @click="saveRole">保存并注销受影响会话</el-button></template></el-dialog>
    <el-dialog v-model="editUserDialog" title="编辑用户访问权限" width="560px"><el-form label-position="top"><el-form-item label="角色"><el-checkbox-group v-model="checkedRoles"><el-checkbox v-for="role in roles" :key="role.role.id" :value="role.role.id">{{ roleName(role.role) }}</el-checkbox></el-checkbox-group></el-form-item><el-form-item><el-checkbox v-model="allProjects">允许访问全部项目</el-checkbox></el-form-item><el-form-item v-if="!allProjects" label="项目 ID"><el-input v-model="projectIdsText" placeholder="例如：1,2" /></el-form-item></el-form><template #footer><el-button @click="editUserDialog = false">取消</el-button><el-button type="primary" @click="saveUser">保存并注销该用户会话</el-button></template></el-dialog>
  </section>
</template>
