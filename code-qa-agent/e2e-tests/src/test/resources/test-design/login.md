# 登录接口测试设计

## 范围

覆盖 `code-qa-agent` 的 Chainlit `/login` 端点（`password_auth_callback`），验证登录成功与失败分支。

## 被测代码

```python
# app.py
@cl.password_auth_callback
async def auth_callback(username: str, password: str) -> cl.User | None:
    if settings.auth_password and password != settings.auth_password:
        return None
    if not username.strip():
        return None
    return cl.User(identifier=username.strip(), metadata={"role": "user"})
```

当前 e2e 环境 `CQA_AUTH_PASSWORD=""`（空字符串），故第一个条件恒为假，密码不参与校验。

## 输入因子

| 因子 | 取值/等价类 | 说明 |
| --- | --- | --- |
| `username` | 空白字符串；非空字符串 | `app.py` 中空白用户名（`not username.strip()` 为真）返回 `None`，非空用户名允许登录。 |
| `password` | 非空任意字符串 | 当前 e2e 环境 `CQA_AUTH_PASSWORD` 为空，后端不校验具体密码值，但表单字段必须存在。 |

## 输出因子

| 因子 | 说明 |
| --- | --- |
| 登录响应 | HTTP 状态码与 JSON `detail`。失败返回 `401` + `{"detail": "credentialssignin"}`；成功返回用户信息并设置 JWT Cookie。 |

## 流程图

```text
[POST /login]
     │
     ▼
┌─ auth_password ≠ "" ─┐
│  and password wrong?  │  ← 当前环境 auth_password=""，此分支恒为 No
└─────────┬─────────────┘
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

| 用例名 | `username` | `password` | 期望输出 |
| --- | --- | --- | --- |
| 用户名为空登录失败 | `" "`（空白） | `anything` | `code=401`，`body.json.detail=credentialssignin` |
| 有效用户名登录成功 | `"joseph"`（非空） | `anything` | `code=200` |

## 覆盖性检查

1. **代码路径覆盖**：
   - `username.strip()` 为空 → `return None` → 401（覆盖）。
   - `username.strip()` 非空 → `return User` → 200（覆盖）。
   - `auth_password` 非空且密码不匹配 → `return None` → 401（当前 e2e 环境未覆盖，`CQA_AUTH_PASSWORD=""` 使该分支不可达）。

2. **输入因子覆盖**：
   - `username` 的空白/非空两类均覆盖。
   - `password` 因子在当前环境下无等价类分化，单一取值即可覆盖。

3. **条件分支覆盖**：
   - `not username.strip()` 为真 / 为假 均覆盖。
   - `settings.auth_password and password != settings.auth_password`：当前环境 `auth_password=""` 恒为假，单边覆盖。
