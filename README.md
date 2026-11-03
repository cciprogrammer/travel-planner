# 智能旅行规划系统（AI Travel Planner）

基于大模型的旅行行程规划 Web 系统。用户输入出发地、目的地、天数、预算与偏好，系统调用 DeepSeek 生成**结构化、可校验、可编辑**的行程方案，并支持地图可视化、预算实时核算、方案管理与导出。

与传统「让模型写一篇攻略」的做法不同，本项目把模型输出约束成固定字段的 JSON，再用 11 条行程质量标准逐条校验，形成 **自动校验 → 定向重写 → 降级兜底** 的三级处理链路，使生成结果从「看起来还行」变成「可判定、可追溯」。

---

## 一、主要功能

| 模块 | 说明 |
| --- | --- |
| 行程生成 | 目的地 / 天数 / 预算 / 兴趣 → 大模型生成结构化行程（交通、住宿、每日动线、预算、提示） |
| 质量校验 | 11 条规则（R1–R11）逐条判定；不合格自动修复、定向重写或降级，并保留处理轨迹 |
| 地图可视化 | 腾讯地图渲染每日动线，支持地点搜索、逆地理编码、酒店点选，路线随住宿基点重排 |
| 预算核算 | 推荐酒店按「预算内单价最高」匹配；自选住宿可填房价，总预算实时重算 |
| 方案管理 | 历史列表、详情、编辑、重新生成、Markdown 导出 |
| 账号体系 | 注册、登录、改密、资料与头像；JWT 双拦截器（登录态 + 管理员角色） |
| 收藏 | 地点收藏与查看 |
| 管理后台 | 酒店库维护、用户与方案统计 |

---

## 二、技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.2.5、MyBatis-Plus 3.5.7、JJWT 0.12.3、Bean Validation |
| 前端 | Vue 3.5（Composition API）、Vite 5、Element Plus 2.8、Pinia、Vue Router 4、Axios |
| 数据库 | MySQL 8（InnoDB / utf8mb4），行程主体以 JSON 字段存储 |
| 大模型 | DeepSeek `deepseek-chat`（OpenAI 风格 `/chat/completions`） |
| 地图 | 腾讯位置服务 JavaScript API GL |
| 测试 | JUnit 5 + Mockito、Python 标准库接口脚本、Shell 冒烟脚本 |

---

## 三、目录结构

```
travel-planner/
├─ backend/                             # Spring Boot 后端
│  ├─ pom.xml
│  └─ src/
│     ├─ main/java/com/dgcz/travel/
│     │  ├─ controller/                 # Auth / Plan / Favorite / User / Admin
│     │  ├─ service/                    # 业务编排、大模型调用、质量校验与三级链路
│     │  │  ├─ PlanService.java
│     │  │  ├─ ItineraryPipeline.java   # L1 校验/修复 → L2 重写 → L3 兜底
│     │  │  ├─ ItineraryStandard.java   # R1–R11 判定、修复与纠错指令
│     │  │  └─ DeepSeekService.java
│     │  ├─ mapper/ entity/ dto/        # 持久层、实体、传输对象（含行程结构定义）
│     │  ├─ common/                     # 统一响应、异常、用户上下文
│     │  ├─ config/ interceptor/ util/  # 配置、鉴权拦截器、JWT 工具
│     │  └─ resources/
│     │     ├─ application.yml
│     │     ├─ application-local.example.yml
│     │     └─ mock/                    # 内置示例行程（模型不可用时兜底）
│     └─ test/java/                     # 单元测试（76 个用例）
├─ frontend/                            # Vue 3 前端
│  ├─ package.json  vite.config.js  index.html  .env.example
│  └─ src/
│     ├─ api/                           # 按模块拆分的接口封装
│     ├─ components/                    # ItineraryMap（地图）、AnchorNav（锚点导航）
│     ├─ views/                         # 登录、首页、方案列表/详情、收藏、个人中心、后台
│     ├─ router/ stores/ utils/         # 路由守卫、状态管理、请求与地图工具
│     └─ styles.css                     # 设计令牌（统一主色与间距）
├─ sql/
│  ├─ schema.sql                        # 建库建表
│  └─ upgrade-admin.sql                 # 旧库增量升级（幂等）
├─ scripts/                             # 启动、状态、冒烟、黑盒、自检脚本
├─ start.cmd                            # Windows 一键启动
└─ stop.cmd                             # Windows 一键停止
```

---

## 四、快速开始

### 4.1 环境要求

- JDK 17（**注意**：系统默认 `java` 可能是 11，请显式指定 `JAVA_HOME`）
- Maven 3.8+
- Node.js 18+
- MySQL 8.0

### 4.2 初始化数据库

```bash
mysql -u root -p < sql/schema.sql
```

脚本会创建 `travel_planner` 库与 4 张表（用户、方案、收藏、酒店）。已有旧库可用 `sql/upgrade-admin.sql` 增量升级。

### 4.3 配置后端

```bash
cd backend/src/main/resources
cp application-local.example.yml application-local.yml
```

编辑 `application-local.yml`，填写数据库口令与大模型 Key。**该文件已被 `.gitignore` 忽略，不会被提交。**

也可以通过环境变量注入（推荐）：

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `DB_USERNAME` / `DB_PASSWORD` | 是 | 数据库账号与口令 |
| `DEEPSEEK_API_KEY` | 是 | DeepSeek API Key；不配置则只能使用内置示例行程 |
| `JWT_SECRET` | 是 | JWT 签名密钥，长度 ≥ 32 字节 |
| `ADMIN_PASSWORD` | 否 | 启动时创建管理员账号所需；**留空则跳过创建** |
| `DEEPSEEK_MODEL` | 否 | 默认 `deepseek-chat`（推理模型易截断长 JSON，不建议更换） |
| `DEEPSEEK_MOCK` | 否 | `true` 时强制使用内置示例，便于本地演示 |

### 4.4 启动后端

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

或打包运行：

```bash
mvn clean package -DskipTests
java -Dfile.encoding=UTF-8 -jar target/travel-planner-0.0.1-SNAPSHOT.jar \
     --server.port=8081 --spring.profiles.active=local
```

> 默认端口 8080；若被占用，用 `--server.port=8081` 覆盖，同时把前端代理指向同一端口。

### 4.5 启动前端

```bash
cd frontend
npm install
cp .env.example .env.development   # 填入 VITE_TMAP_KEY（可选，用于地图）
npm run dev
```

打开 `http://127.0.0.1:5173`。开发服务器会把 `/api` 代理到 `http://localhost:8081`，可用 `VITE_PROXY_TARGET` 覆盖。

### 4.6 Windows 一键启动

双击 `start.cmd`：脚本会自动探测 JDK 与 Node、检查 MySQL、按顺序拉起后端与前端，等健康检查通过后再打开浏览器。停止用 `stop.cmd`。

---

## 五、行程质量校验（核心设计）

生成结果不是「拿到就用」，而是先过一遍校验：

| 规则 | 要求 | 级别 |
| --- | --- | --- |
| R1 | 地点必须具体，不得用「核心景区」等泛称 | 阻断 |
| R2 | 每日事项 ≥ 3 | 警告 |
| R3 | 当日时间非递减 | 阻断 |
| R4 | 每项写清点对点交通 | 阻断 |
| R5 | 每日交通小结 | 阻断 |
| R6 | 城际往返含交通方式 | 阻断 |
| R7 | 市内交通建议 ≥ 2 种 | 阻断 |
| R8 | 住宿 ≥ 3 家、含推荐、预算内有可选项 | 阻断 |
| R9 | 天数与请求一致 | 阻断 |
| R10 | 预算含分项与合计 | 警告 |
| R11 | 购物推荐 2–5 处（不设额度） | 阻断 |

未通过时按优先级处理：

1. **L1 自动修复**：时间乱序（R3）、天数不符（R9）、酒店标签缺失（R8 部分）就地修复并复检；
2. **L2 定向重写**：机器补不出的问题（R1/R4/R5/R6/R7/R11）按规则分组生成纠正指令，连同原输出回灌模型重写一次；
3. **L3 降级兜底**：重写后仍不达标或模型不可用时，改用内置示例并标注原因。

每次生成都会在 `itinerary.quality.stages` 留下逐阶段轨迹（触发原因、修复前后阻断数、是否采纳），前端据此展示校验结果。**生成失败不阻塞用户，但绝不隐瞒。**

---

## 六、测试

```bash
# 单元测试（76 个用例）
cd backend && mvn test

# 接口级黑盒（56 条，可跳过真实大模型调用）
python scripts/blackbox_test.py --no-llm

# 冒烟测试（8 个核心接口）
bash scripts/smoke-test.sh
```

单元测试包括：行程质量规则（R1–R11）、三级链路三态矩阵（11 条 × 通过/需重写/需兜底）、JWT 鉴权与拦截器、酒店与用户服务。

---

## 七、接口概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/register` / `login` | 注册 / 登录 |
| GET | `/api/auth/me` | 当前用户 |
| POST | `/api/plans/generate` | 生成行程 |
| GET | `/api/plans` / `/{id}` | 方案列表 / 详情 |
| PUT | `/api/plans/{id}` | 更新方案（含住宿与预算） |
| POST | `/api/plans/{id}/regenerate` | 重新生成 |
| GET/POST/DELETE | `/api/favorites` | 收藏管理 |
| PUT | `/api/user/profile` / `password` | 资料 / 改密 |
| GET/POST/PUT/DELETE | `/api/admin/hotels` | 酒店管理（需管理员） |
| GET | `/api/admin/users` / `stats` | 用户与统计（需管理员） |

业务错误统一返回 HTTP 200 + `code` 字段（拦截器鉴权失败除外），前端按 `code` 判定。

---

## 八、说明

- 本项目为个人学习与毕业设计作品，未做生产级加固；如需上线，请务必替换 `JWT_SECRET`、关闭 `mock` 兜底、补充限流与审计。
- 仓库中不含任何真实密钥、数据库口令或环境变量文件；`application-local.yml`、`.env.*`、日志与构建产物均已被 `.gitignore` 排除。
- 地图底图与地理编码服务由腾讯位置服务提供，需自行申请 Key（JavaScript GL）。
