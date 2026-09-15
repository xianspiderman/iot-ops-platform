<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, type ApiResponse } from '../api'
import { useSessionStore } from '../stores/session'

interface GroupView { group: { id: number; projectId: number; groupName: string; description?: string }; deviceIds: number[] }
interface TagView { tag: { id: number; tagName: string; tagColor: string }; deviceIds: number[] }
const session = useSessionStore()
const groups = ref<GroupView[]>([])
const tags = ref<TagView[]>([])
const groupDialog = ref(false)
const tagDialog = ref(false)
const groupForm = reactive({ projectId: 1, groupName: '', description: '' })
const tagForm = reactive({ tagName: '', tagColor: '#409EFF' })

async function load() {
  const [groupResult, tagResult] = await Promise.all([
    api.get<ApiResponse<GroupView[]>>('/device-taxonomy/groups'),
    api.get<ApiResponse<TagView[]>>('/device-taxonomy/tags'),
  ])
  groups.value = groupResult.data.data
  tags.value = tagResult.data.data
}
async function createGroup() {
  try { await api.post('/device-taxonomy/groups', groupForm); groupDialog.value = false; ElMessage.success('Group created'); await load() }
  catch (error: any) { ElMessage.error(error.response?.data?.message ?? 'Create failed') }
}
async function createTag() {
  try { await api.post('/device-taxonomy/tags', tagForm); tagDialog.value = false; ElMessage.success('Tag created'); await load() }
  catch (error: any) { ElMessage.error(error.response?.data?.message ?? 'Create failed') }
}
async function editMembers(kind: 'groups' | 'tags', id: number, current: number[]) {
  try {
    const { value } = await ElMessageBox.prompt('Comma-separated device IDs', 'Replace membership', { inputValue: current.join(',') })
    const deviceIds = value.split(',').map(Number).filter(Boolean)
    await api.put(`/device-taxonomy/${kind}/${id}/devices`, { deviceIds })
    ElMessage.success('Membership replaced')
    await load()
  } catch (error: any) { if (error !== 'cancel') ElMessage.error(error.response?.data?.message ?? 'Update failed') }
}
onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="eyebrow">DEVICE TAXONOMY</span><h1>Groups & tags</h1></div><div v-if="session.can('device-taxonomy:write')"><el-button @click="tagDialog = true">New tag</el-button><el-button type="primary" @click="groupDialog = true">New group</el-button></div></div>
    <div class="split-grid">
      <el-card shadow="never"><template #header>Project groups</template><el-table :data="groups">
        <el-table-column prop="group.groupName" label="Group" /><el-table-column prop="group.projectId" label="Project" width="90" />
        <el-table-column label="Devices" width="100"><template #default="scope">{{ scope.row.deviceIds.length }}</template></el-table-column>
        <el-table-column v-if="session.can('device-taxonomy:write')" label="Action" width="100"><template #default="scope"><el-button link @click="editMembers('groups', scope.row.group.id, scope.row.deviceIds)">Members</el-button></template></el-table-column>
      </el-table></el-card>
      <el-card shadow="never"><template #header>Reusable tags</template><el-table :data="tags">
        <el-table-column label="Tag"><template #default="scope"><el-tag :color="scope.row.tag.tagColor" effect="dark">{{ scope.row.tag.tagName }}</el-tag></template></el-table-column>
        <el-table-column label="Devices" width="100"><template #default="scope">{{ scope.row.deviceIds.length }}</template></el-table-column>
        <el-table-column v-if="session.can('device-taxonomy:write')" label="Action" width="100"><template #default="scope"><el-button link @click="editMembers('tags', scope.row.tag.id, scope.row.deviceIds)">Members</el-button></template></el-table-column>
      </el-table></el-card>
    </div>
    <el-dialog v-model="groupDialog" title="Create device group" width="480px"><el-form label-position="top"><el-form-item label="Project ID"><el-input-number v-model="groupForm.projectId" :min="1" /></el-form-item><el-form-item label="Name"><el-input v-model="groupForm.groupName" /></el-form-item><el-form-item label="Description"><el-input v-model="groupForm.description" /></el-form-item></el-form><template #footer><el-button @click="groupDialog = false">Cancel</el-button><el-button type="primary" @click="createGroup">Create</el-button></template></el-dialog>
    <el-dialog v-model="tagDialog" title="Create device tag" width="480px"><el-form label-position="top"><el-form-item label="Name"><el-input v-model="tagForm.tagName" /></el-form-item><el-form-item label="Color"><el-color-picker v-model="tagForm.tagColor" /></el-form-item></el-form><template #footer><el-button @click="tagDialog = false">Cancel</el-button><el-button type="primary" @click="createTag">Create</el-button></template></el-dialog>
  </section>
</template>
