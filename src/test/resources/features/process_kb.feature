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
          test-charm.txt: ```
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
          new-file.txt: ```
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
          rule.txt: ```
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
          a.txt: ```
                 Scenario: feature A - scenario A
                   Given step A
                 ```
          sub: {
            b.txt: ```
                   Scenario: feature B - scenario B
                     Given step B
                   ```
          }
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
            keyword: 'b.txt'
          }
        } {
          params: {
            limit: '100'
            keyword: 'a.txt'
          }
        }]

        MockApi::filter: { POST[/dify/v1/datasets/testcharm/documents/feature-file/update-by-file]: {...}}
        : [{
          formData: [{
            fieldName= file
            name= 'b.txt'
            inputStream.string: ```
                                Scenario: feature B - scenario B
                                  Given step B
                                ```
          }]
        } {
          formData: [{
            fieldName= file
            name= 'a.txt'
            inputStream.string: ```
                                Scenario: feature A - scenario A
                                  Given step A
                                ```
          }]
        }]
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
          tagged.txt: ```
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
          scenario-tag.txt: ```
                            Scenario: feature Y - scenario Y
                              Given step Y
                            ```
        }
      }
      """

