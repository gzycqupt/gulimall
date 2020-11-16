安装Docker
Docker安装文档：https://docs.docker.com/engine/install/centos/

卸载旧版本

复制代码
 sudo yum remove docker \
                  docker-client \
                  docker-client-latest \
                  docker-common \
                  docker-latest \
                  docker-latest-logrotate \
                  docker-logrotate \
                  docker-engine
复制代码
安装docker需要依赖的包

sudo yum install -y yum-utils
告诉Linux，Docker安装地址

sudo yum-config-manager \
    --add-repo \
    https://download.docker.com/linux/centos/docker-ce.repo
安装Docker引擎，客户端，容器

sudo yum install docker-ce docker-ce-cli containerd.io
 启动

sudo systemctl start docker
查看版本

docker -v
设置开机自启

systemctl enable docker
配置阿里云镜像加速（CnetOS）:https://cr.console.aliyun.com/cn-hangzhou/instances/mirrors

创建文件夹

sudo mkdir -p /etc/docker
配置镜像加速器地址

sudo tee /etc/docker/daemon.json <<-'EOF'
{
  "registry-mirrors": ["https://这里需要登陆阿里云获取.com"]
}
EOF
重启docker的后台线程

sudo systemctl daemon-reload
重启docker的服务

sudo systemctl restart docker
docker安装mysql
下载（5.7版本，其他版本参照docker hub）

 docker pull mysql:5.7
查看docker中镜像

docker images
创建实例并启动

docker run -p 3306:3306 --name mysql03 \
-v /mydata/mysql/log:/var/log/mysql \
-v /mydata/mysql/data:/var/lib/mysql \
-v /mydata/mysql/conf:/etc/mysql \
-e MYSQL_ROOT_PASSWORD=123456 \
-d mysql:5.7


创建mysql配置文件

vi /mydata/mysql/conf/my.cnf
写入配置信息

复制代码
[client]
default-character-set=utf8

[mysql]
default-character-set=utf8

[mysqld]
init_connect='SET collation_connection = utf8_unicode_ci'
init_connect='SET NAMES utf8'
character-set-server=utf8
collation-server=utf8_unicode_ci
skip-character-set-client-handshake
skip-name-resolve

复制代码
重启mysql容器

docker restart mysql
进入mysql容器内部，并查看文件目录（是一个完整的Linux目录）whereis mysql：查看MySQL相关位置

复制代码
[root@localhost ~]# docker exec -it mysql /bin/bash
root@68dd321e9343:/# ls
bin boot dev docker-entrypoint-initdb.d entrypoint.sh etc home lib lib64 media mnt opt proc root run sbin srv sys tmp usr var

root@68dd321e9343:/# whereis mysql
mysql: /usr/bin/mysql /usr/lib/mysql /etc/mysql /usr/share/mysql
root@68dd321e9343:/#

复制代码
docker安装redis
下载镜像(最新)

docker pull redis
 创建目录结构

mkdir -p /mydata/redis/conf
创建配置文件

touch /mydata/redis/conf/redis.conf
安装redis（并挂载配置文件）

docker run -p 6379:6379 --name redis -v /mydata/redis/data:/data \
-v /mydata/redis/conf/redis.conf:/etc/redis/redis.conf \
-d redis redis-server /etc/redis/redis.conf
 连接到docker的redis

docker exec -it redis redis-cli
测试redis（exit：退出）

127.0.0.1:6379> set a b
OK
127.0.0.1:6379> get a
"b"
127.0.0.1:6379> exit
重启redis

docker restart redis
修改redis配置文件（设置持久化）

appendonly yes
 设置容器在docker启动的时候启动
docker update mysql --restart=always
docker update redis --restart=always
 注册中心（下载地址）
https://github.com/alibaba/nacos/releases

（注册中心下载不了，使用ip代理）免费代理网站

https://www.kuaidaili.com/free/
https://www.kuaidaili.com/free/inha/
http://www.66ip.cn/5.html
https://www.xicidaili.com/wt/