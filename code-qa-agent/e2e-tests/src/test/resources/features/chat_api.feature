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
  场景: 有效消息返回助手回复
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
      """
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
      """
      {
        "clientType": "webapp",
        "sessionId": "${session-id}",
        "userEnv": "{}"
      }
      """
    当发送事件 "connection_successful"
    当发送事件 "client_message":
      """
      {
        "message": {
          "id": "${message-id}",
          "createdAt": "2026-07-09T00:00:00.000Z",
          "output": "hello",
          "name": "joseph"
        }
      }
      """
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
       body.json= {
         stream: false
         model: mock-gpt
         tool_choice: required
         tools= [{
           function= {
             description: ```
                          List directory tree structure. Use this first to understand project layout.

                              Args:
                                  path: Directory relative to workspace root. Default "." for root.
                                  max_depth: Traversal depth (1-5). Default 3.
                          ```
             name: list_directory
             parameters= {
               properties= {
                 max_depth= {
                   default: 3,
                   type: integer
                 }
                 path= {
                   default: '.'
                   type: string
                 }
               }
               type: object
             }
           }
           type: function
         } {
           function= {
             description: ```
                          Find files matching a glob pattern.

                              Args:
                                  pattern: Glob pattern, e.g. "**/*.py", "**/test_*", "src/**/*.java"
                                  path: Base directory relative to workspace root. Default ".".
                          ```
             name: find_files
             parameters= {
               properties= {
                 path= {
                   default: '.'
                   type: string
                 }
                 pattern= {
                   type: string
                 }
               }
               required: [ pattern ]
               type: object
             }
           }
           type: function
         } {
           function= {
             description: ```
                          Search code content with regex (powered by ripgrep).

                              Args:
                                  pattern: Regex pattern to search for.
                                  file_glob: Optional file filter, e.g. "*.py", "*.java".
                                  path: Directory to search, relative to workspace root.
                          ```
             name: grep_code
             parameters= {
               properties= {
                 file_glob= {
                   anyOf= | type   |
                          | string |
                          | 'null' |
                   default: null
                 }
                 path= {
                   default: '.'
                   type: string
                 }
                 pattern= {
                   type: string
                 }
               }
               required: [ pattern ]
               type: object
             }
           }
           type: function
         } {
           function= {
             description: ```
                          Read file contents with optional line range.

                              Args:
                                  file_path: Path relative to workspace root.
                                  start_line: First line (1-indexed). Default 1.
                                  end_line: Last line. Default reads up to 300 lines from start_line.
                          ```
             name: read_file
             parameters= {
               properties= {
                 end_line= {
                   anyOf= | type    |
                          | integer |
                          | 'null'  |
                   default: null
                 }
                 file_path= {
                   type: string
                 }
                 start_line= {
                   default: 1
                   type: integer
                 }
               }
               required: [ file_path ]
               type: object
             }
           }
           type: function
         } {
           function= {
             description: ```
                          Extract code symbols (functions, classes, methods) from a file via tree-sitter AST parsing.

                              Args:
                                  file_path: Path relative to workspace root.
                          ```
             name: get_symbols
             parameters= {
               properties= {
                 file_path= {
                   type: string
                 }
               }
               required: [ file_path ]
               type: object
             }
           }
           type: function
         } {
           function= {
             description: ```
                          Generate a symbol map of the repository — a bird's-eye view of all
                              functions, classes, and methods across the codebase. Extremely useful
                              for understanding project structure before deep-diving into files.

                              Args:
                                  path: Base directory relative to workspace root.
                                  file_glob: Optional glob filter, e.g. "**/*.py", "**/*.java".
                          ```
             name: get_repo_map
             parameters= {
               properties= {
                 file_glob= {
                   anyOf= | type   |
                          | string |
                          | 'null' |
                   default: null
                 }
                 path= {
                   default: '.'
                   type: string
                 }
               }
               type: object
             }
           }
           type: function
         }]
         messages= {
           empty: false
         }
       }
     } {
       body.json= {
         stream: false
         model: mock-gpt
         tool_choice: null
         tools= {
           empty: false
         }
         messages= {
           empty: false
         }
       }
     }]
     """
