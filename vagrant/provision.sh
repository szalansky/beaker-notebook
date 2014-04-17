#!/bin/bash

sudo apt-get update

if [ ! -f /usr/bin/vim ]; then
  sudo apt-get install -qq -y --no-install-recommends vim
fi

if [ ! -f /usr/sbin/dnsmasq ]; then
  sudo apt-get install -qq -y --no-install-recommends dnsmasq
fi

grep -q '^nameserver 127.0.0.1$' /etc/resolv.conf \
  || sed -i '1i nameserver 127.0.0.1' /etc/resolv.conf

sed -i "/^#prepend domain-name-servers 127.0.0.1;/ s/#//" /etc/dhcp/dhclient.conf

cp /vagrant/vagrant/update_*_on_ip_change.sh /etc/dhcp/dhclient-exit-hooks.d/

(
  export reason="REBOOT";
  export new_ip_address=$(ip -o -4 addr show eth0 | grep -Po '\d+\.\d+\.\d+\.\d+(?=\/)');

  /etc/dhcp/dhclient-exit-hooks.d/update_docker_on_ip_change.sh;
  /etc/dhcp/dhclient-exit-hooks.d/update_dnsmasq_on_ip_change.sh;
)
