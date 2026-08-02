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

  场景: MCP问答时主LLM遇到429错误后切换到备用LLM
   假如Mock API:
     """
     POST: '/v1/chat/completions'
     ---
     code: 429
     body: ```
           {"error": {"message": "Rate limit exceeded", "type": "rate_limit_error", "code": "rate_limit_exceeded"}}
           ```
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
           content: 'MCP备用LLM的回复。'
         }
       }]
     }
     """
   当向MCP服务发送问题"what is the backup"
   那么MCP回答应为:
     """
     : MCP备用LLM的回复。
     """
   并且数据应为:
     """
     MockApi::filter: { POST: '/v1/chat/completions' } :
       | body.json.model      | body.json.tool_choice | headers.Authorization      |
       | mock-gpt             | required              | Bearer mock-key            |
       | mock-gpt-backup      | required              | Bearer mock-backup-key     |
       | mock-gpt-backup      | null                  | Bearer mock-backup-key     |
     """

  场景: MCP问答请求和回复持久化到数据库
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
           content: '持久化测试回答内容。'
         }
       }]
     }
     """
    当向MCP服务发送问题"persist this question"
    那么MCP回答应为:
      """
      : 持久化测试回答内容。
      """
    并且数据应为:
      """
      McpRequest: | id    | question              | answer            | provider | model    | createdAt    |
                  | {...} | persist this question | 持久化测试回答内容。 | openai   | mock-gpt | is AlmostNow |
      """
