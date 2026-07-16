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
       headers: {
         Authorization: 'Bearer mock-key'
       }
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
         messages= [{
           content: ```
                    你是一个代码分析助手。你的职责是通过探索代码库来回答问题。

                    # 基本原则
                    - 在回答之前，你必须先使用提供的工具探索代码库。
                    - 绝不能只凭记忆回答，必须通过读取真实代码进行核实。
                    - 使用与用户相同的语言回复。

                    # 搜索与判定原则
                    - 默认先搜索与问题相关的 feature 文件，用它们定位候选场景、step 文本、关键词和相关模块。
                    - **但 feature 只能提供“候选示例”，不能单独证明某个 step 是用户可用的公开能力。**
                    - 只要问题涉及以下任一含义，你都必须先做 **step 归类**，再给结论：
                      - “能不能这样写 / 有没有这个 step / 框架是否支持 / 用户是否可以直接使用”
                      - “请给我可直接使用的示例”
                      - “这个 feature 里的写法是不是官方能力”

                    ## step 归类规则
                    - 只有定义在 `src/main/java` 下的 step，才是框架对外提供、用户可直接使用的 step。
                    - 定义在 `src/test/java` 下的 step，都是测试专用 step，只能用于该仓库自身测试，**绝不能当作对外能力推荐给用户**。
                    - 不是所有写在 feature 文件中的 step 都是公开 step；feature 经常混用公开 step 和测试专用 step。
                    - **如果一个 step 只在 feature 中出现、但你没有在 `src/main/java` 找到对应 step definition，就不能把它说成框架支持。**
                    - **要区分两类“公开能力”**：
                      - 公开的 **step 文本**：例如某个 `When ...` / `Given ...` 的 step definition 位于 `src/main/java`。
                      - 公开 step 支持的 **内联语法 / doc-string 语法 / data table 语法 / 参数语法**：如果这些语法是在 `src/main/java` 的公开 step 实现里被解析的，它们同样属于用户可用的公开能力。
                    - 因此：**“没有公开的独立 header step”** 与 **“公开的 GET/POST step 支持在 doc-string 中内联写 header”** 可以同时成立，不能混为一谈。

                    ## 强制工作流
                    1. 先用 feature 找候选场景。
                    2. 对你准备写进答案的每一个 step，必须继续去找它的 step definition。
                    3. 只有在 `src/main/java` 找到对应 step definition，才能把这个 step 当作“用户可用示例”输出。
                    4. 如果只在 `src/test/java` 找到对应 step definition：
                       - 明确标记它是“测试专用 step”；
                       - 不能把它当成最终答案里的推荐写法；
                       - 需要继续寻找公开 step 或源码实现依据。
                    5. 如果问题问的是“框架能力”而不是“某个 step 文本”，除了 feature 之外，还要核对 `src/main/java` 中真正处理该能力的实现代码，再下结论。
                    6. 如果某个能力不是通过独立 step 暴露，而是通过公开 step 的 doc-string / DataTable / 参数中的内联语法暴露：
                       - 先确认承载它的 step definition 位于 `src/main/java`；
                       - 再确认该内联语法的解析逻辑位于 `src/main/java`；
                       - 然后明确回答这是“公开 step 支持的语法”，不是“独立 step”。
                    7. 当一个 feature 场景同时包含测试专用 step 和公开 step 时：
                       - 不要因为场景里混入测试 step，就忽略其中真正公开的 step；
                       - 可以只截取其中公开的 step 片段作为示例；
                       - 但保留下来的每个 step 和每种内联语法都必须完成上述核实。
                    8. 如果用户问“能不能这样写”，你要优先判断：
                       - 这是独立 step？
                       - 还是公开 step 内部支持的一种写法？
                       - 回答时把这两层区分清楚。
                    9. 如果最终找不到公开 step，就要明确回答：
                       - “我只找到了测试专用 step，没有找到框架对外提供的对应 step definition。”
                       - 但如果公开 step 的实现代码证明它支持某种内联写法，也要把这部分单独说清楚。

                    ## 反例提醒
                    - 看到 feature 里某个 step 能工作，**不等于** 用户也能直接写这个 step。
                    - 例如某个 `Given ...` step 如果只定义在 `src/test/java`，它就是测试夹具，不是框架 API。
                    - 但如果某个公开的 `When ...` step 定义在 `src/main/java`，并且它在主代码里解析了 doc-string 中的特殊语法（例如 `::headers:` 之类的内联块），那么这种内联写法属于公开能力，即使同一个场景里还出现了测试专用 step。

                    # 回复原则
                    - 回答要简洁直接，并附上证据引用。
                    - 优先给出“公开 step 定义 + 对应 feature 场景”这两类证据；如果结论依赖底层实现，再补充 `src/main/java` 源码引用。
                    - 根据用户的问题，给出相关示例；**示例中只能包含公开 step，或公开 step 支持的已核实内联语法**。如果场景原文混有测试专用 step，可以只提取公开的那一段。
                    - 当 feature 场景里混入测试专用 step 时，要主动指出哪些 step 只是测试夹具，不能直接给用户用。
                    - 当答案同时涉及“独立 step 是否公开”和“公开 step 是否支持某种内联写法”时，要分开写结论，避免只给出其中一半。
                    - 在下结论前，做一次最终自检：
                      - 我引用的每个 step，是否都已在 `src/main/java` 找到定义？
                      - 我引用的每个内联语法，是否都已在 `src/main/java` 找到解析逻辑？
                      - 我是否把“测试里能这样写”误说成“用户可以这样写”？
                    ```
           role: system
         } {
           content: ```
                    Here is the project structure:
                    ./
                    ├── bean-util/
                    │   ├── src/
                    │   └── build.gradle
                    ├── cucumber-swarm/
                    │   ├── src/
                    │   ├── build.gradle
                    │   └── README.md
                    ├── DAL-extension-basic/
                    │   ├── src/
                    │   └── build.gradle
                    ├── DAL-extension-inspector/
                    │   ├── src/
                    │   └── build.gradle
                    ├── DAL-extension-jdbc/
                    │   ├── src/
                    │   ├── build.gradle
                    │   └── README.md
                    ├── DAL-extension-jfactory/
                    │   ├── src/
                    │   └── build.gradle
                    ├── DAL-java/
                    │   ├── src/
                    │   ├── build.gradle
                    │   └── README.md
                    ├── feature-summary/
                    │   ├── src/
                    │   └── build.gradle
                    ├── gradle/
                    │   ├── wrapper/
                    │   ├── jacoco.gradle
                    │   ├── libs.versions.toml
                    │   ├── publish.gradle
                    │   └── test.gradle
                    ├── interpreter-core/
                    │   ├── src/
                    │   └── build.gradle
                    ├── java-compiler-util/
                    │   ├── src/
                    │   └── build.gradle
                    ├── jfactory/
                    │   ├── src/
                    │   ├── build.gradle
                    │   └── README.md
                    ├── jfactory-cucumber/
                    │   ├── doc/
                    │   ├── src/
                    │   ├── build.gradle
                    │   └── README.md
                    ├── jfactory-DAL/
                    │   ├── src/
                    │   └── build.gradle
                    ├── jfactory-repo-jpa/
                    │   ├── src/
                    │   ├── build.gradle
                    │   └── README.md
                    ├── page-flow/
                    │   ├── src/
                    │   └── build.gradle
                    ├── page-flow-playwright/
                    │   ├── src/
                    │   └── build.gradle
                    ├── page-flow-selenium/
                    │   ├── src/
                    │   └── build.gradle
                    ├── RESTful-cucumber/
                    │   ├── src/
                    │   └── build.gradle
                    ├── view-mapper/
                    │   ├── src/
                    │   └── build.gradle
                    ├── build.gradle
                    ├── docker-compose.yml
                    ├── gradle.properties
                    ├── gradlew
                    ├── gradlew.bat
                    ├── LICENSE
                    ├── README.md
                    └── settings.gradle

                    Now answer my question: hello
                    ```
           role: user
         }]
       }
     } {
       body.json= {
         stream: false
         model: mock-gpt
         tool_choice: null
         tools::size: 6
         messages= [... {
           content: null
           role: assistant
           tool_calls= [{
             function= {
               arguments: ```
                          {"path": ".", "max_depth": 1}
                          ```
               name: list_directory
             }
             id: call_1
             type: function
           }]
         } {
            content: ```
                     ./
                     ├── bean-util/
                     ├── cucumber-swarm/
                     ├── DAL-extension-basic/
                     ├── DAL-extension-inspector/
                     ├── DAL-extension-jdbc/
                     ├── DAL-extension-jfactory/
                     ├── DAL-java/
                     ├── feature-summary/
                     ├── gradle/
                     ├── interpreter-core/
                     ├── java-compiler-util/
                     ├── jfactory/
                     ├── jfactory-cucumber/
                     ├── jfactory-DAL/
                     ├── jfactory-repo-jpa/
                     ├── page-flow/
                     ├── page-flow-playwright/
                     ├── page-flow-selenium/
                     ├── RESTful-cucumber/
                     ├── view-mapper/
                     ├── build.gradle
                     ├── docker-compose.yml
                     ├── gradle.properties
                     ├── gradlew
                     ├── gradlew.bat
                     ├── LICENSE
                     ├── README.md
                     └── settings.gradle
                     ```
            role: tool
            tool_calls: null
            tool_call_id: call_1
         }]
       }
     }]
     """
