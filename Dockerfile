FROM openjdk:8-jre-alpine
COPY newsriver-beamer-*.jar /home/newsriver-beamer.jar
WORKDIR /home
EXPOSE 31000-32000
ENV PORT 31113
ENTRYPOINT ["java","-Duser.timezone=GMT","-Dfile.encoding=utf-8","-Xms64m","-Xmx1g","-Xss1m","-XX:MaxMetaspaceSize=64m","-XX:+UseConcMarkSweepGC","-XX:+CMSParallelRemarkEnabled","-XX:+UseCMSInitiatingOccupancyOnly","-XX:CMSInitiatingOccupancyFraction=70","-XX:OnOutOfMemoryError='kill -9 %p'","-jar","/home/newsriver-beamer.jar"]
