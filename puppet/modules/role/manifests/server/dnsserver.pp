class role::server::dnsserver {
  include profile::server::base
  include profile::puppet::node
  include profile::server::dnsserver
}
