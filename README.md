Hadoop Autodeployment
=====================

Hadoop Deb
----------
Only tested on ubuntu 14

Create deb package via make
```shell
cd hadoop-deb
make
```
Install deb and java
```shell
apt-get install openjdk-7-jdk jsvc
dpkg --install hadoop.deb
```
Move core-site.xml, hdfs-site.xml, yarn-site.xml, mapred-site.xml from /etc/hadoop/conf.example to /etc/hadoop and edit the 'TODO' tag.
Format the namenode
```shell
su hdfs -c 'hdfs namenode -format'
```
Start daemond
```shell
/etc/init.d/hadoop start
```
