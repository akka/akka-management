eval $(minikube -p minikube docker-env)
sbt $PROJECT_NAME/docker:publishLocal

docker images | head

kubectl create namespace akka-bootstrap-demo-ns || true
kubectl -n $NAMESPACE apply -f $DEPLOYMENT

for i in {1..10}
do
  echo "Waiting for pods to get ready..."
  kubectl get pods -n $NAMESPACE
  [ `kubectl get pods -n $NAMESPACE | grep Running | wc -l` -eq 3 ] && break
  sleep 4
done

if [ $i -eq 10 ]
then
  echo "Pods did not get ready"
  exit -1
fi

POD=$(kubectl get pods -n $NAMESPACE | grep $APP_NAME | grep Running | head -n1 | awk '{ print $1 }')

for i in {1..15}
do
  echo "Checking for MemberUp logging..."
  kubectl logs $POD -n $NAMESPACE | grep MemberUp || true
  [ `kubectl logs -n $NAMESPACE $POD | grep MemberUp | wc -l` -eq 3 ] && break
  sleep 3
done

echo "Logs"
echo "=============================="
for POD in $(kubectl get pods -n $NAMESPACE | grep $APP_NAME | grep Running | awk '{ print $1 }')
do
  echo "Logging for $POD"
  kubectl logs $POD -n $NAMESPACE
done


if [ $i -eq 15 ]
then
  echo "No 3 MemberUp log events found"
  echo "=============================="

  exit -1
fi

