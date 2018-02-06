#/bin/bash

echo "****************************************"
echo "* Publishing akka-management locally   *"
echo "****************************************"

sbt clean publishLocal
cd bootstrap-joining-demo/aws-api/ec2-tag-based

echo "**********************************************"
echo "* Building ec2-tag-based package (app.zip)   *"
echo "**********************************************"
sbt clean universal:packageBin

echo "****************************************"
echo "* Uploading app.zip to S3              *"
echo "****************************************"

aws s3 cp target/universal/app.zip s3://$BUCKET/ --acl public-read  

echo "***************************************************************"
echo " Running integration tests for ec2-tag-based demo             *"
echo "***************************************************************"

sbt test
