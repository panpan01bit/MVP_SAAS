<template>
  <div>
    <el-card class="card-shadow" style="margin-bottom: 24px;">
      <template #header>
        <span style="font-weight: 600;">上传产品说明书（AI 训练）</span>
      </template>
      
      <el-form :model="uploadForm" label-width="120px">
        <el-form-item label="选择产品">
          <el-select v-model="uploadForm.productId" placeholder="请选择产品" style="width: 100%">
            <el-option v-for="product in products" :key="product.id" :label="`${product.productName} (${product.sku})`" :value="product.id" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="PDF文件">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".pdf"
            :on-change="handleFileChange"
          >
            <el-button type="primary">选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">仅支持PDF文件，最大50MB。支持"转曲/扫描版"说明书（AI 视觉逐页阅读）</div>
            </template>
          </el-upload>
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="uploadManual" :loading="uploading">
            上传并开始 AI 训练
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
    
    <el-card class="card-shadow">
      <template #header>
        <span style="font-weight: 600;">说明书列表</span>
      </template>
      
      <el-table :data="manuals" style="width: 100%">
        <el-table-column prop="product.productName" label="产品" width="220" />
        <el-table-column prop="originalFileName" label="文件名" />
        <el-table-column label="AI 训练状态" width="260">
          <template #default="{ row }">
            <div class="train-cell">
              <el-tag :type="trainTagType(row.parseStatus)" size="small">
                {{ trainStatusText(row.parseStatus) }}
              </el-tag>
              <el-progress
                v-if="isTraining(row.parseStatus)"
                :percentage="row.parseProgress || 0"
                :stroke-width="8"
                striped
                striped-flow
                style="margin-top: 6px;"
              />
              <div class="train-msg">{{ row.parseMessage }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="确认状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'LOCKED' ? 'success' : 'warning'">
              {{ row.status === 'LOCKED' ? '已确认' : '待确认' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button v-if="row.parseStatus === 'DONE'" type="success" size="small" @click="showTrainingResult(row)">
              训练成果
            </el-button>
            <el-button v-if="row.status === 'UNCONFIRMED' && row.parseStatus === 'DONE'" type="primary" size="small" @click="confirmManual(row)">
              确认锁定
            </el-button>
            <el-button v-if="row.status === 'UNCONFIRMED' && row.parseStatus !== 'DONE'" type="danger" size="small" plain @click="deleteManual(row)">
              删除重传
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 训练成果弹窗 -->
    <el-dialog v-model="resultVisible" :title="`AI 训练成果 — ${resultManual?.originalFileName || ''}`" width="680px">
      <template v-if="resultManual">
        <h4 class="section-title">📖 AI 提取的产品信息</h4>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="产品名称">{{ resultManual.extractedProductName }}</el-descriptions-item>
          <el-descriptions-item label="型号">{{ resultManual.extractedModel }}</el-descriptions-item>
          <el-descriptions-item label="电池信息">{{ resultManual.extractedBatteryInfo }}</el-descriptions-item>
          <el-descriptions-item label="功率信息">{{ resultManual.extractedPowerInfo }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">❓ AI 预测的客户可能问题（已自动入知识库）</h4>
        <el-table :data="resultIssues" size="small" max-height="360">
          <el-table-column type="index" label="#" width="44" />
          <el-table-column prop="titleZh" label="问题场景" min-width="160" />
          <el-table-column prop="symptomEn" label="客户症状 (EN)" min-width="180" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              <el-tag :type="row.isFalseFault ? 'success' : 'danger'" size="small">
                {{ row.isFalseFault === false ? '真故障·转人工' : '假故障·可拦截' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <template v-if="resultManual.extractedSafetyWarnings">
          <h4 class="section-title">⚠️ 安全警告摘要</h4>
          <div class="digest-text">{{ resultManual.extractedSafetyWarnings }}</div>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const products = ref([])
const manuals = ref([])
const uploadForm = ref({
  productId: null
})
const selectedFile = ref(null)
const uploadRef = ref(null)
const uploading = ref(false)
const resultVisible = ref(false)
const resultManual = ref(null)
let pollTimer = null

const resultIssues = computed(() => {
  try {
    return JSON.parse(resultManual.value?.possibleIssuesJson || '{}').issues || []
  } catch {
    return []
  }
})

const isTraining = (status) => status === 'READING' || status === 'GENERATING'
const trainStatusText = (s) => ({
  UPLOADED: '已上传',
  READING: 'AI 阅读手册中',
  GENERATING: '生成可能问题中',
  DONE: '训练完成',
  FAILED: '训练失败'
}[s] || s)
const trainTagType = (s) => ({
  UPLOADED: 'info',
  READING: 'warning',
  GENERATING: 'warning',
  DONE: 'success',
  FAILED: 'danger'
}[s] || 'info')

const loadData = async () => {
  try {
    const [productsRes, manualsRes] = await Promise.all([
      api.products.getAll(),
      api.manuals.getAll()
    ])
    
    if (productsRes.success) products.value = productsRes.data || []
    if (manualsRes.success) manuals.value = manualsRes.data || []

    // 有训练中的手册 → 开启轮询；全部完成 → 停止轮询
    const anyTraining = manuals.value.some(m => isTraining(m.parseStatus))
    if (anyTraining && !pollTimer) {
      pollTimer = setInterval(loadData, 3000)
    } else if (!anyTraining && pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  } catch (error) {
    ElMessage.error('加载数据失败')
  }
}

const handleFileChange = (file) => {
  selectedFile.value = file.raw
}

const uploadManual = async () => {
  if (!uploadForm.value.productId) {
    ElMessage.warning('请选择产品')
    return
  }
  
  if (!selectedFile.value) {
    ElMessage.warning('请选择PDF文件')
    return
  }
  
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    formData.append('productId', uploadForm.value.productId)
    
    const response = await api.manuals.upload(formData)
    if (response.success) {
      ElMessage.success('上传成功，AI 训练已开始（转曲 PDF 也可读取）')
      uploadForm.value.productId = null
      selectedFile.value = null
      uploadRef.value.clearFiles()
      loadData()
    }
  } catch (error) {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

const showTrainingResult = (row) => {
  resultManual.value = row
  resultVisible.value = true
}

const deleteManual = async (manual) => {
  try {
    await ElMessageBox.confirm('删除后可重新上传说明书进行训练。是否删除？', '删除手册', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await api.manuals.delete(manual.id)
    ElMessage.success('已删除')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const confirmManual = async (manual) => {
  try {
    await ElMessageBox.confirm(
      '确认后，安全警告和保修条款将被锁定，无法修改。是否继续？',
      '确认锁定',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const response = await api.manuals.confirm(manual.id, {
      extractedProductName: manual.extractedProductName,
      extractedModel: manual.extractedModel,
      extractedBatteryInfo: manual.extractedBatteryInfo,
      extractedPowerInfo: manual.extractedPowerInfo
    })
    
    if (response.success) {
      ElMessage.success('确认成功')
      loadData()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('确认失败')
    }
  }
}

onMounted(() => {
  loadData()
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.train-cell { display: flex; flex-direction: column; }
.train-msg {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}
.section-title {
  margin: 18px 0 10px;
  font-size: 14px;
  color: #303133;
}
.digest-text {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 10px 14px;
  font-size: 13px;
  color: #606266;
  white-space: pre-wrap;
  max-height: 160px;
  overflow: auto;
}
</style>
