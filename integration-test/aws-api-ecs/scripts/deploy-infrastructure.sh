#!/bin/bash

set -euo pipefail

if [ $# -ne 1 ]
  then
    echo "Usage: $0 <create|update>"
    exit 1
fi

DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

case $1 in
  create | update)
    ACTION=$1
    ;;
  *)
    echo "Usage: $0 <create|update>"
   exit 1
    ;;
esac

aws cloudformation $ACTION-stack \
  --region us-east-1 \
  --stack-name ecs-integration-test-app-infrastructure \
  --template-body file://$DIR/../cfn-templates/ecs-integration-test-app-infrastructure.yaml

aws cloudformation wait stack-$ACTION-complete \
  --region us-east-1 \
  --stack-name ecs-integration-test-app-infrastructure
