# language: zh-CN
功能: MCP Server 问答接口

  场景: 通过MCP ask_repo_question工具获取代码库回答
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
           content: '这是通过MCP工具返回的回答。代码入口在app.py中。'
         }
       }]
     }
     """
    当向MCP服务发送问题"what is the entry point"
    那么MCP回答应为:
      """
      : 这是通过MCP工具返回的回答。代码入口在app.py中。
      """

  场景: 通过MCP工具问答时LLM先无工具调用后被要求重试
    假如Mock API:
      """
      POST: '/v1/chat/completions'
      ---
      body(LlmResponse): {
        choices: [{
          message: {
            content: '我知道了，这个项目的入口是app.py。'
          }
        }]
      }
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
           content: '重试后返回的回答。'
         }
       }]
     }
     """
    当向MCP服务发送问题"retry question"
    那么MCP回答应为:
      """
      : 重试后返回的回答。
      """
