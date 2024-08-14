FROM clojure:latest
RUN apt-get update

RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

RUN chmod -R 777 /root/

RUN sed -i 's/deb.debian.org/mirrors.tuna.tsinghua.edu.cn/' /etc/apt/sources.list.d/debian.sources
RUN sed -i 's/security.debian.org/mirrors.tuna.tsinghua.edu.cn/' /etc/apt/sources.list.d/debian.sources
RUN sed -i 's/security-cdn.debian.org/mirrors.tuna.tsinghua.edu.cn/' /etc/apt/sources.list.d/debian.sources
RUN apt clean
RUN apt update 

RUN apt-get update && apt-get install -y \
      npm \
      nodejs;
COPY package.json /usr/src/app/
COPY package-lock.json /usr/src/app/
RUN npm config set registry https://registry.npmmirror.com/
RUN npm ci

# Install clojure dependencies
COPY project.clj /usr/src/app/
RUN lein deps
