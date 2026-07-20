# language: zh-CN
功能: 登录

  场景: 用户名为空登录失败
    当POST form "/login":
      """
      {
        username: ' '
        password: anything
      }
      """
    那么response should be:
      """
      : {
        code=401
        body.json= {
          detail: credentialssignin
        }
      }
      """

  场景: 有效用户名登录成功
    当POST form "/login":
      """
      {
        username: joseph
        password: anything
      }
      """
    那么response should be:
      """
      : {
        code=200
        body.json= {
          success: true
        }
      }
      """

