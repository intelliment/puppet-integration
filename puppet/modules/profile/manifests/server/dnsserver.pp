class profile::server::dnsserver {
  tag 'app:dns'
  include ::dns

  itlm::provides { 'dns::resolv':
    source  => [ 'all-consumers' ],
    ports   => [ '53/udp' ],
  }
}
