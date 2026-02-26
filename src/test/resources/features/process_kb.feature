# language: zh-CN
功能: 知识库处理

  背景:
    假如Mock API:
      """
      : {
        path.value= '/dify/v1/datasets'
        method.value= 'GET'
      }
      ---
      code: 200
      body: ```
            {
                "data": [
                    {
                        "id": "testcharm",
                        "name": "TestCharm"
                    }
                ]
            }
            ```
      """
    假如Mock API:
      """
      : {
        path.value= '/dify/v1/datasets/testcharm/documents'
        method.value= 'GET'
      }
      ---
      code: 200
      body: ```
            {
                "data": [
                    {
                        "id": "feature-file"
                    }
                ]
            }
            ```
      """
    假如Mock API:
      """
      : {
        path.value= '/dify/v1/datasets/testcharm/documents/feature-file/update-by-file'
        method.value= 'POST'
      }
      ---
      code: 200
      body: ```
            {
                "document": {
                    "id": "feature-file"
                }
            }
            ```
      """

  场景: 基本文件处理
    假如存在"Feature文件":
      """
      fileName: 'test-charm.feature'
      content: ```
               Feature: query data

                 Scenario: Query data use jfactory
                   Given "Orders":
                     | id | code |
                     | 1  | SN1  |
                   Then query data by jfactory:
                     '''
                     : {
                       Orders: [{
                         id= 1
                         code= SN1
                       }]
                     }
                     '''
               ```
      """
    假如Mock API:
      """
      : {
        path.value= '/dify/v1/datasets'
        method.value= 'GET'
        getHeader: {
          Authorization= ['Bearer difyDatasetApiKey']
          Accept, 'Content-Type'= ['application/json']
        }
      }
      ---
      code: 200
      body: ```
            {
                "data": [
                    {
                        "id": "2200ec1f-fd85-402f-8afc-a3052135a105",
                        "name": "JFactory"
                    }
                ]
            }
            ```
      """
    假如Mock API:
      """
      : {
        path.value= '/dify/v1/datasets/2200ec1f-fd85-402f-8afc-a3052135a105/documents'
        method.value= 'GET'
        getHeader: {
          Authorization= ['Bearer difyDatasetApiKey']
          Accept, 'Content-Type'= ['application/json']
        }
      }
      ---
      code: 200
      body: ```
            {
                "data": [
                    {
                        "id": "73d03751-93cc-42da-9079-0224fd2cc3c6"
                    }
                ]
            }
            ```
      """
    假如Mock API:
      """
      : {
        path.value= '/dify/v1/datasets/2200ec1f-fd85-402f-8afc-a3052135a105/documents/73d03751-93cc-42da-9079-0224fd2cc3c6/update-by-file'
        method.value= 'POST'
        getHeader: {
          Authorization= ['Bearer difyDatasetApiKey']
          'Content-Type'= [/^multipart\/form-data;.*/]
        }
      }
      ---
      code: 200
      body: ```
            {
                "document": {
                    "id": "73d03751-93cc-42da-9079-0224fd2cc3c6"
                }
            }
            ```
      """
    当用以下"命令行参数"执行时:
      """
      src: '/tmp/ai_for_test_charm/input'
      dst: '/tmp/ai_for_test_charm/output/JFactory'
      """
    那么输出的文件应为:
      """
      : {
        JFactory: {
          test-charm_done.txt: ```
                          Scenario: query data - Query data use jfactory
                            Given "Orders":
                              | id | code |
                              | 1  | SN1  |
                            Then query data by jfactory:
                              '''
                              : {
                                Orders: [{
                                  id= 1
                                  code= SN1
                                }]
                              }
                              '''
                          ```
        }
      }
      """
    并且数据应为ex:
      """
      : {
        MockApi::filter: { GET[/dify/v1/datasets/2200ec1f-fd85-402f-8afc-a3052135a105/documents]: {...}}
        : {
          params: {
            limit: '100'
            keyword: 'test-charm.txt'
          }
        }

        MockApi::filter: { POST[/dify/v1/datasets/2200ec1f-fd85-402f-8afc-a3052135a105/documents/73d03751-93cc-42da-9079-0224fd2cc3c6/update-by-file]: {...}}
        : {
          formData: [{
            fieldName= file
            name= 'test-charm.txt'
            inputStream.string: ```
                                Scenario: query data - Query data use jfactory
                                  Given "Orders":
                                    | id | code |
                                    | 1  | SN1  |
                                  Then query data by jfactory:
                                    '''
                                    : {
                                      Orders: [{
                                        id= 1
                                        code= SN1
                                      }]
                                    }
                                    '''
                                ```
          }]
        }

        系统日志::filter: { message: '上传成功: test-charm.txt' }
        : { ::size= 1 }

      }
      """

  场景: 知识库中不存在同名文件时创建新文件
    假如存在"Feature文件":
      """
      fileName: 'new-file.feature'
      content: ```
               Feature: new feature

                 Scenario: new scenario
                   Given new step
               ```
      """
    假如Mock API:
      """
      : {
        path.value= '/dify/v1/datasets/testcharm/documents'
        method.value= 'GET'
      }
      ---
      code: 200
      body: ```
            {
                "data": []
            }
            ```
      """
    假如Mock API:
      """
      : {
        path.value= '/dify/v1/datasets/testcharm/document/create-by-file'
        method.value= 'POST'
      }
      ---
      code: 200
      body: ```
            {
                "document": {
                    "id": "new-doc-id"
                }
            }
            ```
      """
    当用以下"命令行参数"执行时:
      """
      {}
      """
    那么输出的文件应为:
      """
      : {
        TestCharm: {
          new-file_done.txt: ```
                             Scenario: new feature - new scenario
                               Given new step
                             ```
        }
      }
      """
    并且数据应为ex:
      """
      : {
        MockApi::filter: { POST[/dify/v1/datasets/testcharm/document/create-by-file]: {...}}
        : {
          formData: [{
            fieldName= file
            name= 'new-file.txt'
            inputStream.string: ```
                                Scenario: new feature - new scenario
                                  Given new step
                                ```
          }]
        }
      }
      """

  场景: Rule的title也要加到Scenario title中
    假如存在"Feature文件":
      """
      fileName: 'rule.feature'
      content: ```
               Feature: feature X

                 Rule: rule Y

                   Scenario: scenario Z
                     Given step Z
               ```
      """
    当用以下"命令行参数"执行时:
      """
      {}
      """
    那么输出的文件应为:
      """
      : {
        TestCharm: {
          rule_done.txt: ```
                         Scenario: feature X - rule Y - scenario Z
                           Given step Z
                         ```
        }
      }
      """

  场景: 多个feature文件及子目录下的feature文件
    假如存在"Feature文件":
      """
      fileName: 'a.feature'
      content: ```
               Feature: feature A

                 Scenario: scenario A
                   Given step A
               ```
      """
    假如存在"Feature文件":
      """
      fileName: 'sub/b.feature'
      content: ```
               Feature: feature B

                 Scenario: scenario B
                   Given step B
               ```
      """
    当用以下"命令行参数"执行时:
      """
      {}
      """
    那么输出的文件应为:
      """
      : {
        TestCharm: {
          a_done.txt: ```
                      Scenario: feature A - scenario A
                        Given step A
                      ```
          sub-b_done.txt: ```
                          Scenario: feature B - scenario B
                            Given step B
                          ```
        }
      }
      """
    并且数据应为ex:
      """
      : {
        MockApi::filter: { GET[/dify/v1/datasets/testcharm/documents]: {...}}
        : [{
          params: {
            limit: '100'
            keyword: 'a.txt'
          }
        } {
          params: {
            limit: '100'
            keyword: 'sub-b.txt'
          }
        }]

        MockApi::filter: { POST[/dify/v1/datasets/testcharm/documents/feature-file/update-by-file]: {...}}
        : [{
          formData: [{
            fieldName= file
            name= 'a.txt'
            inputStream.string: ```
                                Scenario: feature A - scenario A
                                  Given step A
                                ```
          }]
        } {
          formData: [{
            fieldName= file
            name= 'sub-b.txt'
            inputStream.string: ```
                                Scenario: feature B - scenario B
                                  Given step B
                                ```
          }]
        }]

        等待时间.seconds[]: [ 1, 1 ]
      }
      """

  场景: 不同子目录下的同名文件输出时加子目录前缀
    假如存在"Feature文件":
      """
      fileName: 'dir1/common.feature'
      content: ```
               Feature: feature in dir1

                 Scenario: scenario in dir1
                   Given step in dir1
               ```
      """
    假如存在"Feature文件":
      """
      fileName: 'dir2/common.feature'
      content: ```
               Feature: feature in dir2

                 Scenario: scenario in dir2
                   Given step in dir2
               ```
      """
    当用以下"命令行参数"执行时:
      """
      {}
      """
    那么输出的文件应为:
      """
      : {
        TestCharm: {
          dir1-common_done.txt: ```
                                Scenario: feature in dir1 - scenario in dir1
                                  Given step in dir1
                                ```
          dir2-common_done.txt: ```
                                Scenario: feature in dir2 - scenario in dir2
                                  Given step in dir2
                                ```
        }
      }
      """

  场景: 去掉Feature之前的tag和注释
    假如存在"Feature文件":
      """
      fileName: 'tagged.feature'
      content: ```
               @tag1 @tag2
               # This is a comment
               Feature: feature with tags

                 Scenario: scenario X
                   Given step X
               ```
      """
    当用以下"命令行参数"执行时:
      """
      {}
      """
    那么输出的文件应为:
      """
      : {
        TestCharm: {
          tagged_done.txt: ```
                           Scenario: feature with tags - scenario X
                             Given step X
                           ```
        }
      }
      """

  场景: 去掉Scenario的tag
    假如存在"Feature文件":
      """
      fileName: 'scenario-tag.feature'
      content: ```
               Feature: feature Y

                 @scenario-tag
                 Scenario: scenario Y
                   Given step Y
               ```
      """
    当用以下"命令行参数"执行时:
      """
      {}
      """
    那么输出的文件应为:
      """
      : {
        TestCharm: {
          scenario-tag_done.txt: ```
                                 Scenario: feature Y - scenario Y
                                   Given step Y
                                 ```
        }
      }
      """

  场景: 多个Scenario之间有空行
    假如存在"Feature文件":
      """
      fileName: 'multi-scenario.feature'
      content: ```
               Feature: multi scenario

                 Scenario: first
                   Given step 1

                 Scenario: second
                   Given step 2
               ```
      """
    当用以下"命令行参数"执行时:
      """
      {}
      """
    那么输出的文件应为:
      """
      : {
        TestCharm: {
          multi-scenario_done.txt: ```
                                   Scenario: multi scenario - first
                                     Given step 1

                                   Scenario: multi scenario - second
                                     Given step 2
                                   ```
        }
      }
      """

  场景: 更新文件时服务器返回500会自动重试
    假如存在"Feature文件":
      """
      fileName: 'retry-update.feature'
      content: ```
               Feature: retry update

                 Scenario: retry update scenario
                   Given retry update step
               ```
      """
    假如Mock API:
      """
      : {
        path.value= '/dify/v1/datasets/testcharm/documents/feature-file/update-by-file'
        method.value= 'POST'
      }
      ---
      code: 500
      body: failed
      times: 2
      ---
      code: 200
      body: ```
            {
                "document": {
                    "id": "feature-file"
                }
            }
            ```
      """
    当用以下"命令行参数"执行时:
      """
      {}
      """
    那么输出的文件应为:
      """
      : {
        TestCharm: {
          retry-update_done.txt: ```
                                 Scenario: retry update - retry update scenario
                                   Given retry update step
                                 ```
        }
      }
      """
    并且数据应为ex:
      """
      : {
        MockApi::filter: { POST[/dify/v1/datasets/testcharm/documents/feature-file/update-by-file]: {...}}
        : [{...} {...} {...}]

        系统日志::filter: { message: '上传成功: retry-update.txt' }
        : { ::size= 1 }

      }
      """

  场景: 创建文件时服务器返回500会自动重试
    假如存在"Feature文件":
      """
      fileName: 'retry-create.feature'
      content: ```
               Feature: retry create

                 Scenario: retry create scenario
                   Given retry create step
               ```
      """
    假如Mock API:
      """
      : {
        path.value= '/dify/v1/datasets/testcharm/documents'
        method.value= 'GET'
      }
      ---
      code: 200
      body: ```
            {
                "data": []
            }
            ```
      """
    假如Mock API:
      """
      : {
        path.value= '/dify/v1/datasets/testcharm/document/create-by-file'
        method.value= 'POST'
      }
      ---
      code: 500
      times: 2
      ---
      code: 200
      body: ```
            {
                "document": {
                    "id": "new-doc-id"
                }
            }
            ```
      """
    当用以下"命令行参数"执行时:
      """
      {}
      """
    那么输出的文件应为:
      """
      : {
        TestCharm: {
          retry-create_done.txt: ```
                                 Scenario: retry create - retry create scenario
                                   Given retry create step
                                 ```
        }
      }
      """
    并且数据应为ex:
      """
      : {
        MockApi::filter: { POST[/dify/v1/datasets/testcharm/document/create-by-file]: {...}}
        : [{...} {...} {...}]

        系统日志::filter: { message: '上传成功: retry-create.txt' }
        : { ::size= 1 }

      }
      """

  场景: 上传失败时打印错误日志包含文件名和响应内容
    假如存在"Feature文件":
      """
      fileName: 'fail-upload.feature'
      content: ```
               Feature: fail upload

                 Scenario: fail upload scenario
                   Given fail upload step
               ```
      """
    假如Mock API:
      """
      : {
        path.value= '/dify/v1/datasets/testcharm/documents/feature-file/update-by-file'
        method.value= 'POST'
      }
      ---
      code: 403
      body: 'Forbidden: you have been blocked'
      """
    当用以下"命令行参数"执行时:
      """
      retryCount: 0
      """
    那么数据应为ex:
      """
      : {
        系统日志::filter: {
          level.levelStr: 'ERROR'
          message: '上传失败: fail-upload.txt, response: Forbidden: you have been blocked'
        }
        : { ::size= 1 }
      }
      """

  场景: 使用--disable-upload时不上传到Dify
    假如存在"Feature文件":
      """
      fileName: 'no-upload.feature'
      content: ```
               Feature: no upload feature

                 Scenario: no upload scenario
                   Given no upload step
               ```
      """
    当用以下"命令行参数"执行时:
      """
      disableUpload: true
      """
    那么输出的文件应为:
      """
      : {
        TestCharm: {
          no-upload.txt: ```
                        Scenario: no upload feature - no upload scenario
                          Given no upload step
                        ```
        }
      }
      """

  场景: --upload-only跳过文件生成只上传已有文件
    假如存在"输出文件":
      """
      fileName: 'pre-existing.txt'
      content: 'pre-existing content'
      """
    当用以下"命令行参数"执行时:
      """
      uploadOnly: true
      """
    那么输出的文件应为:
      """
      : {
        TestCharm: {
          pre-existing_done.txt= 'pre-existing content'
        }
      }
      """
    并且数据应为ex:
      """
      : {
        MockApi::filter: { POST[/dify/v1/datasets/testcharm/documents/feature-file/update-by-file]: {...}}
        : [{
          formData: [{
            fieldName= file
            name= 'pre-existing.txt'
            inputStream.string= 'pre-existing content'
          }]
        }]

        系统日志::filter: { message: '上传成功: pre-existing.txt' }
        : { ::size= 1 }
      }
      """

  场景: --upload-only时跳过_done文件
    假如存在"输出文件":
      """
      fileName: 'already-done_done.txt'
      content: 'done content'
      """
    假如存在"输出文件":
      """
      fileName: 'not-done-yet.txt'
      content: 'pending content'
      """
    当用以下"命令行参数"执行时:
      """
      uploadOnly: true
      """
    那么输出的文件应为:
      """
      : {
        TestCharm: {
          already-done_done.txt= 'done content'
          not-done-yet_done.txt= 'pending content'
        }
      }
      """
    并且数据应为ex:
      """
      : {
        MockApi::filter: { POST[/dify/v1/datasets/testcharm/documents/feature-file/update-by-file]: {...}}
        : [{
          formData: [{
            fieldName= file
            name= 'not-done-yet.txt'
            inputStream.string= 'pending content'
          }]
        }]

        系统日志::filter: { message: '上传成功: not-done-yet.txt' }
        : { ::size= 1 }
      }
      """

