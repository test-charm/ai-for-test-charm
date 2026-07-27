# language: zh-CN
@api-login
功能: 符号节点类型覆盖测试 — Java

  确保 _SYMBOL_NODE_TYPES 中每种 Java 相关的节点类型都能被正确提取。

  场景: get_symbols提取annotation_type_declaration类型符号
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
                           {"file_path": "jfactory/src/main/java/org/testcharm/jfactory/Global.java"}
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
            content: 'get_symbols结果：符号检查完成-annotation_type_declaration。'
          }
        }]
      }
      """
    当用户发送消息"test symbol type annotation_type_declaration"
    那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
        } : [ ... {
          data.output: ```
                       get_symbols结果：符号检查完成-annotation_type_declaration。

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
            content: 'annotation Global (L9-12): @Target(TYPE)'
            role: tool
            tool_call_id: get_symbols-0
          }]
        }
      }]
      """

  场景: get_symbols提取class_declaration类型符号
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
                           {"file_path": "DAL-java/src/main/java/org/testcharm/dal/util/TextUtil.java"}
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
            content: 'get_symbols结果：符号检查完成-class_declaration。'
          }
        }]
      }
      """
    当用户发送消息"test symbol type class_declaration"
    那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
        } : [ ... {
          data.output: ```
                       get_symbols结果：符号检查完成-class_declaration。

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
                     class TextUtil (L7-25): public class TextUtil {
                         class lines (L7-25): public class TextUtil {
                         method lines (L8-10): public static List<String> lines(String content) {
                         method String (L12-16): public static String join(List<Character> characters) {
                         method differentPosition (L18-24): public static int differentPosition(String expected, String actual) {
                     ```
            role: tool
            tool_call_id: get_symbols-0
          }]
        }
      }]
      """

  场景: get_symbols提取constructor_declaration类型符号
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
                           {"file_path": "DAL-java/src/main/java/org/testcharm/dal/DAL.java"}
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
            content: 'get_symbols结果：符号检查完成-constructor_declaration。'
          }
        }]
      }
      """
    当用户发送消息"test symbol type constructor_declaration"
    那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
        } : [ ... {
          data.output: ```
                       get_symbols结果：符号检查完成-constructor_declaration。

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
                     class DAL (L25-157): public class DAL {
                         class Compiler (L25-157): public class DAL {
                         constructor DAL (L32-35): @Deprecated
                         method DAL (L37-40): @Deprecated
                         method DAL (L42-48): @Deprecated
                         method DAL (L50-52): public static DAL dal() {
                         constructor DAL (L54-56): public DAL(String name) {
                         method String (L58-60): public String getName() {
                         method DAL (L62-64): public static synchronized DAL dal(String name) {
                         method DAL (L66-71): public static DAL create(String name, Class<?>... exceptExtensions) {
                         method RuntimeContextBuilder (L73-75): public RuntimeContextBuilder getRuntimeContextBuilder() {
                         method evaluateAll (L77-79): public <T> List<T> evaluateAll(Object input, String expressions) {
                         method evaluateAll (L81-83): public <T> List<T> evaluateAll(InputCode<Object> input, String expressions) {
                         method evaluateAll (L85-97): @SuppressWarnings("unchecked")
                         method T (L99-101): public <T> T evaluate(Object input, String expression) {
                         method T (L103-105): public <T> T evaluate(InputCode<Object> input, String expression) {
                         method T (L107-109): public <T> T evaluate(InputCode<Object> input, String expression, Class<?> rootSchema) {
                         method T (L111-121): @SuppressWarnings("unchecked")
                         method DALNode (L123-128): public DALNode compileSingle(String expression, DALRuntimeContext runtimeContext) {
                         method compile (L130-133): public List<DALNode> compile(String expression, DALRuntimeContext runtimeContext) {
                         method getOperandPosition (L135-137): private int getOperandPosition(DALNode node) {
                         method String (L139-141): private String format(String expression) {
                         method DAL (L143-152): public DAL extend(Class<?>... excepts) {
                         method wrap (L154-156): public Data<?> wrap(Object object) {
                     ```
            role: tool
            tool_call_id: get_symbols-0
          }]
        }
      }]
      """

  场景: get_symbols提取enum_declaration类型符号
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
                           {"file_path": "DAL-java/src/main/java/org/testcharm/dal/runtime/Operators.java"}
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
            content: 'get_symbols结果：符号检查完成-enum_declaration。'
          }
        }]
      }
      """
    当用户发送消息"test symbol type enum_declaration"
    那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
        } : [ ... {
          data.output: ```
                       get_symbols结果：符号检查完成-enum_declaration。

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
            content: 'enum Operators (L3-5): public enum Operators {'
            role: tool
            tool_call_id: get_symbols-0
          }]
        }
      }]
      """

  场景: get_symbols提取interface_declaration类型符号
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
                           {"file_path": "DAL-extension-basic/src/main/java/org/testcharm/dal/extensions/basic/CheckerType.java"}
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
            content: 'get_symbols结果：符号检查完成-interface_declaration。'
          }
        }]
      }
      """
    当用户发送消息"test symbol type interface_declaration"
    那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
        } : [ ... {
          data.output: ```
                       get_symbols结果：符号检查完成-interface_declaration。

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
                     interface CheckerType (L3-21): public interface CheckerType {
                         method String (L4-4): String getType();
                         interface Equals (L6-12): interface Equals extends CheckerType {
                         method String (L8-11): @Override
                         interface Matches (L14-20): interface Matches extends CheckerType {
                         method String (L16-19): @Override
                     ```
            role: tool
            tool_call_id: get_symbols-0
          }]
        }
      }]
      """

  场景: get_symbols提取method_declaration类型符号
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
                           {"file_path": "DAL-java/src/main/java/org/testcharm/dal/util/TextUtil.java"}
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
            content: 'get_symbols结果：符号检查完成-method_declaration。'
          }
        }]
      }
      """
    当用户发送消息"test symbol type method_declaration"
    那么收到的 Socket.IO 事件应满足:
      """
      ::eventually: {
        receivedEvents::filter: {
          name= new_message
        } : [ ... {
          data.output: ```
                       get_symbols结果：符号检查完成-method_declaration。

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
                     class TextUtil (L7-25): public class TextUtil {
                         class lines (L7-25): public class TextUtil {
                         method lines (L8-10): public static List<String> lines(String content) {
                         method String (L12-16): public static String join(List<Character> characters) {
                         method differentPosition (L18-24): public static int differentPosition(String expected, String actual) {
                     ```
            role: tool
            tool_call_id: get_symbols-0
          }]
        }
      }]
      """
