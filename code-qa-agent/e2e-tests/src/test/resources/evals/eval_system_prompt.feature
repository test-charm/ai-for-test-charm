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

  场景: JFactory 简介问答
    当用户发送消息"简单介绍一下jfactory"
    当收齐回复
    而且回复蕴含度应大于 0.70:
      """
      jfactory 是 Test Charm 的数据创建库，核心理念是先定义有意义的 Spec 形态，场景中只覆盖关心的字段。

      支持 Spec 复用数据形状、Trait 叠加变体、DataRepository 自动保存和查询复用。

      嵌套属性设置时会自动查找 repository 中已有数据复用，保持数据一致性。

      相关模块包括 jfactory-cucumber、jfactory-repo-jpa、DAL-extension-jfactory。
      """

  场景: DAL-java 简介问答
    当用户发送消息"简单介绍一下 DAL-java"
    当收齐回复
    而且回复蕴含度应大于 0.70:
      """
      DAL-java 是 Test Charm 的数据断言语言 Java 实现，定位在 JSON 和通用编程语言之间。

      核心能力包括属性导航、值匹配（= 严格相等，: 宽松匹配）、列表映射、表格断言、Schema 验证。

      Java 入口为 Assertions.expect 和 DAL.dal()，通过 dal.extend() 可接入不同数据源扩展。

      它是整个 Test Charm 生态的数据语言基石，让一种语言描述所有数据。
      """
