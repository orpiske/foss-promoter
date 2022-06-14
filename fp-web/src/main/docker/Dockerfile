# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
#  this work for additional information regarding copyright ownership.
#  The ASF licenses this file to You under the Apache License, Version 2.0
#  (the "License"); you may not use this file except in compliance with
#  the License.  You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
FROM fedora:36 as foss-web
LABEL maintainer="orpiske@apache.org"

ENV DEPLOYMENT_DIR /deployments
ENV DATA_DIR /data
VOLUME ${DATA_DIR}

RUN dnf install -y java-11-openjdk-headless tree && dnf clean all
ENV JAVA_HOME /etc/alternatives/jre

COPY target/fp-*with-dependencies.jar ${DEPLOYMENT_DIR}/web.jar
COPY src/main/scripts/run.sh ${DEPLOYMENT_DIR}/run.sh

RUN mkdir -p ${DATA_DIR} && \
    chmod +x ${DEPLOYMENT_DIR}/*.sh
EXPOSE 8080

HEALTHCHECK --interval=5m --timeout=3s \
  CMD curl -f http://localhost:8080/api/hello || exit 1

WORKDIR ${DEPLOYMENT_DIR}
CMD [ "sh", "-c", "${DEPLOYMENT_DIR}/run.sh" ]
