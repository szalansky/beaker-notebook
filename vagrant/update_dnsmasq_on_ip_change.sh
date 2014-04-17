#!/bin/bash

r=${reason^^}

if [ "$r" = "REBOOT" ] || [ "$r" = "BOUND" ] || [ "$r" = "RENEW" ] && [ "$old_ip_address" != "$new_ip_address" ]; then
   echo "address=/dev/$new_ip_address" > /etc/dnsmasq.d/dev
   service dnsmasq restart
fi
