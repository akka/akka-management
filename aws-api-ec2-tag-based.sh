#!/usr/bin/env bash

echo Running integration test for aws-api-ec2-tag-based

export BUILD_ID=$TRAVIS_JOB_NUMBER-$TRAVIS_JOB_ID

sbt bootstrap-joining-demo-aws-api-ec2-tag-based/universal:packageBin
# create bucket if it doesn't already exist
aws s3api create-bucket --bucket $BUCKET --region us-east-1
aws s3 cp bootstrap-joining-demo/aws-api/ec2-tag-based/target/universal/app.zip s3://$BUCKET/$BUILD_ID/ --acl public-read
sbt bootstrap-joining-demo-aws-api-ec2-tag-based/it:test
# delete file (save a few cents)
aws s3 rm s3://$BUCKET/$BUILD_ID/app.zip
