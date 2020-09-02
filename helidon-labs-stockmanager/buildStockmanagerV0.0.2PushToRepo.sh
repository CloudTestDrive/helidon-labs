#!/bin/bash
echo Updating StatusResource version
mv src/main/java/com/oracle/labs/helidon/stockmanager/resources/StatusResource.java .
cat StatusResource.java | sed s/0.0.1/0.0.2/ > StatusResourcev2.java
mv StatusResourcev2.java src/main/java/com/oracle/labs/helidon/stockmanager/resources/StatusResource.java
rm StatusResource.java
. ./repoStockmanagerConfig.sh
mvn package
echo Resetting StatusResource version
mv src/main/java/com/oracle/labs/helidon/stockmanager/resources/StatusResource.java .
cat StatusResource.java | sed s/0.0.2/0.0.1/ > StatusResourcev1.java
mv StatusResourcev1.java src/main/java/com/oracle/labs/helidon/stockmanager/resources/StatusResource.java
rm StatusResource.java
docker build --tag $REPO/stockmanager:latest --tag $REPO/stockmanager:0.0.2  --file Dockerfile .
docker push $REPO/stockmanager:latest
docker push $REPO/stockmanager:0.0.2
