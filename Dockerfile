FROM openjdk:8-jdk-buster

RUN apt-get update && apt-get upgrade -y

RUN curl -sL https://deb.nodesource.com/setup_14.x | bash - && \
  apt-get install -y nodejs

RUN echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
  echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list && \
  curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key add && \
  apt-get update

RUN apt-get install -y \
    unzip \
    python-dev \
    python-pip \
    libblas-dev \
    liblapack-dev \
    gfortran \
    python-numpy \
    python-pandas && \
  apt-get autoremove && \
  apt-get clean

# Workaround because of bug in sbt from Debian.
# See https://github.com/sbt/sbt/issues/6614
RUN wget https://scala.jfrog.io/artifactory/debian/sbt-1.6.2.deb && \
    apt install ./sbt-1.6.2.deb

WORKDIR /opt

COPY package.json ./
COPY requirements.txt ./

RUN pip install --upgrade setuptools && \
  pip install -r requirements.txt

RUN npm install
