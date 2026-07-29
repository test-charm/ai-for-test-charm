# language: zh-CN
@api-login @eval
功能: system_prompt 规范评估 —— 真实 LLM 回答质量

  场景: DAL group 语法问答
    当用户发送消息"dal验证的时候，如果两个属性值一样可以写成：<a,b>: 1。那么如果一个对象数组里面两个index的属性值一样，有类似的简单写法吗？"
    当收齐回复
    而且回复蕴含度应大于 0.50:
      """
      有，用 <<>> group 语法实现。

      list<<0 1>>.value= 100 可以验证两个 index 的属性值。

      <<>> group 表达式的运行时求值由 GroupExpression.java 实现。
      """

  场景: JFactory 多数据源读写问答
    当用户发送消息"如何使用JFactory同时对多个数据源进行数据读写"
    当收齐回复
    而且回复蕴含度应大于 0.50:
      """
      CompositeDataRepository 是公开 API，位于 src/main/java 目录下。

      CompositeDataRepository 根据实体类型将不同数据路由到对应的 DataRepository 实现。

      使用时创建一个 CompositeDataRepository，为不同实体类型注册不同的 DataRepository，再传入 JFactory 即可。
      """
