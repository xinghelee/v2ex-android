# V2EX for Android

面向个人使用的 V2EX Android 客户端。Kotlin + Jetpack Compose（Material 3）构建，是 [iOS 版](../v2ex) 的移植，数据来自 V2EX API、网页会话和 sov2ex 搜索。

## 功能对齐（移植进度）

- **首页**：全部 / 最热 / 关注 / 快捷节点分类横滑，推广内容过滤
- **话题**：正文与楼层回复渲染（段落 / 代码 / 引用 / 图片提取为独立块）、只看楼主、楼层引用解析
- **节点**：完整节点搜索 + 9 大分类目录（与 iOS 相同的硬编码分类），关注节点管理
- **账户**：WebView 网页登录（与 iOS 一致——验证码 / 2FA 由 V2EX 自己的页面处理），Personal Access Token 手动配置；凭据存于 Android Keystore 加密存储，密码不落盘
- **通知**：PAT 驱动，回复 / @ / 感谢分类，客户端已读状态，v1 接口头像回填
- **搜索**：sov2ex 全文索引
- **写作**：Markdown 草稿自动保存（Room），回复可直接发送，发主题引导到网页完成
- **内容治理**：首启条款闸门、举报与屏蔽（关键词 / 用户），举报回传与 iOS 共用同一 Cloudflare Worker 端点
- **外观**：iOS 同款 5 套配色（翡翠绿 / 海洋蓝 / 绯红 / 琥珀橙 / 紫罗兰，HEX 逐一对齐），明暗模式，字号 / 行距 / 等宽字体设置

## 技术栈

| 模块 | 实现 |
| --- | --- |
| UI | Jetpack Compose + Material 3，Navigation Compose（类型安全路由） |
| DI | Hilt |
| 网络 | Retrofit + OkHttp + kotlinx-serialization；API 2.0 信封解包；Cloudflare 缓存破坏（`_=<epoch-ms>`） |
| HTML | Jsoup 解析 `content_rendered` 为 ContentBlock 渲染树（对应 iOS 自研解析器） |
| 图片 | Coil 3 |
| 存储 | Room（草稿 / 收藏 / 历史 / 屏蔽 / 举报 / 离线）、DataStore（设置）、EncryptedSharedPreferences（PAT / 会话 Cookie，对应 iOS Keychain） |

## 构建

```bash
# 调试构建
./gradlew :app:assembleDebug

# 安装到已连接设备/模拟器
./gradlew :app:installDebug
```

要求：JDK 17+、Android SDK Platform 37。AGP 9.x 内置 Kotlin 支持（无需单独的 kotlin-android 插件）。

## 项目结构

```
app/src/main/kotlin/com/vibe/v2ex/
├── data/
│   ├── datastore/    # 设置、加密凭据、关注节点
│   ├── local/        # Room 实体与 DAO
│   ├── model/        # API 数据模型
│   ├── moderation/   # 举报与屏蔽（UGC 治理）
│   ├── nodes/        # 硬编码节点分类目录
│   ├── remote/       # V2EX v1/v2 API、sov2ex、网页会话
│   └── repository/
├── designsystem/     # 主题（5 套配色）、共享组件、HTML 渲染
├── di/               # Hilt 模块
├── feature/          # 按页面分包：home/topic/nodes/search/notifications/profile/...
└── navigation/       # 路由与 Tab 导航
```

## 说明

- 浏览话题无需登录；通知与个人资料需要 Personal Access Token（v2ex.com/settings/tokens）
- App 内回复需要网页会话（WebView 登录）；V2EX 无开放发帖 API，发主题以"复制正文 + 打开网页"完成
- API 频率上限 600 次/小时/IP，客户端对"关注"聚合采用串行请求以控制配额
