#/bin/bash

echo "****************************************"
echo "* Publishing akka-management locally   *"
echo "****************************************"

sbt clean publishLocal
cd bootstrap-joining-demo/aws-api

echo "****************************************"
echo "* Building aws-api package (app.zip)   *"
echo "****************************************"
sbt clean universal:packageBin

echo "****************************************"
echo "* Uploading app.zip to S3              *"
echo "****************************************"

aws s3 cp target/universal/app.zip s3://$BUCKET/ --acl public-read   
