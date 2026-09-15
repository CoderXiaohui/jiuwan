# 验证记录

验证日期：2026-09-15。

## 本次新增：炸金花（2026-09-15）

- 后端共 **44 项测试通过**：原有平台 29 项，加 1 项炸金花房间集成测试、14 项炸金花专项测试。
- 穷举 **22,100 种**三张牌组合，校验六类牌型数量；覆盖 A23、QKA、KA2、对子踢脚、花色平局、235 开关及三方循环关系下的一对一淘汰。
- 覆盖 17 人不重复发牌、18 人拒绝、行动权限、陈旧回合、截止时间、明暗倍数、加注上限、决胜阶段、超时弃牌、积分守恒和下一局轮换先手。
- 覆盖看牌前不下发底牌、看牌后只本人可见、结算不公开弃牌、Redis JSON 往返恢复、重连及重复 requestId 幂等。
- 前端 TypeScript strict、Vite 生产构建和 Prettier 格式检查通过。
- 生产入口浏览器测试 **3/3 通过**：原有五款游戏流程、首页四种宽度、新增炸金花三人流程。
- 炸金花使用三个独立 Chromium 上下文（375 / 390 / 430px），实测闷牌、非本人行动时看牌、跟注、加注、弃牌、比牌、结算、下一局、酒局 / 235 设置切换；REST 验证私密字段，WebSocket 驱动整个页面。
- 实测刷新保留手牌、离线后恢复；检查浏览器无脚本错误。检查 1440px 牌桌布局、三个手机宽度无页面横向溢出，牌面不会超出座位边框。
- 截图检查后缩小窄屏座位中的扑克牌，人数较多时玩家区域可滚动；结算后弃牌者仍能在自己的独立手牌区域看牌，其他玩家看不到。针对这两处调整重跑炸金花三人测试。
- 真实 REST / WebSocket 协议冒烟检查通过；Docker 服务已重新构建，前端、后端、Redis 均 healthy。

稳定截图：[手机牌桌](screenshots/zhajinhua-mobile.png)、[桌面牌桌](screenshots/zhajinhua-desktop.png)、[结算页](screenshots/zhajinhua-result.png)。

以上是实际本地服务配合浏览器视口模拟，未声称完成实体手机或微信浏览器测试。

## 原有 MVP 验证

- Java 21 + Maven：29 项业务测试通过，0 失败 / 0 跳过。
- 前端：TypeScript strict 检查与 Vite 生产构建通过。
- 浏览器：两个独立 Chromium 上下文，分别模拟 390px 和 375px 手机，完成创建 / 加入 / 昵称 / 房间同步 / 五款游戏 / 下一局 / 换游戏。
- 浏览器：投票后刷新保持身份和本局；离线 → 在线后自动恢复；房主刷新仍是房主；房主离开后由另一玩家接管；最后关闭房间。
- 显示：375、390、430、1440px 页面没有意外横向溢出；首页规则弹窗与 Escape 关闭正常。
- 实际协议：REST 鉴权与房主权限、WebSocket 拒绝伪造 playerId、投票隐藏其他人选择、重复 requestId 幂等、私有选择重连恢复、并列结果和关闭事件通过。
- Docker：前端 Nginx、后端 Java、Redis 镜像构建及启动成功，三个容器均 healthy；生产入口 http://localhost:8088 的上述双人流程与协议检查通过。
- 本机 OrbStack 关闭了容器端口的局域网暴露，使用 `scripts/lan-proxy.mjs` 为本项目提供独立 Wi-Fi 地址入口。
- 经该入口访问 `http://192.168.110.97:8088`，两个独立浏览器上下文的全游戏流程及四种宽度检查通过（2/2）。该地址来自验证时的 Wi-Fi IP，换网络后需更新。

双人流程使用真实 Java / Redis / WebSocket，无模拟接口。手机布局使用浏览器视口模拟，未声称已在所有实体手机 / 微信浏览器上测试。

## 联调修复

网络恢复时，旧 WebSocket 的 onclose 可能晚于新连接建立，覆盖当前连接引用。客户端现在按 WebSocket 实例过滤过时回调；服务端离线检查也在房间锁内确认是否已有替代连接，避免刷新把新连接误标成离线。修复后双人全流程重新通过。

## 可复现命令

```bash
# 后端测试
cd backend && mvn test

# 前端构建
cd frontend && npm ci && npm run build

# 启动整个应用（项目根目录）
docker compose up -d --build --wait

# 真正的 REST / WebSocket 协议检查（项目根目录，Node >=22）
node scripts/protocol-smoke.mjs

# 生产入口端到端测试
cd frontend
npx playwright install chromium
E2E_BASE_URL=http://localhost:8088 npm run test:e2e
```

端到端测试结果与失败 trace 在 `frontend/test-results/`，该目录已忽略。稳定预览图：

- [桌面首页](screenshots/home-desktop.png)
- [390px 手机首页](screenshots/home-mobile.png)

## 已知边界

- 单后端实例，不能直接多副本共享房间数据。
- 活跃房间状态存 Redis；关闭保留 15 分钟，无在线玩家 30 分钟后关闭。
- 游戏进行时只允许已有身份恢复，新玩家等回到大厅再加入。
- 第一版提供链接 / 房间码邀请，二维码入口预留。
- PWA 仅提供离线提示，不支持离线计算多人游戏结果。
