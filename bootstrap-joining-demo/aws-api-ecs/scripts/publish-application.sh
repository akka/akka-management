#!/bin/bash

set -euo pipefail

if [ $# -ne 0 ]
  then
    echo "Usage: $0"
    exit 1
fi

DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

(cd $DIR/.. && sbt docker:publishLocal)

eval $(aws ecr get-login --region us-east-1 --no-include-email)

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --output text --query 'Account')

docker tag \
  "bootstrap-joining-demo-aws-api-ecs:1.0" \
  "$AWS_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/bootstrap-joining-demo-aws-api-ecs:1.0"

docker push \
  "$AWS_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/bootstrap-joining-demo-aws-api-ecs:1.0"
