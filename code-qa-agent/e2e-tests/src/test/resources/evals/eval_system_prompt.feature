# language: zh-CN
@api-login @eval
功能: system_prompt 规范评估 —— 真实 LLM 回答质量

  场景: DAL group 语法问答
    当用户发送消息"dal验证的时候，如果两个属性值一样可以写成：<a,b>: 1。那么如果一个对象数组里面两个index的属性值一样，有类似的简单写法吗？"
    当收齐回复
    而且回复蕴含度应大于 0.70:
      """
     有，用 <<0 1>> 的 group 语法。group 语法的完整写法是双尖括号 <<a, b>>。

     可以写成 list<<0 1>>.value= 100，等价于同时验证 list[0].value 和 list[1].value 都等于 100。

     在对象作用域内也可以用 list: { <<0 1>>: 1 } 或 list: { <<[0], [1]>>: 1 } 的写法。
     """

  场景: JFactory 多数据源读写问答
    当用户发送消息"如何使用JFactory同时对多个数据源进行数据读写"
    当收齐回复
    而且回复蕴含度应大于 0.70:
      """
     JFactory 通过 CompositeDataRepository 支持同时对多个数据源进行读写。

     CompositeDataRepository 可根据类型、包名或自定义条件将不同实体路由到对应的 DataRepository，
     提供 registerByType、registerByPackage、registerBy 三种注册方式。

     也可以使用 CompositeRepository，它自带 MemoryDataRepository 作为默认兜底仓库。
     """

  场景: RESTful-cucumber 同步设置 header
    当用户发送消息"RESTful-cucumber 发请求的时候除了 request body，可以同步设置 header吗"
    当收齐回复
    而且回复蕴含度应大于 0.70:
      """
      可以同步设置 header。

      通过 ::headers 内联语法在 POST/PUT/PATCH 的 body doc-string 或 GET/DELETE 的 params doc-string 中设置 header。

      ::headers 支持单值和多值数组语法。

      Given header by RESTful api 是测试专用的，不能给用户使用。
      """
