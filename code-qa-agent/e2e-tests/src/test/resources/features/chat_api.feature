# language: zh-CN
功能: Chainlit 聊天 HTTP 接口

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
        body.json: {
          detail= credentialssignin
        }
      }
      """

#  @api-login
#  场景: 非 UUID 消息 id 返回错误消息且不落库
#    当POST "/set-session-cookie":
#      """ application/json
#      {
#        "session_id": "${session-id}"
#      }
#      """
#    那么response should be:
#      """
#      : {
#        code=200
#        body.json.message='Session cookie set'
#      }
#      """
#    当连接 Socket.IO:
#      """ application/json
#      {"clientType":"webapp","sessionId":"${session-id}","threadId":"","userEnv":"{}","chatProfile":""}
#      """
#    当发送事件 "connection_successful"
#    当发送事件 "client_message":
#      """ application/json
#      {"message":{"id":"not-a-uuid","threadId":"","parentId":null,"createdAt":"2026-07-09T00:00:00.000Z","output":"hello","name":"joseph","type":"user_message","metadata":{"location":"http://127.0.0.1:18000/"},"streaming":false,"isError":false,"waitForAnswer":false},"fileReferences":null}
#      """
#    那么等待最多 5 秒接收 Socket.IO 事件
#    那么收到的 Socket.IO 事件应满足:
#      """
#      receivedEvents: [{
#        name: new_message
#        data.output: ''
#      } {
#        name: update_message
#        data.output: ''
#      }]
#      """

  @api-login
  场景: 有效消息返回助手回复并落库
    假如Mock API:
      """
      : {
        path.value= '/v1/chat/completions'
        method.value= 'POST'
      }
      ---
      body: ```
            {
              "id": "chatcmpl-tool-1",
              "object": "chat.completion",
              "created": 1752050400,
              "model": "mock-gpt",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "",
                    "tool_calls": [
                      {
                        "id": "call_1",
                        "type": "function",
                        "function": {
                          "name": "list_directory",
                          "arguments": "{\"path\": \".\", \"max_depth\": 1}"
                        }
                      }
                    ]
                  },
                  "finish_reason": "tool_calls"
                }
              ],
              "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 10,
                "total_tokens": 20
              }
            }
            ```
      ---
      body: ```
            {
              "id": "chatcmpl-final-1",
              "object": "chat.completion",
              "created": 1752050401,
              "model": "mock-gpt",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "这是一个mock回复。"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 10,
                "total_tokens": 20
              }
            }
            ```
      """
    当POST "/set-session-cookie":
      """ application/json
      {
        "session_id": "${session-id}"
      }
      """
    那么response should be:
      """
      : {
        code=200
        body.json.message='Session cookie set'
      }
      """
    当连接 Socket.IO:
      """ application/json
      {"clientType":"webapp","sessionId":"${session-id}","threadId":"","userEnv":"{}","chatProfile":""}
      """
    当发送事件 "connection_successful"
    当发送事件 "client_message":
      """ application/json
      {"message":{"id":"${message-id}","threadId":"","parentId":null,"createdAt":"2026-07-09T00:00:00.000Z","output":"hello","name":"joseph","type":"user_message","metadata":{"location":"http://127.0.0.1:18000/"},"streaming":false,"isError":false,"waitForAnswer":false},"fileReferences":null}
      """
#    那么等待最多 30 秒接收 Socket.IO 事件
    那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
        } : [ ... {
          data.output= ```
                       这是一个mock回复。

                       ---
                       ⏱️ 耗时 0秒
                       ```
        } ... ]
      }
      """
    并且数据应为:
     """
     MockApi::filter: { POST: '/v1/chat/completions' } : [{
       ...
     } {
       ...
     }]
     """
