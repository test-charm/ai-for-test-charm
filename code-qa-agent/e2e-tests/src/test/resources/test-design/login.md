# 登录接口测试设计

> 覆盖率数据基于 2026-07-25 全量测试运行。`app.py` 42%（76 语句，44 未覆盖，含 coverage bootstrap 死代码），auth_callback 相关行 L53-54 密码不匹配分支仍不可达。

## 范围

覆盖 `code-qa-agent` 的 Chainlit `/login` 端点（`password_auth_callback`），验证登录成功与失败分支。

## 被测代码

```python
# app.py L51-57
@cl.password_auth_callback
async def auth_callback(username: str, password: str) -> cl.User | None:
    if settings.auth_password and password != settings.auth_password:  # L53
        return None                                                    # L54 (未覆盖)
    if not username.strip():                                           # L55
        return None                                                    # L56 (未覆盖)
    return cl.User(identifier=username.strip(), metadata={"role": "user"})  # L57 (已覆盖)
```

当前 e2e 环境 `CQA_AUTH_PASSWORD=""`（空字符串），故 L53 条件恒为假，L54 密码不匹配分支不可达。

## 输入因子

| 因子 | 取值/等价类 | 说明 |
| --- | --- | --- |
| `username` | 空白字符串；非空字符串 | `app.py` L55 中空白用户名（`not username.strip()` 为真）返回 `None`，非空用户名允许登录。 |
| `password` | 非空任意字符串 | 当前 e2e 环境 `CQA_AUTH_PASSWORD` 为空，后端不校验具体密码值，但表单字段必须存在。 |
| `CQA_AUTH_PASSWORD` | 空字符串（当前环境） | 控制 L53 条件分支是否激活。设置为非空可解锁密码校验路径。 |

## 输出因子

| 因子 | 说明 |
| --- | --- |
| 登录响应 | HTTP 状态码与 JSON `detail`。失败返回 `401` + `{"detail": "credentialssignin"}`；成功返回用户信息并设置 JWT Cookie。 |

## 流程图

```text
[POST /login]
     │
     ▼
┌─ settings.auth_password ≠ "" ─┐
│  and password wrong?          │  ← 当前环境 auth_password=""，此分支恒为 No
└─────────┬─────────────────────┘
   Yes    │    No
    │     │     │
    ▼     │     ▼
 return   │  username.strip()
 None     │  is empty?
    │     │   │
    │  Yes│   │No
    │     ▼   ▼
    │  return  return
    │  None    User
    │   │       │
    └───┴───────┘
         │
    ┌────┴────┐
    ▼         ▼
HTTP 401    HTTP 200
```

## 用例设计

| 用例名 | `username` | `password` | `CQA_AUTH_PASSWORD` | 期望输出 |
| --- | --- | --- | --- | --- |
| 用户名为空登录失败 | `" "`（空白） | `anything` | `""`（空） | `code=401`，`body.json.detail=credentialssignin` |
| 有效用户名登录成功 | `"joseph"`（非空） | `anything` | `""`（空） | `code=200` |
| 密码不匹配登录失败 | `"joseph"`（非空） | `"wrong"` | `"correct"`（非空） | `code=401`，`body.json.detail=credentialssignin` |

> 注：第三个用例"密码不匹配登录失败"需 `CQA_AUTH_PASSWORD=correct`，当前 Docker Compose profile 未配置该环境变量，**暂未实现**。

## 覆盖性检查

1. **代码路径覆盖**：
   - `username.strip()` 为空 → `return None` → 401（覆盖）。
   - `username.strip()` 非空 → `return User` → 200（覆盖）。
   - `settings.auth_password and password != settings.auth_password` → `return None` → 401（当前 e2e 环境未覆盖，`CQA_AUTH_PASSWORD=""` 使该分支不可达）。

2. **输入因子覆盖**：
   - `username` 的空白/非空两类均覆盖。
   - `password` 因子在当前环境下无等价类分化，单一取值即可覆盖。

3. **条件分支覆盖**：
   - `not username.strip()` 为真 / 为假 均覆盖。
   - `settings.auth_password and password != settings.auth_password`：当前环境 `auth_password=""` 恒为假，单边覆盖。`auth_password` 非空时 `password != settings.auth_password` 的两种结果未覆盖。
