#!/bin/bash
set -euo pipefail

SRC_DIR="${1:?Usage: $0 <源目录路径> <数据集名称>}"
DATASET_NAME="${2:?Usage: $0 <源目录路径> <数据集名称>}"
OUTPUT_DIR="/tmp/ai_for_test_charm/output/$DATASET_NAME"

: "${DIFY_DATASET_API_KEY:?请设置环境变量 DIFY_DATASET_API_KEY}"

echo "==> 清理输出目录 $OUTPUT_DIR"
rm -rf "$OUTPUT_DIR"

echo "==> 构建 bootJar"
./gradlew bootJar --quiet

echo "==> 处理并上传到 Dify"
java -jar build/libs/ai_for_test_charm-0.0.1-SNAPSHOT.jar \
  "$SRC_DIR" "$OUTPUT_DIR" \
  --dify.api-endpoint=https://api.dify.ai/v1 \
  --dify.dataset-api-key="$DIFY_DATASET_API_KEY"

echo "==> 完成"
