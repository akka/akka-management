#/bin/bash

echo Running integration test for aws-api-ec2-tag-based

sbt clean publishLocal
cd bootstrap-joining-demo/aws-api/ec2-tag-based
sbt clean universal:packageBin
aws s3 cp target/universal/app.zip s3://$BUCKET/ --acl public-read
sbt test
