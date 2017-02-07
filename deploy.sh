#!/bin/zsh

# tomcat7 + java7
mvn clean ; mvn install ; scp target/xbee-0.0.1-SNAPSHOT.war root@localhost:/usr/local/apache-tomcat-7.0/webapps/xbee.war

# si besoin, une fois tomcat démarré :
# ssh root@localhost pkill socat
