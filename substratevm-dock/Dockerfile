FROM ubuntu

COPY graal graal

RUN ln -s /graal/bin/java /usr/bin/java

RUN ls -alFh graal/bin/java

RUN ldd graal/bin/java

RUN graal/bin/java -version

RUN ls -alFh /usr/bin/java

RUN /usr/bin/java -version

ENTRYPOINT /usr/bin/java -version
