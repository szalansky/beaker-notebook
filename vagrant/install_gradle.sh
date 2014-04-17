#!/bin/bash

wget http://services.gradle.org/distributions/gradle-1.11-bin.zip -O /root/gradle-1.11-bin.zip
unzip -q -d /usr/lib/ /root/gradle-1.11-bin.zip
rm /root/gradle-1.11-bin.zip
chown -R root.root /usr/lib/gradle-1.11
update-alternatives --install /usr/bin/gradle gradle /usr/lib/gradle-1.11/bin/gradle 1
