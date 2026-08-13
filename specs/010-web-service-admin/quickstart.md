# 验收指南:Web Service 与第一版管理平台

## 自动化门禁(机器判卷)

```bash
# 全量构建 + 静态检查(P3C/SpotBugs/FindSecBugs/PMD)+ 全部单测
mvn clean verify
# 预期:BUILD SUCCESS,三个新测试类全绿

# 只跑本节 Web 层测试
mvn test -pl oryxos-web -Dtest='SessionApiControllerTest,GlobalExceptionHandlerTest'

# 集成冒烟(真上下文,不依赖模型;@Tag("integration") 不进默认构建)
mvn test -pl oryxos-boot -Dtest=WebSmokeIT
# 预期:/health、/info、/profiles、/tools 均 200,信封字段完整
```

## 启动与真链路人工项(harness 判不了,留给用户)

```bash
# 前置:DEEPSEEK_API_KEY 环境变量;先构建(前端由 frontend-maven-plugin 自动构建并进 fat JAR,
# 需可下载 node;仅后端迭代可加 -Dfrontend.skip=true)
mvn clean package -DskipTests

# 启动(或 oryxos serve)
java -jar oryxos-boot/target/*.jar serve

# 10+1 个端点 curl 真链路
curl -X POST localhost:8080/api/v1/sessions \
     -H 'Content-Type: application/json' -d '{"profile_name":"<某Agent>"}'
# → {"code":200,...,"data":{"session_id":"web:anonymous:<某Agent>"}}

curl -X POST localhost:8080/api/v1/sessions/<session_id>/messages \
     -H 'Content-Type: application/json' -d '{"content":"今天北京天气怎么样"}'
# → 真模型回复(审计表 llm_calls / tool_invocations 有账)

curl localhost:8080/api/v1/sessions          # 会话列表
curl localhost:8080/api/v1/sessions/<id>     # 历史(≤100 条)
curl -X DELETE localhost:8080/api/v1/sessions/<id>
curl -X POST localhost:8080/api/v1/agents/<Agent>/invoke \
     -H 'Content-Type: application/json' -d '{"content":"你好"}'
curl localhost:8080/api/v1/profiles
curl localhost:8080/api/v1/memory
curl localhost:8080/api/v1/tools
curl localhost:8080/api/v1/health
curl localhost:8080/api/v1/info              # providers 连通状态

open http://localhost:8080/admin             # 管理台五页,只读
open http://localhost:8080/swagger-ui.html   # 接口文档
```

## 前端改动流程(插件构建,产物不入库)

```bash
# 前端改动后直接打整包(插件自动 install → build)
mvn clean package -DskipTests
# 本地快速预览前端(可选):
cd oryxos-web/src/main/frontend && npm run dev
# 跳过前端构建(纯后端迭代):mvn ... -Dfrontend.skip=true
```

## 人工项清单(课件"做完怎么验"剩余项)

1. 10 端点 + 会话列表 curl 真链路走通(含真模型发消息),审计有账
2. CLI 聊过的 Session 从 `GET /sessions/{id}` 能查到(两个人推入口共享存储)
3. 断掉 Provider(如临时改错 base-url)拿 503
4. 构造超 60 秒调用拿 504(需真实故障注入,切片测试模拟不了完整链路)
5. 200 并发 invoke 压测,virtual thread 扛得住
6. 管理台五页渲染真实数据、无写操作入口;空/加载/错误三态占位
7. 400/404 映射、错误格式统一、500 不泄漏——harness 覆盖,mvn test 绿即打勾

## 收尾文档同步(用户已确认,随本节收尾执行)

- 宪法/TS §7.2/CLAUDE.md:"10 个端点" → "核心阶段 10 个端点(会话列表为只读扩展)"类表述;宪法改动按 Amendment 流程附变更说明
- TS §7.2/CLAUDE.md:"查长期记忆(MEMORY.md)" → "长期记忆全文"
