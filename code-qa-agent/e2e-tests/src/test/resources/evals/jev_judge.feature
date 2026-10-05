# language: zh-CN
@jev-judge
功能: Jev 判定服务契约 —— 正/负样本应被明显区分

  场景: 回复完整表达了所有 claim 时判为蕴含
    当向评测服务发送请求:
      """
      {
        "reply": "JFactory 通过 CompositeDataRepository 支持同时对多个数据源进行读写。它提供了 registerByType、registerByPackage 和 registerBy 三种注册方式。",
        "claims": [
          "JFactory 通过 CompositeDataRepository 支持同时对多个数据源进行读写。",
          "提供 registerByType、registerByPackage、registerBy 三种注册方式。"
        ]
      }
      """
    那么评测结果应满足:
      """
      ratio >= 0.8 = true
      and scores[0] >= 0.8 = true
      and scores[1] >= 0.8 = true
      and passed = true
      """

  场景: 回复完全未提及 claim 时判为不蕴含
    当向评测服务发送请求:
      """
      {
        "reply": "抱歉，我暂时无法回答这个问题。你可以先自己在代码库中搜索相关的 feature 文件。",
        "claims": [
          "JFactory 通过 CompositeDataRepository 支持同时对多个数据源进行读写。",
          "提供 registerByType、registerByPackage、registerBy 三种注册方式。"
        ]
      }
      """
    那么评测结果应满足:
      """
      ratio <= 0.3 = true
      and scores[0] <= 0.3 = true
      and scores[1] <= 0.3 = true
      and passed = false
      """
