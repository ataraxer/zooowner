apt-get update -y > /dev/null
apt-get install -y openjdk-7-jre-headless > /dev/null

wget http://apache-mirror.rbc.ru/pub/apache/zookeeper/zookeeper-3.4.6/zookeeper-3.4.6.tar.gz -O zk.tgz
tar -xzf zk.tgz

mv zookeeper-3.4.6 zookeeper
cd zookeeper

cp conf/zoo_sample.cfg conf/zoo.cfg

bin/zkServer.sh start conf/zoo.cfg

exit
