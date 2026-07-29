# language: zh-CN
@api-login @eval
功能: system_prompt 规范评估 —— 真实 LLM 回答质量

  场景: DAL group 语法问答
    当用户发送消息"dal验证的时候，如果两个属性值一样可以写成：<a,b>: 1。那么如果一个对象数组里面两个index的属性值一样，有类似的简单写法吗？"
    当收齐回复
    而且回复蕴含度应大于 0.50:
      """
      group 语法支持把 index 作为 group 节点来验证数组。

      可以写成 list<<0 1>>.value= 100，表示两个元素的 value 属性都等于 100。
      """

  场景: JFactory 多数据源读写问答
    当用户发送消息"如何使用JFactory同时对多个数据源进行数据读写"
    当收齐回复
    而且回复蕴含度应大于 0.50:
      """
      CompositeDataRepository 可以根据类型、包名或自定义条件，将不同实体路由到对应的 DataRepository。

      CompositeDataRepository 提供 registerByType、registerByPackage、registerBy 三种注册方式。
      """

  场景: RESTful-cucumber 同步设置 header
    当用户发送消息"RESTful-cucumber 发请求的时候除了 request body，可以同步设置 header吗"
    当收齐回复
    而且回复蕴含度应大于 0.50:
      """
      可以同步设置 header。

      通过 ::headers 内联语法在 doc-string 中设置 header。

      Given header by RESTful api 是测试专用的。
      """
