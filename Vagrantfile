# -*- mode: ruby -*-
# vi: set ft=ruby :

VAGRANTFILE_API_VERSION = "2"

Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|
  config.vm.box     = "debian7.4"
  config.vm.box_url = "http://opscode-vm-bento.s3.amazonaws.com/vagrant/virtualbox/opscode_debian-7.4_chef-provisionerless.box"

  config.vm.provider :vmware_fusion do |v, override|
    override.vm.box_url = "http://opscode-vm-bento.s3.amazonaws.com/vagrant/vmware/opscode_debian-7.4_chef-provisionerless.box"
  end

  config.vm.network :forwarded_port, guest: 4243, host: 4243, auto_correct: true # docker
  config.vm.network :forwarded_port, guest: 8801, host: 8801, auto_correct: true # beaker

  config.vm.provision :docker do |d|
    d.pull_images "debian:wheezy"
  end

  config.vm.provision :shell, path: "vagrant/provision.sh"
end
