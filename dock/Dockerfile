# FROM alpine
# FROM openjdk:8-jre-alpine
# FROM openjdk:8-jre-slim
FROM ubuntu

EXPOSE 8080 8558 2552

COPY integration-test-kubernetes-api-assembly-1.3.3.7 .

# RUN apk add libc6-compat

# RUN ldd /integration-test-kubernetes-api-assembly-1.3.3.7

ENTRYPOINT /integration-test-kubernetes-api-assembly-1.3.3.7

# ENTRYPOINT cat /etc/hosts
