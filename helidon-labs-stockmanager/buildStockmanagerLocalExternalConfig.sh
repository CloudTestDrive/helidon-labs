#!/bin/bash
mvn package
docker build --tag stockmanager --file Dockerfile .
