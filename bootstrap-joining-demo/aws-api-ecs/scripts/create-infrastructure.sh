#!/bin/bash

set -euo pipefail

if [ $# -ne 0 ]
  then
    echo "Usage: $0"
    exit 1
fi

DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

aws cloudformation create-stack \
  --region us-east-1 \
  --stack-name bootstrap-joining-demo-aws-api-ecs-infrastructure \
  --template-body file://$DIR/../infrastructure-stack.yaml
