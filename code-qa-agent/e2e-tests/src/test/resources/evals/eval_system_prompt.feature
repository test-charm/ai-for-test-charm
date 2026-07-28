# language: zh-CN
@api-login @eval
功能: system_prompt 规范评估 —— 真实 LLM 回答质量

  场景: DAL group 语法问答 —— 内容完整性与 NLI 蕴含度
    当用户发送消息"dal验证的时候，如果两个属性值一样可以写成：<a,b>: 1。那么如果一个对象数组里面两个index的属性值一样，有类似的简单写法吗？"
    当收齐回复
    而且回复蕴含度应大于 0.7:
      """
      有，用 <<>> group 语法实现。

      `<<a, b>>: 1` 叫做 group 表达式。

      list<<0 1>>.value= 100 可以验证两个 index 的属性值。

      <<>> group 语法实现在 GroupExpression.java。

      <<>> group 语法参考了 group.feature。
      """

