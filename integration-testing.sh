#/bin/bash
rm -rf target
rm -rf bootstrap-joining-demo/aws-api/target
rm -rf project/target
rm -rf bootstrap-joining-demo/aws-api/project/target

sbt publishLocal
cd bootstrap-joining-demo/aws-api
sbt universal:packageBin

aws s3 cp target/universal/app.zip s3://$BUCKET/ --acl public-read
   
