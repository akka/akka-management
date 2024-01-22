#!/usr/bin/env bash

echo Running integration tests for aws-api-ec2-tag-based
echo You need to run this from the root folder of the akka-management project

export BUILD_ID=$TRAVIS_JOB_NUMBER-$TRAVIS_JOB_ID

# Download things necessary for the various integration tests
# AWS tools (necessary for the aws-api-ec2-tag-based integration test)
curl "https://s3.amazonaws.com/aws-cli/awscli-bundle.zip" -o "awscli-bundle.zip"
unzip awscli-bundle.zip
./awscli-bundle/install -b ~/bin/aws
export PATH=~/bin:$PATH

sbt bootstrap-demo-aws-api-ec2-tag-based/universal:packageBin
# create bucket if it doesn't already exist
aws s3api create-bucket --bucket $BUCKET --region us-east-1
aws s3 cp bootstrap-demo/aws-api-ec2/target/universal/app.zip s3://$BUCKET/$BUILD_ID/ --acl public-read
# run the actual integration test
sbt bootstrap-demo-aws-api-ec2-tag-based/it:test
# delete file (save a few cents)
aws s3 rm s3://$BUCKET/$BUILD_ID/app.zip
