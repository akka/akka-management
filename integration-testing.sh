#/bin/bash

sbt clean publishLocal
cd bootstrap-joining-demo/aws-api
sbt clean universal:packageBin

aws s3 cp target/universal/app.zip s3://$BUCKET/ --acl public-read   
