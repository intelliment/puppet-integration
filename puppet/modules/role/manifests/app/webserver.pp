class role::app::webserver {
  include profile::server::base
  include profile::puppet::node
  include profile::app::webserver
}
