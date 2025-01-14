#!/bin/bash

# Docker down
sudo docker-compose down

# Docker image prune
sudo docker image prune -a -f

# Docker volume prune
sudo docker volume prune -f

# Git pull
sudo git pull

# Gradle clean and build
sudo ./gradlew clean build

# Docker up
sudo docker-compose up -d

# MySQL 환경 변수로 비밀번호 처리
MYSQL_PWD='bZGPt9zQZ7aAtv+T]6}(*EfDONfO.R' mysql -u dsiotAppServer <<EOF
set foreign_key_checks=0;
SELECT @@FOREIGN_KEY_CHECKS;
EXIT
EOF

# Docker logs
sudo docker-compose logs -f



