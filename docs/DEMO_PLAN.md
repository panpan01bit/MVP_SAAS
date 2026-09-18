# ToolFix 本地演示版改造计划

> 目标：基于真实产品说明书，打造一套可一键启动、断网可讲、有真实 AI 能力的本地 Demo。
> 状态标记：[x] 已完成 / [ ] 待办

## 需求决议

| # | 决议项 | 结论 |
|---|--------|------|
| 1 | AI 方式 | **真实 LLM**：智谱 GLM（Coding Plan）`glm-4.5v` 视觉 + `glm-4.6` 文本 |
| 2 | 体验账户 | 后台演示账号 `demo@toolfix.com / demo123` + H5 一键体验入口 |
| 3 | 运行方式 | **Docker Compose 一键起**（修复 vite base 白屏 Bug） |
| 4 | 素材 | 真实说明书：`FD11040711 冲击钻`(16页)、`FD11050751 角磨机`(转曲)；双语策略：后台中文 / H5 客户端英文 |

## 关键技术决策

### 手册"转曲"问题 → 视觉模型路线
两份真实 PDF 均无法提取文字（字体转曲线）。方案：
1. PDFBox `PDFRenderer` 将每页渲染为 PNG（最长边 1500px）
2. 单页单次调用 `glm-4.5v`（`thinking: disabled`），4 路并发（实测 16 页约 6 分钟）
3. 逐页摘要合并 → 文本模型生成结构化 JSON：产品信息 + **可能问题清单**（约 7 假故障 + 3 真故障，双语）
4. 问题清单入库 `KnowledgeBase`（`SKU_SPECIFIC` 类型，挂产品 SKU）

> 已实测：《FD11040711 冲击钻说明书》16 页转曲 PDF 全链路通过，AI 准确提取出
> 零件编号(No.5/No.8/No.28)、BS 1362 保险丝、12 个月保修等手册细节。

### "训练"演示状态机
`UPLOADED → READING(页 n/总 N) → GENERATING → DONE / FAILED`
后台手册详情页实时展示进度，完成后列出 AI 生成的问题并可发布到知识库。

## 改造清单

### P0 基础设施
- [x] `.env` / `.env.example` 建立并验证 Key（视觉+文本双通过）
- [x] vite `base` 可配置化（修复 Docker 白屏）
- [x] docker-compose 接入 `.env`（LLM/HMAC/演示账号注入 backend）
- [x] 后端读取 `toolfix.llm.*` 配置

### P1 核心 AI 能力
- [x] `LlmService`：GLM API 客户端（视觉+文本，OpenAI 兼容协议，新建连接+重试）
- [x] 视觉手册解析管线（4 路并发逐页读图，保留 Mock 作为 fallback）
- [x] 可能问题生成 + 自动入库知识库
- [x] 诊断对话接入文本模型（高危词拦截优先级最高，实测通过）
- [x] 训练状态机 + 进度展示接口（UPLOADED→READING→GENERATING→DONE/FAILED）

### P2 演示体验
- [x] 后台登录页 + 演示账号 `demo@toolfix.com/demo123` + 拦截器鉴权（H5 诊断接口保持公开）
- [x] H5 "Start Free Demo" 一键体验入口
- [x] 种子数据替换为真实产品（FD11040711 冲击钻 / FD11050751 角磨机）
- [ ] 预置一份已"训练完成"的产品数据（首次训练后数据留存即可，无需额外开发）

### P3 顺手修的 Review 问题
- [x] 上传文件名清洗（路径穿越）— DiagnosisController + ManualController
- [x] `Shop.accessToken` / `DiagnosisSession.secureToken` 加 `@JsonIgnore`
- [x] feedback/resolve 补 token 校验
- [x] `/sessions/stats` 无 shopId 时返回全局统计
- [x] 【额外发现】高危词拦截器首启加载 0 关键词的时序 Bug（DataSeeder 播种后强制刷新）
- [x] 【额外发现】已转人工/已解决会话仍可继续 AI 对话的逻辑漏洞
- [x] 【额外发现】FALSE_FAULT_GUIDE 知识库未命中时客户卡死（回退转人工）

## 演示脚本（建议）

1. **开场**：Docker Compose 一键启动，登录后台 `demo@toolfix.com`
2. **训练**：上传《冲击钻说明书》PDF → 观众看 AI 逐页读手册（转曲也能读）→ 生成"客户可能遇到的 10 个问题"
3. **发布**：一键发布到知识库
4. **客户视角**：H5 扫码/Try Demo → 英文对话诊断 → AI 用刚学的手册知识解题 → 说"smoke"触发高危拦截转人工
5. **后台闭环**：人工回复 → 仪表盘看拦截率/AI 成本统计
