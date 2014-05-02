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
Create the pid dir  (will be automated in initscript version)
```shell
mkdir -p /var/run/hadoop/hdfs /var/run/hadoop/yarn /var/run/hadoop/mapred
chown hdfs:hadoop /var/run/hadoop/hdfs
chown yarn:hadoop /var/run/hadoop/yarn
chown mapred:hadoop /var/run/hadoop/mapred
```
Format the namenode
```shell
su hdfs -c 'hdfs namenode -format'
```
Start daemond (will be automated in initscript version)
```shell
su hdfs -c 'hadoop-daemon.sh start namenode'
hadoop-daemon.sh start datanode
su yarn -c 'yarn-daemon.sh start resourcemanager'
su yarn -c 'yarn-daemon.sh start nodemanager'
su yarn -c 'mr-jobhistory-daemon.sh start hostoryserver'
```
