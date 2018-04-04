#!/bin/bash

set -euo pipefail

if [ $# -ne 1 ]
  then
    echo "Usage: $0 subnets"
    exit 1
fi

DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

aws cloudformation create-stack \
  --region us-east-1 \
  --stack-name bootstrap-joining-demo-aws-api-ecs-application \
  --template-body file://$DIR/../application-stack.yaml \
  --capabilities CAPABILITY_IAM \
  --parameters ParameterKey=Subnets,ParameterValue=\"$1\"
