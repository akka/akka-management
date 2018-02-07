#!/usr/bin/env bash

echo Running integration test for aws-api-ec2-tag-based

sbt clean publishLocal
cd bootstrap-joining-demo/aws-api/ec2-tag-based
sbt clean universal:packageBin
# create bucket if it doesn't already exist
aws s3api create-bucket --bucket $BUCKET --region us-east-1
aws s3 cp target/universal/app.zip s3://$BUCKET/ --acl public-read
sbt test
# delete file (save a few cents)
aws s3 rm s3://$BUCKET/app.zip