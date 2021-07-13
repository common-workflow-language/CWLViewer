FROM maven:3.6-jdk-8-alpine
MAINTAINER Stian Soiland-Reyes <stain@apache.org>

# Build-time metadata as defined at http://label-schema.org
ARG BUILD_DATE
ARG VCS_REF
ARG VERSION
LABEL org.label-schema.build-date=$BUILD_DATE \
  org.label-schema.name="CWL Viewer" \
  org.label-schema.description="Viewer of Common Workflow Language" \
  org.label-schema.url="https://view.commonwl.org/" \
  org.label-schema.vcs-ref=$VCS_REF \
  org.label-schema.vcs-url="https://github.com/common-workflow-language/cwlviewer" \
  org.label-schema.vendor="Common Workflow Language project" \
  org.label-schema.version=$VERSION \
  org.label-schema.schema-version="1.0"


RUN apk add --update \
  graphviz \
  ttf-freefont \
  py3-pip \
  gcc \
  python3-dev \
  libc-dev \
  nodejs \
  libc-dev \
  linux-headers \
  libxml2-dev \
  libxml2-utils \
  libxslt-dev \
  && rm -rf /var/cache/apk/*

#wheel needed by ruamel.yaml for some reason
RUN pip3 install wheel
RUN pip3 install cwltool html5lib

RUN cwltool --version

RUN mkdir /usr/share/maven/ref/repository

RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

# Top-level files (ignoring .git etc)
ADD pom.xml LICENSE.md NOTICE.md README.md /usr/src/app/

# add src/ (which often change)
ADD src /usr/src/app/src
# Skip tests while building as that requires a local mongodb
RUN mvn clean package -DskipTests && cp target/cwlviewer-*.jar /usr/lib/cwlviewer.jar && rm -rf target

# NOTE: ~/.m2/repository is a VOLUME and so will be deleted anyway
# This also means that every docker build downloads all of it..

WORKDIR /tmp

EXPOSE 8080
ENV LC_ALL C.UTF-8
CMD ["/usr/bin/java", "-jar", "/usr/lib/cwlviewer.jar"]
