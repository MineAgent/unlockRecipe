# Unlock Recipe (`unlockrecipe`)

[![License: LGPL-3.0-only](https://img.shields.io/badge/license-LGPL--3.0--only-blue.svg)](LICENSE)
[![Docs: CC BY-NC-SA 4.0](https://img.shields.io/badge/docs-CC%20BY--NC--SA%204.0-lightgrey.svg)](LICENSE.CC-BY-NC-SA-4.0)

**Minecraft 26.2 / 26.3**（Fabric）**服务端**模组：**只要有玩家加入世界，就替这个玩家执行一次
`/recipe give <玩家> *`**，把服务端知道的全部配方解锁给他。

不记录任何人是否来过：**每次加入都会执行**，同一玩家的第二次、第三次也一样。
原版对「已经没有新配方可给」的回答是命令自己的提示（`没有新的配方`），模组照常执行、照常记日志。

```
[Server thread/INFO]: Steve joined -> /recipe give Steve * : 4 -> 1561 of 1585 recipes unlocked for the player
```

**不需要开启作弊、不需要 OP、不需要在服务器控制台手动敲命令**：命令由服务端自己的命令源
（控制台身份，权限等级 4）发出，所以单人存档关掉作弊、多人服务器玩家不是 OP，都照样生效。

---

## 它解决什么问题

原版拿到材料才会解锁对应配方，所以在「新世界/新玩家」场景里，配方书一开始几乎是空的。
想让人人开局就有整本配方书，通常得在控制台一个个敲 `/recipe give <玩家> *`，或者把玩家设成 OP
自己敲（还得开作弊）。

这个模组把这件事变成自动的：**谁进来，就给谁整本配方书**。

---

## 行为细节

| 项目 | 行为 |
| --- | --- |
| 触发 | 玩家加入世界（单人、局域网、独立服务端都一样） |
| 执行 | `/recipe give <玩家名> *`，由控制台命令源发出（权限 4） |
| 记忆 | **没有**。每次加入都执行，不判断是否来过 |
| 已在配方书里 | 原版命令自己回一句「没有新的配方」，无害；模组仍然执行并记日志 |
| 日志 | 每次执行写一行，格式见上（`旧数量 -> 新数量 of 服务端配方总数`） |

### 为什么是「加入世界」而不是「刚连进来」

Fabric 的 `ServerPlayConnectionEvents.JOIN` 在 `PlayerList#placeNewPlayer` **开头**触发，
那时玩家**还没被放进玩家列表**，`/recipe give Steve *` 的名字选择器会直接失败（`未找到玩家`）。
`MinecraftServer#execute` 也救不了：在服务端线程上调用它是**立即执行**的，任务仍然跑在
`placeNewPlayer` 里面。

所以模组只在 JOIN 里记下玩家的 UUID，等这一 tick 结束时（`ServerTickEvents.END_SERVER_TICK`）
再查一次玩家列表并执行命令。玩家中途掉线则跳过。

### 为什么 `environment` 是 `*`、入口点是 `main`

* Fabric Loader 在客户端 JVM 里会**过滤掉 `environment: "server"` 的模组**，而单人/局域网世界跑在
  客户端里的**集成服务端**上。写成 `server` 的话，模组在单人世界里根本不会加载。
* 同理，`server` 入口点（`DedicatedServerModInitializer`）只在**独立服务端**被调用；集成服务端只有
  `main`/`client` 入口点。用 `main` 注册事件，独立服务端和集成服务端共用同一条代码路径。

模组本身**没有任何客户端行为**，纯服务端逻辑；`*` 只是为了让它同时适用于单人/局域网。

---

## 安装

1. Minecraft **26.2 / 26.3** + Fabric Loader **0.19.5+**（26.3 实例用 0.20.1）；
2. 装 **Fabric API**（26.2 用 `0.160.0+26.2`，26.3 用 `0.166.2+26.3`；模组用到其中的
   `fabric-networking-api-v1`（`ServerPlayConnectionEvents`）和
   `fabric-lifecycle-events-v1`（`ServerTickEvents`），两者都随 Fabric API 一起安装）；
3. 把 `unlockrecipe-1.0.1.jar` 放进**服务端**（或单人客户端）的 `mods/` 目录。

独立服务端、单人存档、局域网世界都支持；客户端连别人的服务器时它什么也不做。

## 构建

```bash
./gradlew build           # 产物：build/libs/unlockrecipe-1.0.1.jar
```

Minecraft 26.1+ 官方版就是未混淆的，所以 `build.gradle` 里没有 `mappings` 依赖；
Fabric API 用 `compileOnly`（运行时由 Fabric Loader 从 `mods/` 加载真身，绝不打包进本模组）。

## 已验证

在真实客户端（Minecraft 26.2 + Fabric Loader 0.19.5 + Fabric API 0.160.0+26.2，Linux）里实测，
用一个**没开作弊**的存档副本（进入世界前配方书里只有 4 个配方）：

| 检查 | 结果 |
| --- | --- |
| 模组加载 | `Loading 56 mods: … - unlockrecipe 1.0.1` |
| 加入即执行 | `Steve joined -> /recipe give Steve * : 4 -> 1561 of 1585 recipes unlocked for the player` |
| 服务端配方书 | 存档 `players/data/<uuid>.dat` 里的 `recipeBook.recipes` 由 **4** 变成 **1561**（独立于日志的持久化证据） |
| 剩余 24 个配方 | 全部是 `crafting_special_*` / `crafting_decorated_pot` 这类**特殊配方**（旗帜复制、烟花、修复物品、地图扩展、书复制、盾牌图案、装饰罐）。原版配方书本来就不收录它们，`/recipe give *` 也给不了——直接在合成格摆材料即可，无需解锁 |
| 重复加入 | 每次加入都再执行一次，行为一致 |
| 26.3（Fabric Loader 0.20.1 + Fabric API 0.166.2+26.3） | 同一 jar 正常加载，进单人世界时日志 `DSH joined -> /recipe give DSH * : 125 -> 1739 of 2042 recipes unlocked for the player` |

> 结论：**服务端能放进配方书的配方（1561/1561）全部解锁**，剩下的 24 个不是配方书条目。

## 许可证

* **代码**（`src/`、`build.gradle` 等）：**[LGPL-3.0-only](LICENSE)**（完整条款见 [`LICENSE`](LICENSE)，
  它引用的 GPL-3.0 文本见 [`LICENSE.GPL-3.0`](LICENSE.GPL-3.0)）。
* **文档**（本 `README.md` 及仓库内的说明性文字）：**[CC BY-NC-SA 4.0](LICENSE.CC-BY-NC-SA-4.0)**
  （署名—非商业性使用—相同方式共享 4.0 国际，完整文本见
  [`LICENSE.CC-BY-NC-SA-4.0`](LICENSE.CC-BY-NC-SA-4.0)）。
