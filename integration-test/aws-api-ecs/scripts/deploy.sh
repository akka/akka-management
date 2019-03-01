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

VPC_ID=$(
  aws ec2 describe-vpcs \
    --region us-east-1 \
    --filters \
      Name=isDefault,Values=true \
    --output text \
    --query \
      "Vpcs[0].VpcId"
)
SUBNETS=$(
  aws ec2 describe-subnets \
    --region us-east-1 \
    --filter \
      Name=vpcId,Values=$VPC_ID \
      Name=defaultForAz,Values=true \
    --output text \
    --query \
      "Subnets[].SubnetId | join(',', @)"
)

aws cloudformation $ACTION-stack \
  --region us-east-1 \
  --stack-name ecs-integration-test-app \
  --template-body file://$DIR/../cfn-templates/ecs-integration-test-app.yaml \
  --capabilities CAPABILITY_IAM \
  --parameters \
    ParameterKey=Subnets,ParameterValue=\"$SUBNETS\"

aws cloudformation wait stack-$ACTION-complete \
  --region us-east-1 \
  --stack-name ecs-integration-test-app
