# language: zh-CN
功能: 知识库处理

  Rule: Feature文件处理

    场景: 基本文件处理
      假如存在"Feature文件":
        """
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
      当用以下"命令行参数"执行时:
        """
        src: '/tmp/ai_for_test_charm/input'
        dst: '/tmp/ai_for_test_charm/output/JFactory'
        """
      那么输出的文件应为:
        """
        : {
          JFactory: {
            test-charm.feature: ```
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
          }
        }
        """