# language: zh-CN
@api-login
功能: 工具函数测试

  场景: list_directory对文件路径返回错误
    假如Mock API:
      """
      POST: '/v1/chat/completions'
      ---
      body: ```
            {
              "created": 1752050400,
              "choices": [
                {
                  "finish_reason": "tool_calls",
                  "message": {
                    "role": "assistant",
                    "tool_calls": [
                      {
                        "id": "call_1",
                        "function": {
                          "name": "list_directory",
                          "arguments": "{\"path\": \"code-qa-agent/app.py\"}"
                        }
                      }
                    ]
                  }
                }
              ]
            }
            ```
      ---
      body: ```
            {
              "created": 1752050401,
              "choices": [
                {
                  "finish_reason": "stop",
                  "message": {
                    "role": "assistant",
                    "content": "list_directory结果：该路径不是目录。处理完成。"
                  }
                }
              ]
            }
            ```
      """
    当用户发送消息"test list_directory on file"
    那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
          data.type= assistant_message
        }
      }
      """

  场景: find_files无匹配时返回空结果
    假如Mock API:
      """
      POST: '/v1/chat/completions'
      ---
      body: ```
            {
              "created": 1752050400,
              "choices": [
                {
                  "finish_reason": "tool_calls",
                  "message": {
                    "role": "assistant",
                    "tool_calls": [
                      {
                        "id": "call_1",
                        "function": {
                          "name": "find_files",
                          "arguments": "{\"pattern\": \"*.nonexistent_ext\"}"
                        }
                      }
                    ]
                  }
                }
              ]
            }
            ```
      ---
      body: ```
            {
              "created": 1752050401,
              "choices": [
                {
                  "finish_reason": "stop",
                  "message": {
                    "role": "assistant",
                    "content": "find_files结果：未找到匹配文件。"
                  }
                }
              ]
            }
            ```
      """
    当用户发送消息"test find_files no match"
    那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
          data.type= assistant_message
        }
      }
      """

  场景: grep_code无匹配时返回空结果
    假如Mock API:
      """
      POST: '/v1/chat/completions'
      ---
      body: ```
            {
              "created": 1752050400,
              "choices": [
                {
                  "finish_reason": "tool_calls",
                  "message": {
                    "role": "assistant",
                    "tool_calls": [
                      {
                        "id": "call_1",
                        "function": {
                          "name": "grep_code",
                          "arguments": "{\"pattern\": \"ZZZ_NONEXISTENT_PATTERN_12345\"}"
                        }
                      }
                    ]
                  }
                }
              ]
            }
            ```
      ---
      body: ```
            {
              "created": 1752050401,
              "choices": [
                {
                  "finish_reason": "stop",
                  "message": {
                    "role": "assistant",
                    "content": "grep_code结果：未找到匹配内容。"
                  }
                }
              ]
            }
            ```
      """
    当用户发送消息"test grep_code no match"
    那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
          data.type= assistant_message
        }
      }
      """

  场景: read_file读取不存在文件返回错误
    假如Mock API:
      """
      POST: '/v1/chat/completions'
      ---
      body: ```
            {
              "created": 1752050400,
              "choices": [
                {
                  "finish_reason": "tool_calls",
                  "message": {
                    "role": "assistant",
                    "tool_calls": [
                      {
                        "id": "call_1",
                        "function": {
                          "name": "read_file",
                          "arguments": "{\"file_path\": \"nonexistent_file.txt\"}"
                        }
                      }
                    ]
                  }
                }
              ]
            }
            ```
      ---
      body: ```
            {
              "created": 1752050401,
              "choices": [
                {
                  "finish_reason": "stop",
                  "message": {
                    "role": "assistant",
                    "content": "read_file结果：文件未找到。"
                  }
                }
              ]
            }
            ```
      """
    当用户发送消息"test read_file not found"
    那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
          data.type= assistant_message
        }
      }
      """

  场景: read_file读取目录路径返回错误
    假如Mock API:
      """
      POST: '/v1/chat/completions'
      ---
      body: ```
            {
              "created": 1752050400,
              "choices": [
                {
                  "finish_reason": "tool_calls",
                  "message": {
                    "role": "assistant",
                    "tool_calls": [
                      {
                        "id": "call_1",
                        "function": {
                          "name": "read_file",
                          "arguments": "{\"file_path\": \"code-qa-agent\"}"
                        }
                      }
                    ]
                  }
                }
              ]
            }
            ```
      ---
      body: ```
            {
              "created": 1752050401,
              "choices": [
                {
                  "finish_reason": "stop",
                  "message": {
                    "role": "assistant",
                    "content": "read_file结果：该路径是目录不是文件。"
                  }
                }
              ]
            }
            ```
      """
    当用户发送消息"test read_file on directory"
    那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
          data.type= assistant_message
        }
      }
      """

  场景: get_symbols分析不存在文件返回错误
    假如Mock API:
      """
      POST: '/v1/chat/completions'
      ---
      body: ```
            {
              "created": 1752050400,
              "choices": [
                {
                  "finish_reason": "tool_calls",
                  "message": {
                    "role": "assistant",
                    "tool_calls": [
                      {
                        "id": "call_1",
                        "function": {
                          "name": "get_symbols",
                          "arguments": "{\"file_path\": \"nonexistent.py\"}"
                        }
                      }
                    ]
                  }
                }
              ]
            }
            ```
      ---
      body: ```
            {
              "created": 1752050401,
              "choices": [
                {
                  "finish_reason": "stop",
                  "message": {
                    "role": "assistant",
                    "content": "get_symbols结果：符号文件未找到。"
                  }
                }
              ]
            }
            ```
      """
    当用户发送消息"test get_symbols not found"
    那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
          data.type= assistant_message
        }
      }
      """

  场景: get_repo_map带glob过滤只分析Python文件
    假如Mock API:
      """
      POST: '/v1/chat/completions'
      ---
      body: ```
            {
              "created": 1752050400,
              "choices": [
                {
                  "finish_reason": "tool_calls",
                  "message": {
                    "role": "assistant",
                    "tool_calls": [
                      {
                        "id": "call_1",
                        "function": {
                          "name": "get_repo_map",
                          "arguments": "{\"file_glob\": \"**/*.py\"}"
                        }
                      }
                    ]
                  }
                }
              ]
            }
            ```
      ---
      body: ```
            {
              "created": 1752050401,
              "choices": [
                {
                  "finish_reason": "stop",
                  "message": {
                    "role": "assistant",
                    "content": "get_repo_map结果：符号索引完成。"
                  }
                }
              ]
            }
            ```
      """
    当用户发送消息"test get_repo_map with glob"
    那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
          data.type= assistant_message
        }
      }
      """
