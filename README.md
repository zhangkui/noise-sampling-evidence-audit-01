# 城市噪声传感器采样证据链管理系统

管理城市噪声传感器、采样批次、噪声记录、异常事件、证据版本与审计日志的前后端一体化系统。
原始采样数据**只增不改**；异常状态变更、处理说明、证据关联全部写入**哈希链式、不可篡改的审计日志**，
支持按任意记录回放完整变更历史并校验链完整性。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.2、Spring Security、MyBatis-Plus 3.5、MySQL 8、Redis 7、JWT |
| 前端 | Vue 3、TypeScript、Vite 5、Element Plus、Pinia、Vue Router、Axios、ECharts 5 |
| 部署 | Docker Compose（多阶段构建，**无需本机预装 Java / Node / MySQL / Redis**） |

## 一、快速启动

```bash
docker compose up --build
```

首次启动会自动：

1. 启动 MySQL 8（自动建库，应用启动时执行 `schema.sql` 建表）与 Redis 7；
2. 编译启动后端（等待 MySQL/Redis 健康检查通过后才启动）；
3. 构建启动前端（Nginx 托管静态资源并反向代理 `/api`）；
4. 后端检测到空库，自动初始化演示数据（用户 / 传感器 / 采样批次 / 噪声记录 /
   异常事件 / 证据版本 / 审计哈希链 / 一个混合成败的导入任务）。

> 重新执行不会重复初始化（检测到 admin 账号即跳过）。想重置演示数据：
> `docker compose down -v && docker compose up --build`。

### 访问地址

| 服务 | 地址 | 说明 |
|---|---|---|
| 前端 | http://localhost | 推荐从这里访问 |
| 后端 API | http://localhost:8080/api | 健康前缀，接口均在 `/api/**` |
| MySQL | localhost:**3307** | 账号 noise / noise_pass_2026，库 noise_chain（root: root_pass_2026） |
| Redis | localhost:**6380** | 无密码 |

宿主机端口冲突时可复制 `.env.example` 为 `.env` 修改 `*_PORT_PUBLISH`。

### 测试账号

| 账号 | 密码 | 角色 |
|---|---|---|
| admin | admin123 | 管理员 |
| operator | operator123 | 操作员 |

## 二、核心业务规则与实现

- **防重复入库（三重保障）**
  1. Redis `SET NX` 分布式锁，按 `传感器编号:采样时间:原始哈希` 指纹串行化并发提交；
  2. 锁内事务执行「先查重再插入」，事务提交后才释放锁；
  3. 数据库唯一键 `uk_sensor_time_hash(sensor_code, sample_time, raw_data_hash)` 兜底。
- **采样批次重叠检测**：同一传感器时间区间重叠（`start < existing.end AND end > existing.start`）
  拒绝创建；提供 `/api/batches/overlap-check` 查询接口；创建路径用传感器维度 Redis 锁防并发竞态。
- **采样批次关闭**：仅允许 `ACTIVE → CLOSED`；重复关闭**幂等**返回当前状态、不追加重复审计节点；
  状态变更与 before/after 快照审计（含操作人、客户端 IP、traceId）在同一事务内原子提交，
  批次维度 Redis 锁 + 数据库条件更新（`WHERE status='ACTIVE'`）双重防并发。
- **批次门禁**：新增记录指定批次时，批次必须存在且为 `ACTIVE`、传感器匹配、采样时间落在区间内；
  任一校验失败都返回统一业务错误，事务整体回滚，不留记录、异常、证据或审计残留。
- **异常自动生成**：分贝 `> 85 dB(A)` 自动生成 `HIGH_DECIBEL` 异常；
  唯一键 `uk_record_type(record_id, anomaly_type)` 保证同一记录不重复生成同类型异常。
- **证据版本哈希链**：记录创建即固化证据 v1；证据只能追加新版本，
  `evidenceHash = SHA256(prevHash | 规范化负载)`。
- **审计哈希链**：每条记录一条独立链，`entryHash = SHA256(prevHash | seq | 实体 | 动作 | 变更前 | 变更后 | 操作人)`；
  链头以 `SELECT … FOR UPDATE` 行锁推进，审计与业务变更在**同一事务**内原子提交。
  回放时逐节点重算哈希，任何篡改都会导致 `chainValid=false` 并指出断裂位置。
- **批量导入**：逐行字段校验（精确到字段的中文原因）+ 每行独立事务（`REQUIRES_NEW`），
  失败行回滚不写库、成功行保留；任务与逐条结果持久化，可在页面回看。
- **查询筛选**：分页 + 时间范围 + 传感器编号 + 异常状态（正常/有异常/各处理状态）+ 分贝区间。
- **统一响应与异常**：`Result{code,message,data}`，参数校验、唯一键冲突、业务异常、
  认证失败均有统一错误码；JWT 过期前端自动跳登录。

## 三、页面与功能

| 页面 | 功能 |
|---|---|
| 采样记录 | 多条件筛选、分页、手工录入、**并发重复提交演示**按钮、行点击进入详情 |
| 记录详情 | 原始记录只读展示、异常事件、证据版本表、**审计时间线**（可展开前后快照与哈希）、链校验状态、追加证据 |
| 异常处理 | 按状态筛选、处理弹窗（状态 + 处理说明，防重复提交） |
| 批量导入 | 在线编辑表格 / JSON 粘贴 / 模板下载、提交后**逐条成功失败与原因**、历史任务及明细页 |
| 分贝趋势 | ECharts 小时级平均/最大/最小分贝曲线 + 85dB 阈值标线，支持图表/数据表切换 |
| 传感器与批次 | 传感器维护、批次维护、**时间重叠冲突检测**、**批次关闭（确认/加载态/失败重试）** |
| 审计日志 | 全局审计检索（记录/实体/动作）、查看快照、一键跳转记录链回放 |

所有列表页均完整处理：加载中、空数据、接口失败（含重试）、分页边界与按钮防重复提交。

## 四、演示步骤（界面）

先用 `admin / admin123` 登录 http://localhost。

### 1. 重复提交

「采样记录」→「手工录入」，填表后连续快速点击两次「提交」（或提交成功后原样再提交一次）：
第二次返回 **40901 重复提交：该传感器在相同采样时间与相同数据哈希下已存在记录**，列表中只有一条数据。

### 2. 并发写入

「采样记录」页点击 **「并发重复提交演示」**：前端同时发出 5 个完全相同的请求，
提示「仅 1 条入库，4 条被判定为重复/并发冲突」，数据库无重复。

### 3. 采样批次时间冲突

「传感器与批次」→「采样批次」→「新增批次」，传感器选 `S-NJ-001`，
时间选 **2026-09-01 09:00 ~ 2026-09-01 10:30**（与内置批次 08:00~12:00 重叠）：
点击「仅检测冲突」直接列出冲突批次；点「创建」返回 **40902 时间冲突**。

### 4. 采样批次关闭与批次门禁

「传感器与批次」→「采样批次」→ 对「进行中」批次点击「关闭」并确认：状态立即变为「已关闭」。
再次关闭（或重复调用接口）幂等返回当前状态，审计日志中只会有一个 `BATCH_CLOSE` 节点
（含关闭前后快照、操作人、IP、traceId，可在「审计日志」按实体类型 `BATCH` 检索）。
此后「采样记录」→「手工录入」若仍指定该批次提交，返回
**40905 采样批次已关闭，禁止写入新记录**，且不留记录、异常、证据或审计残留。

### 5. 异常自动生成

「采样记录」→「手工录入」，分贝填 **90**（大于 85）提交：
列表中该记录状态变为「待处理」；「异常处理」页出现一条 OPEN 异常；
打开记录详情可见审计时间线为 `CREATE → EVIDENCE_LINK`。
对同一条记录再次提交（同时间同哈希）会被判重，**不会**产生第二条异常。

### 6. 异常处理与审计

「异常处理」→「处理」→ 选择已解决并填写说明 → 提交。
再进入该记录「证据链/详情」：时间线新增 `状态变更` 节点（含变更前后快照、处理说明），
页面顶部显示 **「哈希链校验通过」**。

### 7. 追加证据并回放

记录详情 →「追加证据版本」→ 填写地址与说明。时间线追加 `证据关联` 节点，
证据版本表出现 v2，`prevHash` 指向 v1。整条链（记录创建 → 异常 → 证据 v1/v2 → 状态变更）
按序回放、逐节点校验。

### 8. 批量导入逐条结果

「批量导入」页直接「提交导入」（内置两行待填可自行补全），或使用接口脚本。
结果区逐行显示成功/失败与原因；系统初始化时还自带一个混合任务：
1 条成功、1 条库内重复、1 条分贝 250 非法、1 条传感器不存在 —— 后三行均未写库。

### 9. 审计不可篡改验证（可选）

```sql
-- 直连 MySQL：故意篡改一条历史审计（mysql -h127.0.0.1 -P3307 -unoise -pnoise_pass_2026 noise_chain）
UPDATE audit_log SET action='HACKED' WHERE id = 2;
```

刷新对应记录详情，顶部立即变为 **「哈希链异常：条目哈希被篡改: seq=2」**，`chainValid=false`。
执行 `docker compose restart backend` 后演示数据不会重置，告警依旧存在。

## 五、命令行演示脚本（可选）

```bash
bash demo/api-demo.sh                 # 默认 http://localhost:8080
BASE_URL=http://localhost:8080 bash demo/api-demo.sh
```

脚本依次演示登录、重复提交、时间冲突、5 路并发写入、异常生成与处理、审计链回放、
批量导入逐行失败原因（仅依赖 curl；有 jq 时输出更易读）。

## 六、主要接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/auth/login | 登录获取 JWT |
| GET | /api/records | 分页+时间/传感器/异常状态/分贝区间筛选 |
| POST | /api/records | 单条录入（重复/并发保护，超阈值自动建异常） |
| GET | /api/records/{id} | 记录详情 |
| GET | /api/records/trend/chart | 分贝小时趋势 |
| GET | /api/batches/overlap-check | 批次时间重叠检测 |
| POST | /api/batches | 创建批次（重叠拒绝） |
| POST | /api/batches/{id}/close | 关闭批次（幂等，写 before/after 快照审计） |
| POST | /api/imports | 批量导入（逐行结果） |
| GET | /api/imports、/api/imports/{id}/items | 导入任务与明细 |
| POST | /api/anomalies/{id}/handle | 异常处理（写审计） |
| POST | /api/records/{id}/evidences | 追加证据版本（写审计） |
| GET | /api/audit/records/{id}/history | **记录完整变更历史 + 链校验** |
| GET | /api/audit/logs | 审计日志检索 |

## 七、目录结构

```
.
├── backend/                Spring Boot 3 + MyBatis-Plus
│   ├── src/main/java/com/citynoise/evidence/
│   │   ├── audit/          审计/证据哈希链服务
│   │   ├── common/         统一响应、异常、Redis 锁、哈希工具
│   │   ├── config/         Security/MyBatis/Redis 配置
│   │   ├── controller/ … service/ … mapper/ … entity/ … dto/
│   │   └── init/           演示数据初始化
│   ├── src/main/resources/db/schema.sql   自动建表
│   └── Dockerfile
├── frontend/               Vue 3 + TS + Element Plus
│   ├── src/views/ … api/ … stores/ … router/ … components/
│   ├── nginx.conf
│   └── Dockerfile
├── demo/api-demo.sh        接口演示脚本
└── docker-compose.yml
```

## 八、本地开发（可选，不需要 Docker 时）

后端：JDK17 + Maven，`mvn spring-boot:run`（需自行准备 MySQL/Redis 或指向 compose 中的：
`MYSQL_HOST=localhost REDIS_HOST=localhost`，映射端口 3307/6380）。

前端：Node 20，`npm install && npm run dev`（Vite 已配置 `/api` 代理到 localhost:8080）。
