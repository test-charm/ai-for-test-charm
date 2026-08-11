# language: zh-CN
功能: UI 功能

  @close-browser
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

  @ui-login
  场景: 有效消息返回助手回复
    假如Mock API:
      """
      POST: '/v1/chat/completions'
      ---
      body(LlmResponse): {
        choices: [{
          finishReason: 'tool_calls'
          message: {
            toolCalls!: [{
              function(ListDirectory): { ... }
            }]
          }
        }]
      }
      ---
      body(LlmResponse): {
        choices: [{
          message: {
            content: '这是一个mock回复。'
          }
        }]
      }
      """
    当用户操作:
      """
      聊天: {
        发送消息: hello
      }
      """
    那么用户应该:
      """
      ::eventually: {
        ::this: /.*这是一个mock回复。.*/
      }
      """
