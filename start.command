#!/bin/zsh
set -e
cd "${0:A:h}"
echo "Đang khởi động Job Application Tracker..."
(sleep 3; open "http://localhost:8080") &
exec mvn spring-boot:run
