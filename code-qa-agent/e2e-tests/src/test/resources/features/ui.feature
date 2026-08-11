# language: zh-CN
功能: UI 功能

  场景: 有效用户名登录成功
    当操作:
      """
      登录: {
        'Email address': joseph
        Password: anything
      }
      """
    那么用户应该:
      """
      ::eventually: {
        ::this: /.*👋 我已准备好分析代码库，请问你想了解什么？.*/
      }
      """

