class profile::server::base {
  class { 'ntp':
    servers => [
      'nist-time-server.eoni.com',
      'nist1-lv.ustiming.org',
      'ntp-nist.ldsbc.edu',
    ]
  }
  # Should configure DNS but won't or demo won't work at the moment

  itlm::consumes { 'dns::resolv':
    destination => [ 'profile::server::dnsserver' ],
    service      => [ 'dns::resolv' ],
  }
  itlm::provides { 'server::ssh':
    source  => [ 'Admins' ],
    ports   => [ '22/tcp' ],
  }
}
