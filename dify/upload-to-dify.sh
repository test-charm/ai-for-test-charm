#!/bin/bash
set -euo pipefail

DEBUG_SUSPEND="n"
if [[ "${1:-}" == "--debug-suspend" ]]; then
  DEBUG_SUSPEND="y"
  shift
fi

SRC_DIR="${1:?Usage: $0 [--debug-suspend] <源目录路径> <数据集名称> [其他参数...]}"
DATASET_NAME="${2:?Usage: $0 [--debug-suspend] <源目录路径> <数据集名称> [其他参数...]}"
shift 2
OUTPUT_DIR="/tmp/ai_for_test_charm/output/$DATASET_NAME"
JAVA_DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=${DEBUG_SUSPEND},address=*:5005"

: "${PROD_DIFY_DATASET_API_KEY:?请设置环境变量 PROD_DIFY_DATASET_API_KEY}"

echo "==> 构建 bootJar"
./gradlew bootJar --quiet

echo "==> 处理并上传到 Dify"
java $JAVA_DEBUG_OPTS -jar build/libs/ai_for_test_charm-0.0.1-SNAPSHOT.jar \
  "$SRC_DIR" "$OUTPUT_DIR" \
  --dify.api-endpoint=https://api.dify.ai/v1 \
  --dify.dataset-api-key="$PROD_DIFY_DATASET_API_KEY" \
  "$@"

echo "==> 完成"
