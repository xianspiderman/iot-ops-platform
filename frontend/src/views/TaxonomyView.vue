<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, readableError, type ApiResponse } from '../api'
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
  try {
    const [groupResult, tagResult] = await Promise.all([
      api.get<ApiResponse<GroupView[]>>('/device-taxonomy/groups'),
      api.get<ApiResponse<TagView[]>>('/device-taxonomy/tags'),
    ])
    groups.value = groupResult.data.data
    tags.value = tagResult.data.data
  } catch (error: any) { ElMessage.error(readableError(error, '加载分组与标签失败')) }
}
async function createGroup() {
  try { await api.post('/device-taxonomy/groups', groupForm); groupDialog.value = false; ElMessage.success('设备分组创建成功'); await load() }
  catch (error: any) { ElMessage.error(readableError(error, '创建设备分组失败')) }
}
async function createTag() {
  try { await api.post('/device-taxonomy/tags', tagForm); tagDialog.value = false; ElMessage.success('设备标签创建成功'); await load() }
  catch (error: any) { ElMessage.error(readableError(error, '创建设备标签失败')) }
}
async function editMembers(kind: 'groups' | 'tags', id: number, current: number[]) {
  try {
    const { value } = await ElMessageBox.prompt('请输入以英文逗号分隔的设备 ID', '更新关联设备', { inputValue: current.join(',') })
    const deviceIds = value.split(',').map(Number).filter(Boolean)
    await api.put(`/device-taxonomy/${kind}/${id}/devices`, { deviceIds })
    ElMessage.success('关联设备更新成功')
    await load()
  } catch (error: any) { if (error !== 'cancel') ElMessage.error(readableError(error, '更新关联设备失败')) }
}
onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="eyebrow">设备分类</span><h1>分组与标签</h1></div><div v-if="session.can('device-taxonomy:write')"><el-button @click="tagDialog = true">新增标签</el-button><el-button type="primary" @click="groupDialog = true">新增分组</el-button></div></div>
    <div class="split-grid">
      <el-card shadow="never"><template #header>项目设备分组</template><el-table :data="groups">
        <el-table-column prop="group.groupName" label="分组名称" /><el-table-column prop="group.projectId" label="项目 ID" width="90" />
        <el-table-column label="设备数" width="100"><template #default="scope">{{ scope.row.deviceIds.length }}</template></el-table-column>
        <el-table-column v-if="session.can('device-taxonomy:write')" label="操作" width="100"><template #default="scope"><el-button link @click="editMembers('groups', scope.row.group.id, scope.row.deviceIds)">关联设备</el-button></template></el-table-column>
      </el-table></el-card>
      <el-card shadow="never"><template #header>通用设备标签</template><el-table :data="tags">
        <el-table-column label="标签"><template #default="scope"><el-tag :color="scope.row.tag.tagColor" effect="dark">{{ scope.row.tag.tagName }}</el-tag></template></el-table-column>
        <el-table-column label="设备数" width="100"><template #default="scope">{{ scope.row.deviceIds.length }}</template></el-table-column>
        <el-table-column v-if="session.can('device-taxonomy:write')" label="操作" width="100"><template #default="scope"><el-button link @click="editMembers('tags', scope.row.tag.id, scope.row.deviceIds)">关联设备</el-button></template></el-table-column>
      </el-table></el-card>
    </div>
    <el-dialog v-model="groupDialog" title="创建设备分组" width="480px"><el-form label-position="top"><el-form-item label="项目 ID"><el-input-number v-model="groupForm.projectId" :min="1" /></el-form-item><el-form-item label="分组名称"><el-input v-model="groupForm.groupName" /></el-form-item><el-form-item label="说明"><el-input v-model="groupForm.description" /></el-form-item></el-form><template #footer><el-button @click="groupDialog = false">取消</el-button><el-button type="primary" @click="createGroup">创建</el-button></template></el-dialog>
    <el-dialog v-model="tagDialog" title="创建设备标签" width="480px"><el-form label-position="top"><el-form-item label="标签名称"><el-input v-model="tagForm.tagName" /></el-form-item><el-form-item label="颜色"><el-color-picker v-model="tagForm.tagColor" /></el-form-item></el-form><template #footer><el-button @click="tagDialog = false">取消</el-button><el-button type="primary" @click="createTag">创建</el-button></template></el-dialog>
  </section>
</template>
