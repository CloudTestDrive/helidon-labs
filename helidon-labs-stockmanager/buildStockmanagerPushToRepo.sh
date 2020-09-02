#!/bin/bash
. ./repoStockmanagerConfig.sh
mvn package
docker build --tag $REPO/stockmanager:latest --tag $REPO/stockmanager:0.0.1  --file Dockerfile .
docker push $REPO/stockmanager:latest
docker push $REPO/stockmanager:0.0.1
