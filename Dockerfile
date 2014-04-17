FROM debian:wheezy

RUN apt-get update

# install ruby 1.9.1 before adding to sources to avoid unstable packages.
# Should be able to avoid this when we reinstate pinning.
RUN apt-get install -y -qq --no-install-recommends build-essential ruby1.9.1 ruby-dev
RUN gem install iruby --no-ri --no-rdoc

ADD apt/backports.list /etc/apt/sources.list.d/
ADD apt/sid.list /etc/apt/sources.list.d/
RUN apt-get update

RUN apt-get install -y -qq --no-install-recommends python libzmq3-dbg libzmq3-dev libzmq3

RUN apt-get install -y -qq --no-install-recommends nginx
# TODO: when beaker starts get this error:
# nginx-stderr>nginx: [alert] could not open error log file: open() "/var/log/nginx/error.log" failed (13: Permission denied)
# Is there a more elegant way to allow access to nginx?
RUN ln -s /usr/sbin/nginx /usr/bin/nginx

RUN apt-get install -y -y -qq --no-install-recommends nodejs npm
RUN ln -s /usr/bin/nodejs /usr/bin/node

RUN apt-get install -y -qq --no-install-recommends ipython

RUN apt-get install -y git -qq --no-install-recommends

RUN apt-get install -y -qq --no-install-recommends julia
RUN julia -e "Pkg.add(\"IJulia\")"
RUN julia -e "Pkg.add(\"Gadfly\")"

RUN apt-get install -y -qq --no-install-recommends r-base r-base-dev
RUN R -e "install.packages('Rserve', repos='http://www.rforge.net/')"
RUN R -e "install.packages('ggplot2', repos='http://cran.mirrors.hoobly.com')"

ADD vagrant/install_oracle_jdk.sh /root/
RUN /root/install_oracle_jdk.sh

ADD vagrant/install_gradle.sh /root/
RUN /root/install_gradle.sh

RUN apt-get install -y -qq --no-install-recommends adduser
RUN adduser beaker --disabled-password --gecos ""
ADD shared /home/beaker/shared
ADD core /home/beaker/core
ADD plugin /home/beaker/plugin
RUN chown -R beaker.beaker /home/beaker

RUN su - beaker -c 'gradle -p /home/beaker/core/config/builds/dev/ build'

EXPOSE 8801
USER beaker
WORKDIR /home/beaker
ENV HOME /home/beaker
ENTRYPOINT gradle -p /home/beaker/core/config/builds/dev/ run


