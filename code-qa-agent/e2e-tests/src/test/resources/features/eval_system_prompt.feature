# language: zh-CN
@api-login @eval
功能: system_prompt 规范评估 —— 真实 LLM 回答质量

  此 feature 使用真实 LLM 配置（opencode zen / deepseek-v4-flash-free），
  验证 agent 回答是否符合 system_prompt.md 中的规范要求。
  **不应在 CI 中自动运行。** 手动运行方式见下方。

  场景: DAL group 语法问答 —— 内容完整性与 NLI 蕴含度
    当用户发送消息"dal验证的时候，如果两个属性值一样可以写成：<a,b>: 1。那么如果一个对象数组里面两个index的属性值一样，有类似的简单写法吗？"
    当收齐回复
    而且回复蕴含度应大于 0.7:
      """
      有，用 `<<0 1>>` 的 group 语法。

      `<a,b>: 1` 是 DAL 的 group 语法，表示 a 和 b 两个属性值相等。

      验证 list[0].value 和 list[1].value 都等于 100，可以写成 list<<0 1>>.value= 100。

      这是公开能力。<<>> group 语法的解析实现在 GroupExpression.java 和 Compiler.java 中。

      示例来自 group.feature 文件。
      """

