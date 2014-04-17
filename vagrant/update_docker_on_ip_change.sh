#!/bin/bash

r=${reason^^}

if [ "$r" = "REBOOT" ] || [ "$r" = "BOUND" ] || [ "$r" = "RENEW" ] && [ "$old_ip_address" != "$new_ip_address" ]; then
  echo "DOCKER_OPTS='-H unix:///var/run/docker.sock -H tcp://0.0.0.0:4243 -dns $new_ip_address'" > /etc/default/docker
  service docker restart
fi
