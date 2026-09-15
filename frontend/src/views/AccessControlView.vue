<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, type ApiResponse } from '../api'

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
  const [u, r, p, c] = await Promise.all([
    api.get<ApiResponse<UserView[]>>('/rbac/users'), api.get<ApiResponse<RoleView[]>>('/rbac/roles'),
    api.get<ApiResponse<Permission[]>>('/rbac/permissions'), api.get<ApiResponse<CacheStats>>('/rbac/cache-stats'),
  ])
  users.value = u.data.data; roles.value = r.data.data; permissions.value = p.data.data; cache.value = c.data.data
}
async function createUser() {
  try { await api.post('/rbac/users', userForm); userDialog.value = false; ElMessage.success('User created'); await load() }
  catch (error: any) { ElMessage.error(error.response?.data?.message ?? 'Create failed') }
}
async function createRole() {
  try { await api.post('/rbac/roles', roleForm); roleDialog.value = false; ElMessage.success('Role created'); await load() }
  catch (error: any) { ElMessage.error(error.response?.data?.message ?? 'Create failed') }
}
function editRole(row: RoleView) { editingRole.value = row; checkedPermissions.value = [...row.permissionIds]; editRoleDialog.value = true }
async function saveRole() {
  if (!editingRole.value) return
  await api.put(`/rbac/roles/${editingRole.value.role.id}/permissions`, { ids: checkedPermissions.value })
  editRoleDialog.value = false; ElMessage.success('Permissions updated; affected sessions expired'); await load()
}
function editUser(row: UserView) {
  editingUser.value = row; checkedRoles.value = [...row.roleIds]; allProjects.value = row.allProjects
  projectIdsText.value = row.projectIds.join(','); editUserDialog.value = true
}
async function saveUser() {
  if (!editingUser.value) return
  const id = editingUser.value.user.id
  const projectIds = projectIdsText.value.split(',').map(Number).filter(Boolean)
  await api.put(`/rbac/users/${id}/roles`, { ids: checkedRoles.value })
  await api.put(`/rbac/users/${id}/data-scope`, { allProjects: allProjects.value, projectIds })
  editUserDialog.value = false; ElMessage.success('Access updated; user session expired'); await load()
}
onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="eyebrow">AUTHORIZATION</span><h1>Access control</h1></div><div><el-button @click="roleDialog = true">New role</el-button><el-button type="primary" @click="userDialog = true">New user</el-button></div></div>
    <div class="metric-grid compact-metrics"><el-card shadow="never"><span>Cache hits</span><strong>{{ cache.hits }}</strong></el-card><el-card shadow="never"><span>Cache misses</span><strong>{{ cache.misses }}</strong></el-card><el-card shadow="never"><span>Invalidations</span><strong>{{ cache.invalidations }}</strong></el-card></div>
    <el-tabs>
      <el-tab-pane label="Users"><el-table :data="users" class="data-table"><el-table-column prop="user.username" label="Username" /><el-table-column prop="user.displayName" label="Name" /><el-table-column label="Roles"><template #default="scope">{{ scope.row.roleIds.join(', ') || 'None' }}</template></el-table-column><el-table-column label="Data scope"><template #default="scope">{{ scope.row.allProjects ? 'All projects' : `Projects ${scope.row.projectIds.join(', ')}` }}</template></el-table-column><el-table-column label="Action" width="100"><template #default="scope"><el-button link @click="editUser(scope.row)">Edit</el-button></template></el-table-column></el-table></el-tab-pane>
      <el-tab-pane label="Roles"><el-table :data="roles" class="data-table"><el-table-column prop="role.roleCode" label="Code" /><el-table-column prop="role.roleName" label="Name" /><el-table-column label="Permissions"><template #default="scope">{{ scope.row.permissionIds.length }}</template></el-table-column><el-table-column label="Action" width="120"><template #default="scope"><el-button link @click="editRole(scope.row)">Permissions</el-button></template></el-table-column></el-table></el-tab-pane>
    </el-tabs>
    <el-dialog v-model="userDialog" title="Create user" width="480px"><el-form label-position="top"><el-form-item label="Username"><el-input v-model="userForm.username" /></el-form-item><el-form-item label="Display name"><el-input v-model="userForm.displayName" /></el-form-item><el-form-item label="Initial password"><el-input v-model="userForm.password" type="password" /></el-form-item></el-form><template #footer><el-button @click="userDialog = false">Cancel</el-button><el-button type="primary" @click="createUser">Create</el-button></template></el-dialog>
    <el-dialog v-model="roleDialog" title="Create role" width="480px"><el-form label-position="top"><el-form-item label="Role code"><el-input v-model="roleForm.roleCode" /></el-form-item><el-form-item label="Role name"><el-input v-model="roleForm.roleName" /></el-form-item></el-form><template #footer><el-button @click="roleDialog = false">Cancel</el-button><el-button type="primary" @click="createRole">Create</el-button></template></el-dialog>
    <el-dialog v-model="editRoleDialog" title="Replace role permissions" width="650px"><el-checkbox-group v-model="checkedPermissions" class="permission-grid"><el-checkbox v-for="permission in permissions" :key="permission.id" :value="permission.id">{{ permission.permissionCode }}</el-checkbox></el-checkbox-group><template #footer><el-button @click="editRoleDialog = false">Cancel</el-button><el-button type="primary" @click="saveRole">Save and expire sessions</el-button></template></el-dialog>
    <el-dialog v-model="editUserDialog" title="Edit user access" width="560px"><el-form label-position="top"><el-form-item label="Roles"><el-checkbox-group v-model="checkedRoles"><el-checkbox v-for="role in roles" :key="role.role.id" :value="role.role.id">{{ role.role.roleName }}</el-checkbox></el-checkbox-group></el-form-item><el-form-item><el-checkbox v-model="allProjects">All projects</el-checkbox></el-form-item><el-form-item v-if="!allProjects" label="Project IDs"><el-input v-model="projectIdsText" /></el-form-item></el-form><template #footer><el-button @click="editUserDialog = false">Cancel</el-button><el-button type="primary" @click="saveUser">Save and expire session</el-button></template></el-dialog>
  </section>
</template>
