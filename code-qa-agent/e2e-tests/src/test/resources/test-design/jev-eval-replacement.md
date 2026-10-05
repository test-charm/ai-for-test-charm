# 用 Jev 替换 eval 判定层（设计文档）

> 状态：待实施
> 目标：用 TypeSafe Jev（System One）替换现有 `回复蕴含度应该` 的 embedding 判定，作为 eval test 的判定层。

## 1. 背景与现状

现有 eval test 方案（`e2e-tests/src/test/resources/evals/eval_system_prompt.feature`，`@eval`）由三部分组成：

| 部分 | 实现 | 是否替换 |
|------|------|---------|
| 编排 | Cucumber feature | 否，复用 |
| 发消息 / 收流式回复 | `SocketIOSteps` + Socket.IO + DB fallback | 否，复用 |
| 判定（蕴含度） | `SocketIOSteps.callNliAggregated` → embedding sidecar `/containment`（`BAAI/bge-small-zh-v1.5` 余弦相似度） | **是** |

现有判定：每条 golden claim 对回复逐句取最大余弦相似度，`ratio = min(scores)`，sidecar 阈值 `CONTAINMENT_THRESHOLD=0.6`，feature 断言 `.ge: 0.7`。

Jev（TypeSafe System One）：`POST https://api.typesafe.ai/v1/systemone`，输入 `state` + 类型化 `questions`（Choice/Score/Noul），输出类型化答案 + 概率 + confidence。

## 2. 推荐方案总览

```
改造:  Cucumber ──发消息/收流式回复──> agent(真实LLM)
                   └─ 回复蕴含度应该 ──POST /containment──> ┬─ embedding sidecar(18002)  [保留]
                        (Java 零改动)                       └─ Jev  sidecar(18003)     [新增]
                                                                  └─ POST /v1/systemone ──> api.typesafe.ai
```

- 只替换**判定层**：Cucumber 编排、Socket.IO/DB 收消息全部复用。
- 新 sidecar 与 embedding **同路径同契约**：`{claims, reply}` → `{scores, ratio:min, threshold, passed}`，Java 只切 base-url，代码零分支。
- 用 `CQA_EVAL_JUDGE=embedding|jev` 环境变量 + `run-eval-tests.sh` 切换；embedding 永久保留。

## 3. 三种 primitive 对比

| 维度 | **Noul** | Score | Choice |
|---|---|---|---|
| 语义 | 单条命题为真的概率 0–1 | 按有序量表的加权分 | 从枚举选项选一个 |
| 与「claim 蕴含」契合 | 高：每条 claim 就是一个 yes/no 命题 | 中：需自造「部分/完整」量表 | 中：需自定义选项 |
| 聚合 | `min(noul)` 直接就是现有 `ratio` | 需把分数映射回通过/否 | 需把 choice 再映射一遍 |
| 阈值语义 | 概率，天然可阈值化 | 官方警告：不要用 score 做数值标定/插值 | 选项顺序有偏置风险 |
| confidence | 无（noul 本身就是概率） | 有 | 有 |

**结论：选 Noul。** claim 本质是二值命题；Noul 的 0–1 概率与现有 `ratio` 分数同构，聚合最自然；Score/Choice 都要多一层不可校准的映射。

## 4. 探针实测结果（`jev-1.13.0`）

| 样本 | c1 | c2 | c3 |
|---|---|---|---|
| 正样本（全部表达） | 0.97 | 0.98 | 0.98 |
| 负样本（未提及） | 0.16 | – | 0.14 |
| 负样本·直接用 claim 原文 | 0.30（世界知识泄露） | – | – |
| 部分样本（只表达 c1） | 0.93 | 0.35 | 0.15 |
| 同义改写·「论断是否成立」措辞 | 0.70（卡阈值） | 0.97 | 0.97 |
| 同义改写·「表达了以下含义」措辞 | 0.81 | – | – |
| 负样本·「表达了以下含义」 | 0.02 | – | 0.01 |
| 同义改写·加 criteria | 0.53（反而降分） | – | – |
| 相反论断（幻觉） | 0.02 | – | – |

关键结论：

1. 中文 Noul 可用，正负分离清晰（0.97 vs 0.02），CJK 风险可控。
2. 指令措辞影响巨大：`该回答是否表达了以下含义（不要求用词一致）：<claim>` 优于 `根据这段回答，以下论断是否成立`（改写场景 0.81 vs 0.70）。
3. 不要用 `criteria`：实测降分。
4. 不要用 claim 原文直接问：会凭世界知识误判（负样本 0.30）。
5. 同义改写场景最好也只有 0.81，离 0.7 阈值余量小 → 阈值必须标定。

## 5. 决策汇总

| # | 决策点 | 结论 |
|---|---|---|
| 1 | 替换范围 | 只替换判定层 |
| 2 | 部署形态 | 新建独立 Python Jev sidecar |
| 3 | 问题建模 | 每条 claim 一个 Noul |
| 4 | 契约/聚合 | 兼容现有契约，`ratio=min(noul)` |
| 5 | golden 数据 | 先不动 |
| 6 | 阈值 | 记录模式标定后再定 |
| 7 | 指令措辞 | 锚定回复的元问题：`该回答是否表达了以下含义（不要求用词一致）：<claim>` |
| 8 | API key | 进 gitignored `.env` + 轮换旧 key |
| 9 | 失败处理 | 有界重试后 fail loud，不 fail-open |
| 10 | 模型版本 | 锁定 `jev-1.13.0` |
| 11 | embedding sidecar | 永久并存、可切换 |
| 12 | 切换点 | 环境变量 + `run-eval-tests.sh` |
| 13 | 验收 | eval feature 为验收 + 轻量探针 |
| 14 | 中文风险 | 先探针（已完成，风险可控） |
| 15 | state 预处理 | 原文 + 长度保护（≤32k token） |
| 16 | 端点/命名 | 两侧车同 `/containment` + 切 base-url |

## 6. sidecar 契约

复用 embedding 契约，Java 零改动：

```
POST /containment
req:  { "claims": ["...", "..."], "reply": "..." }
resp: { "scores": [0.97, 0.02], "ratio": 0.02, "threshold": 0.6, "passed": false }
```

- 每条 claim 生成一个 Noul，`state = reply`，一次请求并发评估。
- `instructions = "该回答是否表达了以下含义（不要求用词一致）：<claim>"`
- `scores` 顺序与 `claims` 一致；`ratio = min(scores)`；`passed = ratio >= threshold`。
- 记录 `usage` 与响应 `model` 版本，便于追溯。

## 7. 实施步骤（TDD）

- **阶段一 新增测试**：写轻量探针（正/负样本 → 断言 noul 分离），确认无 sidecar 时失败。
- **阶段二 增加功能**：
  1. 新建 `e2e-tests/jev_judge_server.py`（FastAPI + `typesafe-sdk`，带 RetryPolicy）；
  2. compose 加 `jev-judge` 服务（`18003:8002`，`profiles:[eval]`）；
  3. `.env` 加 `TYPESAFE_API_KEY` / `TYPESAFE_MODEL=jev-1.13.0`；
  4. `run-eval-tests.sh` 加 `CQA_EVAL_JUDGE`，用 `EMBEDDING_BASE_URL` 注入 base-url（Spring relaxed binding，Java 零改动）。
  - 先跑记录模式：照常调 Jev、打印每条 noul，但 `ratio=1.0` 让用例全绿 → 收集分布。
- **阶段三 标定并转正**：据分布设阈值，改用真实 `ratio=min`，跑现有 `@eval` feature 转绿。
- **阶段四 重构**：抽取两 sidecar 公共聚合逻辑，命名统一（可选）。

## 8. 残留风险

- 改写余量小（0.81 vs 阈值 0.7）：可能需微调 claim 文本或下调阈值。
- context rot：agent 长回复可能拉低 noul，需长度保护并监控。
- 远端依赖：429/529 用 SDK 重试；模型升级需手动换 pinned 版本。
- 成本：单场景 ~400–520 input token，6 场景一轮约 $0.0001，可忽略。
