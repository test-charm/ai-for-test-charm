# language: zh-CN
功能: Chainlit 聊天 HTTP 接口

  场景: 用户名为空登录失败
    当POST "/login":
      """ application/x-www-form-urlencoded
      username=%20&password=anything
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

  @api-login
  场景: 非 UUID 消息 id 返回错误消息且不落库
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
    当GET "/ws/socket.io/?EIO=4&transport=polling&t=t0"
    那么response should be:
      """
      : {
        code=200
      }
      """
    当POST "/ws/socket.io/?EIO=4&transport=polling&sid=${engine-sid}&t=t1":
      """ text/plain
      40{"clientType":"webapp","sessionId":"${session-id}","threadId":"","userEnv":"{}","chatProfile":""}
      """
    那么response should be:
      """
      : {
        code=200
        body.string='OK'
      }
      """
    当GET "/ws/socket.io/?EIO=4&transport=polling&sid=${engine-sid}&t=t2"
    那么response should be:
      """
      : {
        code=200
      }
      """
    当POST "/ws/socket.io/?EIO=4&transport=polling&sid=${engine-sid}&t=t3":
      """ text/plain
      42["connection_successful"]
      """
    那么response should be:
      """
      : {
        code=200
        body.string='OK'
      }
      """
    当POST "/ws/socket.io/?EIO=4&transport=polling&sid=${engine-sid}&t=t4":
      """ text/plain
      42["client_message",{"message":{"id":"not-a-uuid","threadId":"","parentId":null,"createdAt":"2026-07-09T00:00:00.000Z","output":"hello","name":"joseph","type":"user_message","metadata":{"location":"http://127.0.0.1:18000/"},"streaming":false,"isError":false,"waitForAnswer":false},"fileReferences":null}]
      """
    那么response should be:
      """
      : {
        code=200
        body.string='OK'
      }
      """
    当GET "/ws/socket.io/?EIO=4&transport=polling&sid=${engine-sid}&t=t5"
    那么response should be:
      """
      : {
        code=200
        body.string=/.*badly formed hexadecimal UUID string.*/
      }
      """

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
    当GET "/ws/socket.io/?EIO=4&transport=polling&t=t0"
    那么response should be:
      """
      : {
        code=200
      }
      """
    当POST "/ws/socket.io/?EIO=4&transport=polling&sid=${engine-sid}&t=t1":
      """ text/plain
      40{"clientType":"webapp","sessionId":"${session-id}","threadId":"","userEnv":"{}","chatProfile":""}
      """
    那么response should be:
      """
      : {
        code=200
        body.string='OK'
      }
      """
    当GET "/ws/socket.io/?EIO=4&transport=polling&sid=${engine-sid}&t=t2"
    那么response should be:
      """
      : {
        code=200
      }
      """
    当POST "/ws/socket.io/?EIO=4&transport=polling&sid=${engine-sid}&t=t3":
      """ text/plain
      42["connection_successful"]
      """
    那么response should be:
      """
      : {
        code=200
        body.string='OK'
      }
      """
    当POST "/ws/socket.io/?EIO=4&transport=polling&sid=${engine-sid}&t=t4":
      """ text/plain
      42["client_message",{"message":{"id":"${message-id}","threadId":"","parentId":null,"createdAt":"2026-07-09T00:00:00.000Z","output":"hello","name":"joseph","type":"user_message","metadata":{"location":"http://127.0.0.1:18000/"},"streaming":false,"isError":false,"waitForAnswer":false},"fileReferences":null}]
      """
    那么response should be:
      """
      : {
        code=200
        body.string='OK'
      }
      """
    当GET "/ws/socket.io/?EIO=4&transport=polling&sid=${engine-sid}&t=t5"
    那么response should be:
      """
      : {
        code=200
      }
      """
    当GET "/ws/socket.io/?EIO=4&transport=polling&sid=${engine-sid}&t=t6"
    那么response should be:
      """
      : {
        code=200
      }
      """
    当GET "/ws/socket.io/?EIO=4&transport=polling&sid=${engine-sid}&t=t7"
    那么response should be:
      """
      : {
        code=200
      }
      """
    当GET "/ws/socket.io/?EIO=4&transport=polling&sid=${engine-sid}&t=t8"
    那么response should be:
      """
      : {
        code=200
      }
      """
    当GET "/ws/socket.io/?EIO=4&transport=polling&sid=${engine-sid}&t=t9"
    那么response should be:
      """
      : {
        code=200
      }
      """
    并且验证Mock API:
     """
     : {
       ::size= 2
     }
     """
