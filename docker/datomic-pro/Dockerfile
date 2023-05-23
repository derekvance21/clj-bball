# syntax=docker/dockerfile:1

FROM alpine AS download

RUN apk add --no-cache curl unzip
RUN curl https://datomic-pro-downloads.s3.amazonaws.com/1.0.6726/datomic-pro-1.0.6726.zip -O
RUN unzip datomic-pro-1.0.6726.zip

FROM maven:3.9.2-eclipse-temurin-17

COPY --from=download /datomic-pro-1.0.6726/ /datomic-pro-1.0.6726/

WORKDIR /datomic-pro-1.0.6726

CMD [ "/bin/bash" ]
