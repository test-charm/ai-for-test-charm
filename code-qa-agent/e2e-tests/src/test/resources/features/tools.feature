# language: zh-CN
@api-login
功能: 工具函数测试

  Rule: List directory

    场景: list_directory正常返回文件列表
      假如Mock API:
        """
        POST: '/v1/chat/completions'
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: tool_calls
            message: {
              toolCalls!: [{
                function: {
                  name: list_directory
                  arguments: ```
                             {"path": ".","max_depth": 1}
                             ```
                }
              }]
            }
          }]
        }
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: stop
            message: {
              content: 'list_directory结果：处理完成。'
            }
          }]
        }
        """
      当用户发送消息"test list_directory normal match"
      那么收到的 Socket.IO 事件应满足:
        """
        ::eventually: {
          receivedEvents::filter: {
            name= new_message
          } : [ ... {
            data.output: ```
                         list_directory结果：处理完成。

                         ---
                         ⏱️ 耗时 0秒
                         ```
          }]
        }
        """
      并且数据应为:
        """
        MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
          body.json: {
            messages: [... {
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
              tool_call_id: list_directory-0
            }]
          }
        }]
        """

    场景: list_directory对文件路径返回错误
      假如Mock API:
        """
        POST: '/v1/chat/completions'
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: tool_calls
            message: {
              toolCalls!: [{
                function: {
                  name: list_directory
                  arguments: ```
                             {"path": "code-qa-agent/app.py"}
                             ```
                }
              }]
            }
          }]
        }
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: stop
            message: {
              content: 'list_directory结果：该路径不是目录。处理完成。'
            }
          }]
        }
        """
      当用户发送消息"test list_directory on file"
      那么收到的 Socket.IO 事件应满足:
        """
        ::eventually: {
          receivedEvents::filter: {
            name= new_message
          } : [ ... {
            data.output: ```
                         list_directory结果：该路径不是目录。处理完成。

                         ---
                         ⏱️ 耗时 0秒
                         ```
          }]
        }
        """
      并且数据应为:
        """
        MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
          body.json: {
            messages: [... {
              content: 'Not a directory: code-qa-agent/app.py'
              role: tool
              tool_call_id: list_directory-0
            }]
          }
        }]
        """

    场景: list_directory正常返回文件列表结果超过500行屏蔽
      假如Mock API:
        """
        POST: '/v1/chat/completions'
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: tool_calls
            message: {
              toolCalls!: [{
                function: {
                  name: list_directory
                  arguments: ```
                             {"path": ".","max_depth": 100}
                             ```
                }
              }]
            }
          }]
        }
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: stop
            message: {
              content: 'list_directory结果：处理完成。'
            }
          }]
        }
        """
      当用户发送消息"test list_directory normal match"
      那么收到的 Socket.IO 事件应满足:
        """
        ::eventually: {
          receivedEvents::filter: {
            name= new_message
          } : [ ... {
            data.output: ```
                         list_directory结果：处理完成。

                         ---
                         ⏱️ 耗时 0秒
                         ```
          }]
        }
        """
      并且数据应为:
        """
        MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
          body.json: {
            messages: [... {
              content::should.endsWith: ```
                                        more entries truncated)
                                        ```
              role: tool
              tool_call_id: list_directory-0
            }]
          }
        }]
        """

  Rule: Find files

    场景: find_files正常匹配返回文件列表
      假如Mock API:
      """
      POST: '/v1/chat/completions'
      ---
      body(LlmResponse): {
        choices: [{
          finishReason: tool_calls
          message: {
            toolCalls!: [{
              function: {
                name: find_files
                arguments: ```
                           {"pattern": "build.gradle"}
                           ```
              }
            }]
          }
        }]
      }
      ---
      body(LlmResponse): {
        choices: [{
          finishReason: stop
          message: {
            content: 'find_files正常匹配结果：已找到目标文件。'
          }
        }]
      }
      """
      当用户发送消息"test find_files normal match"
      那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
        } : [ ... {
          data.output: ```
                       find_files正常匹配结果：已找到目标文件。

                       ---
                       ⏱️ 耗时 0秒
                       ```
        }]
      }
      """
      并且数据应为:
      """
      MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
        body.json: {
          messages: [... {
            content: 'build.gradle'
            role: tool
            tool_call_id: find_files-0
          }]
        }
      }]
      """

    场景: find_files无匹配时返回空结果
      假如Mock API:
        """
        POST: '/v1/chat/completions'
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: tool_calls
            message: {
              toolCalls!: [{
                function: {
                  name: find_files
                  arguments: ```
                             {"pattern": "*.nonexistent_ext"}
                             ```
                }
              }]
            }
          }]
        }
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: stop
            message: {
              content: 'find_files结果：未找到匹配文件。'
            }
          }]
        }
        """
      当用户发送消息"test find_files no match"
      那么收到的 Socket.IO 事件应满足:
        """
        ::eventually: {
          receivedEvents::filter: {
            name= new_message
          } : [ ... {
            data.output: ```
                         find_files结果：未找到匹配文件。

                         ---
                         ⏱️ 耗时 0秒
                         ```
          }]
        }
        """
      并且数据应为:
        """
        MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
          body.json: {
            messages: [... {
              content: 'No files found matching: *.nonexistent_ext'
              role: tool
              tool_call_id: find_files-0
            }]
          }
        }]
        """

    场景: find_files过滤掉IGNORE_DIRS中的.git目录文件
      假如Mock API:
     """
     POST: '/v1/chat/completions'
     ---
     body(LlmResponse): {
       choices: [{
         finishReason: tool_calls
         message: {
           toolCalls!: [{
             function: {
               name: find_files
               arguments: ```
                          {"pattern": "**/HEAD"}
                          ```
             }
           }]
         }
       }]
     }
     ---
     body(LlmResponse): {
       choices: [{
         finishReason: stop
         message: {
           content: 'find_files结果：已过滤.git目录中的文件。'
         }
       }]
     }
     """
      当用户发送消息"test find_files ignores .git"
      那么收到的 Socket.IO 事件应满足:
     """
     ::eventually: {
       receivedEvents::filter: {
         name= new_message
       } : [ ... {
         data.output: ```
                      find_files结果：已过滤.git目录中的文件。

                      ---
                      ⏱️ 耗时 0秒
                      ```
       }]
     }
     """
      并且数据应为:
     """
     MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
       body.json: {
         messages: [... {
           content: 'No files found matching: **/HEAD'
           role: tool
           tool_call_id: find_files-0
         }]
       }
     }]
     """

    场景: find_files结果超过100个时触发截断
      假如Mock API:
      """
      POST: '/v1/chat/completions'
      ---
      body(LlmResponse): {
        choices: [{
          finishReason: tool_calls
          message: {
            toolCalls!: [{
              function: {
                name: find_files
                arguments: ```
                           {"pattern": "**/*.java"}
                           ```
              }
            }]
          }
        }]
      }
      ---
      body(LlmResponse): {
        choices: [{
          finishReason: stop
          message: {
            content: 'find_files截断结果：匹配过多文件已截断。'
          }
        }]
      }
      """
      当用户发送消息"test find_files truncation"
      那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
        } : [ ... {
          data.output: ```
                       find_files截断结果：匹配过多文件已截断。

                       ---
                       ⏱️ 耗时 0秒
                       ```
        }]
      }
      """
      并且数据应为:
      """
      MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
        body.json: {
          messages: [... {
            content = /.*\(limited to 100 results\)/
            role: tool
            tool_call_id: find_files-0
          }]
        }
      }]
      """

  Rule: Grep code

    场景: grep_code使用file_glob过滤Java文件
      假如Mock API:
      """
      POST: '/v1/chat/completions'
      ---
      body(LlmResponse): {
        choices: [{
          finishReason: tool_calls
          message: {
            toolCalls!: [{
              function: {
                name: grep_code
                arguments: ```
                           {"pattern": "public class JData ", "file_glob": "*.java"}
                           ```
              }
            }]
          }
        }]
      }
      ---
      body(LlmResponse): {
        choices: [{
          finishReason: stop
          message: {
            content: 'grep_code结果：已过滤Java文件。处理完成。'
          }
        }]
      }
      """
      当用户发送消息"test grep_code with file_glob"
      那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
        } : [ ... {
          data.output: ```
                       grep_code结果：已过滤Java文件。处理完成。

                       ---
                       ⏱️ 耗时 0秒
                       ```
        }]
      }
      """
      并且数据应为:
      """
      MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
        body.json: {
          messages: [... {
            content= ```
                     /workspace/jfactory-cucumber/src/main/java/org/testcharm/jfactory/cucumber/JData.java:28:public class JData {

                     ```
            role: tool
            tool_call_id: grep_code-0
          }]
        }
      }]
      """

    场景: grep_code使用file_glob过滤Java文件但是无匹配
      假如Mock API:
      """
      POST: '/v1/chat/completions'
      ---
      body(LlmResponse): {
        choices: [{
          finishReason: tool_calls
          message: {
            toolCalls!: [{
              function: {
                name: grep_code
                arguments: ```
                           {"pattern": "not match at all", "file_glob": "*.java"}
                           ```
              }
            }]
          }
        }]
      }
      ---
      body(LlmResponse): {
        choices: [{
          finishReason: stop
          message: {
            content: 'grep_code结果：已过滤Java文件。处理完成。'
          }
        }]
      }
      """
      当用户发送消息"test grep_code with file_glob"
      那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
        } : [ ... {
          data.output: ```
                       grep_code结果：已过滤Java文件。处理完成。

                       ---
                       ⏱️ 耗时 0秒
                       ```
        }]
      }
      """
      并且数据应为:
      """
      MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
        body.json: {
          messages: [... {
            content= 'No matches found.'
            role: tool
            tool_call_id: grep_code-0
          }]
        }
      }]
      """

    场景: grep_code无匹配时返回空结果
    假如Mock API:
      """
      POST: '/v1/chat/completions'
      ---
      body(LlmResponse): {
        choices: [{
          finishReason: tool_calls
          message: {
            toolCalls!: [{
              function: {
                name: grep_code
                arguments: ```
                           {"pattern": "ZZZ_NONEXISTENT_PATTERN_12345"}
                           ```
              }
            }]
          }
        }]
      }
      ---
      body(LlmResponse): {
        choices: [{
          finishReason: stop
          message: {
            content: 'grep_code结果：未找到匹配内容。'
          }
        }]
      }
      """
    当用户发送消息"test grep_code no match"
    那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
        } : [ ... {
          data.output: ```
                       grep_code结果：未找到匹配内容。

                       ---
                       ⏱️ 耗时 0秒
                       ```
        }]
      }
      """
    并且数据应为:
      """
      MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
        body.json: {
          messages: [... {
            content: 'No matches found.'
            role: tool
            tool_call_id: grep_code-0
          }]
        }
      }]
      """

    场景: grep_code使用非法正则返回搜索错误
      假如Mock API:
      """
      POST: '/v1/chat/completions'
      ---
      body(LlmResponse): {
        choices: [{
          finishReason: tool_calls
          message: {
            toolCalls!: [{
              function: {
                name: grep_code
                arguments: ```
                           {"pattern": "["}
                           ```
              }
            }]
          }
        }]
      }
      ---
      body(LlmResponse): {
        choices: [{
          finishReason: stop
          message: {
            content: 'grep_code结果：搜索错误，正则表达式无效。'
          }
        }]
      }
      """
      当用户发送消息"test grep_code invalid regex"
      那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
        } : [ ... {
          data.output: ```
                       grep_code结果：搜索错误，正则表达式无效。

                       ---
                       ⏱️ 耗时 0秒
                       ```
        }]
      }
      """
      并且数据应为:
      """
      MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
        body.json: {
          messages: [... {
            content= ```
                     Search error: rg: regex parse error:
                         (?:[)
                            ^
                     error: unclosed character class

                     ```
            role: tool
            tool_call_id: grep_code-0
          }]
        }
      }]
      """

    场景: grep_code大量匹配结果触发8000字符截断
      假如Mock API:
      """
      POST: '/v1/chat/completions'
      ---
      body(LlmResponse): {
        choices: [{
          finishReason: tool_calls
          message: {
            toolCalls!: [{
              function: {
                name: grep_code
                arguments: ```
                           {"pattern": "."}
                           ```
              }
            }]
          }
        }]
      }
      ---
      body(LlmResponse): {
        choices: [{
          finishReason: stop
          message: {
            content: 'grep_code结果：匹配过多已截断。'
          }
        }]
      }
      """
      当用户发送消息"test grep_code output truncation"
      那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
        } : [ ... {
          data.output: ```
                       grep_code结果：匹配过多已截断。

                       ---
                       ⏱️ 耗时 0秒
                       ```
        }]
      }
      """
      并且数据应为:
      """
      MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
        body.json: {
          messages: [... {
            content::should.endsWith: '(output truncated)'
            role: tool
            tool_call_id: grep_code-0
          }]
        }
      }]
      """

  Rule: Read file

    场景: read_file读取正常文件返回内容
      假如Mock API:
        """
        POST: '/v1/chat/completions'
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: tool_calls
            message: {
              toolCalls!: [{
                function: {
                  name: read_file
                  arguments: ```
                             {"file_path": "gradle.properties"}
                             ```
                }
              }]
            }
          }]
        }
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: stop
            message: {
              content: 'read_file结果：文件内容如下。'
            }
          }]
        }
        """
      当用户发送消息"test read_file not found"
      那么收到的 Socket.IO 事件应满足:
        """
        ::eventually: {
          receivedEvents::filter: {
            name= new_message
          } : [ ... {
            data.output: ```
                         read_file结果：文件内容如下。

                         ---
                         ⏱️ 耗时 0秒
                         ```
          }]
        }
        """
      并且数据应为:
        """
        MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
          body.json: {
            messages: [... {
              content: ```
                       ── gradle.properties (1-2 of 2 lines) ──
                          1 │ org.gradle.java.installations.auto-detect=true
                          2 │ org.gradle.java.installations.auto-download=true
                       ```
              role: tool
              tool_call_id: read_file-0
            }]
          }
        }]
        """

    场景: read_file读取不存在文件返回错误
      假如Mock API:
        """
        POST: '/v1/chat/completions'
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: tool_calls
            message: {
              toolCalls!: [{
                function: {
                  name: read_file
                  arguments: ```
                             {"file_path": "nonexistent_file.txt"}
                             ```
                }
              }]
            }
          }]
        }
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: stop
            message: {
              content: 'read_file结果：文件未找到。'
            }
          }]
        }
        """
      当用户发送消息"test read_file not found"
      那么收到的 Socket.IO 事件应满足:
        """
        ::eventually: {
          receivedEvents::filter: {
            name= new_message
          } : [ ... {
            data.output: ```
                         read_file结果：文件未找到。

                         ---
                         ⏱️ 耗时 0秒
                         ```
          }]
        }
        """
      并且数据应为:
        """
        MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
          body.json: {
            messages: [... {
              content: 'File not found: nonexistent_file.txt'
              role: tool
              tool_call_id: read_file-0
            }]
          }
        }]
        """

    场景: read_file读取目录路径返回错误
      假如Mock API:
        """
        POST: '/v1/chat/completions'
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: tool_calls
            message: {
              toolCalls!: [{
                function: {
                  name: read_file
                  arguments: ```
                             {"file_path": "jfactory"}
                             ```
                }
              }]
            }
          }]
        }
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: stop
            message: {
              content: 'read_file结果：该路径是目录不是文件。'
            }
          }]
        }
        """
      当用户发送消息"test read_file on directory"
      那么收到的 Socket.IO 事件应满足:
        """
        ::eventually: {
          receivedEvents::filter: {
            name= new_message
          } : [ ... {
            data.output: ```
                         read_file结果：该路径是目录不是文件。

                         ---
                         ⏱️ 耗时 0秒
                         ```
          }]
        }
        """
      并且数据应为:
        """
        MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
          body.json: {
            messages: [... {
              content: 'Not a file: jfactory'
              role: tool
              tool_call_id: read_file-0
            }]
          }
        }]
        """

    场景: read_file读取正常文件结果触发300行字符截断
      假如Mock API:
        """
        POST: '/v1/chat/completions'
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: tool_calls
            message: {
              toolCalls!: [{
                function: {
                  name: read_file
                  arguments: ```
                             {"file_path": "jfactory/README.md"}
                             ```
                }
              }]
            }
          }]
        }
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: stop
            message: {
              content: 'read_file结果：文件内容如下。'
            }
          }]
        }
        """
      当用户发送消息"test read_file not found"
      那么收到的 Socket.IO 事件应满足:
        """
        ::eventually: {
          receivedEvents::filter: {
            name= new_message
          } : [ ... {
            data.output: ```
                         read_file结果：文件内容如下。

                         ---
                         ⏱️ 耗时 0秒
                         ```
          }]
        }
        """
      并且数据应为:
        """
        MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
          body.json: {
            messages: [... {
              content::should.endsWith: ```
                                        ── Use read_file('jfactory/README.md', start_line=301) to continue ──
                                        ```
              role: tool
              tool_call_id: read_file-0
            }]
          }
        }]
        """

  Rule: Get symbols

    场景: get_symbols分析存在文件正常返回内容
      假如Mock API:
        """
        POST: '/v1/chat/completions'
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: tool_calls
            message: {
              toolCalls!: [{
                function: {
                  name: get_symbols
                  arguments: ```
                             {"file_path": "jfactory/src/main/java/org/testcharm/extensions/dal/CollectorInDAL.java"}
                             ```
                }
              }]
            }
          }]
        }
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: stop
            message: {
              content: 'get_symbols结果：符号索引完成。'
            }
          }]
        }
        """
      当用户发送消息"test get_symbols not found"
      那么收到的 Socket.IO 事件应满足:
        """
        ::eventually: {
          receivedEvents::filter: {
            name= new_message
          } : [ ... {
            data.output: ```
                         get_symbols结果：符号索引完成。

                         ---
                         ⏱️ 耗时 0秒
                         ```
          }]
        }
        """
      并且数据应为:
        """
        MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
          body.json: {
            messages: [... {
              content: ```
                       class CollectorInDAL (L15-84): public class CollectorInDAL implements org.testcharm.dal.runtime.Extension {
                           class extend (L15-84): public class CollectorInDAL implements org.testcharm.dal.runtime.Extension {
                           method extend (L17-70): @Override
                           class Object (L25-30): .registerPropertyAccessor(Collector.class, new PropertyAccessor<Collector>() {
                           method Object (L26-29): @Override
                           class Collector (L37-42): return new InfiniteDALCollection<Collector>(Collector::new) {
                           method Collector (L38-41): @Override
                           class match (L46-58): .registerOperator(Operators.EQUAL, new Operation<JFactoryCollector, ExpectationFactory>() {
                           method match (L47-50): @Override
                           method operateData (L52-57): @Override
                           method verificationOptAsAssignmentOpt (L72-83): private Optional<Checker> verificationOptAsAssignmentOpt(Data<?> actual) {
                           class failed (L74-81): return Optional.of(new Checker() {
                           method failed (L75-80): @Override
                       ```
              role: tool
              tool_call_id: get_symbols-0
            }]
          }
        }]
        """

    场景: get_symbols分析存在文件但类型不支持
      假如Mock API:
        """
        POST: '/v1/chat/completions'
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: tool_calls
            message: {
              toolCalls!: [{
                function: {
                  name: get_symbols
                  arguments: ```
                             {"file_path": "build.gradle"}
                             ```
                }
              }]
            }
          }]
        }
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: stop
            message: {
              content: 'get_symbols结果：该文件类型不支持符号索引。'
            }
          }]
        }
        """
      当用户发送消息"test get_symbols not found"
      那么收到的 Socket.IO 事件应满足:
        """
        ::eventually: {
          receivedEvents::filter: {
            name= new_message
          } : [ ... {
            data.output: ```
                         get_symbols结果：该文件类型不支持符号索引。

                         ---
                         ⏱️ 耗时 0秒
                         ```
          }]
        }
        """
      并且数据应为:
        """
        MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
          body.json: {
            messages: [... {
              content: 'Unsupported language for: build.gradle'
              role: tool
              tool_call_id: get_symbols-0
            }]
          }
        }]
        """

    场景: get_symbols分析不存在文件返回错误
      假如Mock API:
        """
        POST: '/v1/chat/completions'
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: tool_calls
            message: {
              toolCalls!: [{
                function: {
                  name: get_symbols
                  arguments: ```
                             {"file_path": "nonexistent.py"}
                             ```
                }
              }]
            }
          }]
        }
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: stop
            message: {
              content: 'get_symbols结果：符号文件未找到。'
            }
          }]
        }
        """
      当用户发送消息"test get_symbols not found"
      那么收到的 Socket.IO 事件应满足:
        """
        ::eventually: {
          receivedEvents::filter: {
            name= new_message
          } : [ ... {
            data.output: ```
                         get_symbols结果：符号文件未找到。

                         ---
                         ⏱️ 耗时 0秒
                         ```
          }]
        }
        """
      并且数据应为:
        """
        MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
          body.json: {
            messages: [... {
              content: 'File not found: nonexistent.py'
              role: tool
              tool_call_id: get_symbols-0
            }]
          }
        }]
        """

  Rule: Get repo map

    场景: get_repo_map带glob过滤存在文件匹配
      假如Mock API:
        """
        POST: '/v1/chat/completions'
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: tool_calls
            message: {
              toolCalls!: [{
                function: {
                  name: get_repo_map
                  arguments: ```
                             {"file_glob": "**/JData.java"}
                             ```
                }
              }]
            }
          }]
        }
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: stop
            message: {
              content: 'get_repo_map结果：符号索引完成。'
            }
          }]
        }
        """
      当用户发送消息"test get_repo_map with glob"
      那么收到的 Socket.IO 事件应满足:
        """
        ::eventually: {
          receivedEvents::filter: {
            name= new_message
          } : [ ... {
            data.output: ```
                         get_repo_map结果：符号索引完成。

                         ---
                         ⏱️ 耗时 0秒
                         ```
          }]
        }
        """
      并且数据应为:
        """
        MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
          body.json: {
            messages: [... {
              content: ```
                       Repository symbol map — 1 files, 45 symbols:
                       ── jfactory-cucumber/src/main/java/org/testcharm/jfactory/cucumber/JData.java ──
                         constructor JData (L32): public JData(JFactory jFactory) {
                         method prepare (L41): public <T> List<T> prepare(String traitsSpec, DocData docData) {
                         method DocData (L52): @DocStringType
                         method DocData (L57): @DataTableType
                         method prepare (L70): @SuppressWarnings("unchecked")
                         method prepare (L75): @SuppressWarnings("unchecked")
                         method prepare (L81): @SuppressWarnings("unchecked")
                         method prepare (L87): public <T> List<T> prepare(Class<T> type, DocData docData) {
                         method prepare (L98): @SuppressWarnings("unchecked")
                         method prepare (L103): @SafeVarargs
                         method prepare (L108): public <T> List<T> prepare(Class<T> type, List<? extends Map<String, ?>> data) {
                         method removeTransposeSymbol (L112): private static List<List<String>> removeTransposeSymbol(DataTable dataTable) {
                         method needTranspose (L118): private static boolean needTranspose(DataTable dataTable) {
                         method allShould (L126): public void allShould(String queryExpression, String dalExpression) {
                         method should (L134): public void should(String queryExpression, String dalExpression) {
                         method T (L138): public <T> T query(String queryExpression) {
                         method queryAll (L142): public <T> Collection<T> queryAll(String queryExpression) {
                         method prepare (L149): public <T> List<T> prepare(int count, String traitsSpec) {
                         method defaultProperties (L153): private List<Map<String, ?>> defaultProperties(int count) {
                         method prepareAttachments (L161): public void prepareAttachments(String beanProperty, String traitsSpec, DocData docData) {
                         method prepareAttachments (L174): public <T> List<T> prepareAttachments(String beanProperty, String traitsSpec, String expression) {
                         method prepareAttachments (L178): @SuppressWarnings("unchecked")
                         method prepareAttachments (L183): public <T> List<T> prepareAttachments(String beanProperty, String traitsSpec, List<? extends Map<String, ?>> data) {
                         method setupAssociation (L187): @SuppressWarnings("unchecked")
                         method prepareAttachments (L206): public <T> List<T> prepareAttachments(String beanProperty, int count, String traitsSpec) {
                         method prepareAttachments (L214): public void prepareAttachments(String traitsSpec, String reverseAssociationProperty, String queryExpression, DocData docData) {
                         method prepareAttachments (L227): @SuppressWarnings("unchecked")
                         method prepareAttachments (L234): @SuppressWarnings("unchecked")
                         method prepareAttachments (L240): public <T> List<T> prepareAttachments(String traitsSpec, String reverseAssociationProperty, String queryExpression,
                         method addAssociationProperty (L245): private List<Map<String, ?>> addAssociationProperty(String reverseAssociationProperty, String queryExpression,
                         method prepareAttachments (L255): public <T> List<T> prepareAttachments(int count, String traitsSpec, String reverseAssociationProperty,
                         method prepare (L264): public void prepare(String data) {
                         method allDataShouldBe (L274): public void allDataShouldBe(String dalExpression) {
                         class QueryExpression (L278): private class QueryExpression {
                             class String (L278): private class QueryExpression {
                             constructor QueryExpression (L283): public QueryExpression(String expression) {
                             method queryAll (L293): @SuppressWarnings("unchecked")
                             method T (L298): public <T> T query() {
                         class DocData (L306): public static class DocData {
                             class Type (L306): public static class DocData {
                             constructor DocData (L310): public DocData(String expression) {
                             constructor DocData (L315): public DocData(List<Map<String, String>> maps) {
                             method String (L320): public String expression() {
                             method maps (L324): @SuppressWarnings("unchecked")
                             enum Type (L329): enum Type {
                       ```
              role: tool
              tool_call_id: get_repo_map-0
            }]
          }
        }]
        """

    场景: get_repo_map分析一个目录被忽略
      假如Mock API:
        """
        POST: '/v1/chat/completions'
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: tool_calls
            message: {
              toolCalls!: [{
                function: {
                  name: get_repo_map
                  arguments: ```
                             {"file_glob": "bean-util"}
                             ```
                }
              }]
            }
          }]
        }
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: stop
            message: {
              content: 'get_repo_map结果：该目录被忽略，未进行符号索引。'
            }
          }]
        }
        """
      当用户发送消息"test get_repo_map with glob"
      那么收到的 Socket.IO 事件应满足:
        """
        ::eventually: {
          receivedEvents::filter: {
            name= new_message
          } : [ ... {
            data.output: ```
                         get_repo_map结果：该目录被忽略，未进行符号索引。

                         ---
                         ⏱️ 耗时 0秒
                         ```
          }]
        }
        """
      并且数据应为:
        """
        MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
          body.json: {
            messages: [... {
              content: ```
                       No parseable source files found. (Is tree-sitter-languages installed?)
                       ```
              role: tool
              tool_call_id: get_repo_map-0
            }]
          }
        }]
        """

    场景: get_repo_map分析一个被忽略的文件
      假如Mock API:
        """
        POST: '/v1/chat/completions'
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: tool_calls
            message: {
              toolCalls!: [{
                function: {
                  name: get_repo_map
                  arguments: ```
                             {"file_glob": ".gitignore"}
                             ```
                }
              }]
            }
          }]
        }
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: stop
            message: {
              content: 'get_repo_map结果：该文件被忽略，未进行符号索引。'
            }
          }]
        }
        """
      当用户发送消息"test get_repo_map with glob"
      那么收到的 Socket.IO 事件应满足:
        """
        ::eventually: {
          receivedEvents::filter: {
            name= new_message
          } : [ ... {
            data.output: ```
                         get_repo_map结果：该文件被忽略，未进行符号索引。

                         ---
                         ⏱️ 耗时 0秒
                         ```
          }]
        }
        """
      并且数据应为:
        """
        MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
          body.json: {
            messages: [... {
              content: ```
                       No parseable source files found. (Is tree-sitter-languages installed?)
                       ```
              role: tool
              tool_call_id: get_repo_map-0
            }]
          }
        }]
        """

    场景: get_repo_map分析一个语言无法识别的文件
      假如Mock API:
        """
        POST: '/v1/chat/completions'
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: tool_calls
            message: {
              toolCalls!: [{
                function: {
                  name: get_repo_map
                  arguments: ```
                             {"file_glob": "README.md"}
                             ```
                }
              }]
            }
          }]
        }
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: stop
            message: {
              content: 'get_repo_map结果：该文件类型不支持符号索引。'
            }
          }]
        }
        """
      当用户发送消息"test get_repo_map with glob"
      那么收到的 Socket.IO 事件应满足:
        """
        ::eventually: {
          receivedEvents::filter: {
            name= new_message
          } : [ ... {
            data.output: ```
                         get_repo_map结果：该文件类型不支持符号索引。

                         ---
                         ⏱️ 耗时 0秒
                         ```
          }]
        }
        """
      并且数据应为:
        """
        MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
          body.json: {
            messages: [... {
              content: ```
                       No parseable source files found. (Is tree-sitter-languages installed?)
                       ```
              role: tool
              tool_call_id: get_repo_map-0
            }]
          }
        }]
        """

    场景: get_repo_map带glob过滤不存在文件匹配
      假如Mock API:
        """
        POST: '/v1/chat/completions'
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: tool_calls
            message: {
              toolCalls!: [{
                function: {
                  name: get_repo_map
                  arguments: ```
                             {"file_glob": "**/*.py"}
                             ```
                }
              }]
            }
          }]
        }
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: stop
            message: {
              content: 'get_repo_map结果：符号索引完成。'
            }
          }]
        }
        """
      当用户发送消息"test get_repo_map with glob"
      那么收到的 Socket.IO 事件应满足:
        """
        ::eventually: {
          receivedEvents::filter: {
            name= new_message
          } : [ ... {
            data.output: ```
                         get_repo_map结果：符号索引完成。

                         ---
                         ⏱️ 耗时 0秒
                         ```
          }]
        }
        """
      并且数据应为:
        """
        MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
          body.json: {
            messages: [... {
              content: 'No parseable source files found. (Is tree-sitter-languages installed?)'
              role: tool
              tool_call_id: get_repo_map-0
            }]
          }
        }]
        """

    场景: get_repo_map带glob过滤存在文件匹配触发200个文件数限制
      假如Mock API:
        """
        POST: '/v1/chat/completions'
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: tool_calls
            message: {
              toolCalls!: [{
                function: {
                  name: get_repo_map
                  arguments: ```
                             {"file_glob": "**/*.java"}
                             ```
                }
              }]
            }
          }]
        }
        ---
        body(LlmResponse): {
          choices: [{
            finishReason: stop
            message: {
              content: 'get_repo_map结果：符号索引完成。'
            }
          }]
        }
        """
      当用户发送消息"test get_repo_map with glob"
      那么收到的 Socket.IO 事件应满足:
        """
        ::eventually: {
          receivedEvents::filter: {
            name= new_message
          } : [ ... {
            data.output: ```
                         get_repo_map结果：符号索引完成。

                         ---
                         ⏱️ 耗时 0秒
                         ```
          }]
        }
        """
      并且数据应为:
        """
        MockApi::filter: { POST: '/v1/chat/completions' } : [ ... {
          body.json: {
            messages: [... {
              content::should.endsWith: ```
                                        ... (limited to 200 files)
                                        ```
              role: tool
              tool_call_id: get_repo_map-0
            }]
          }
        }]
        """

