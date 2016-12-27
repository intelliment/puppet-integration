class profile::app::webserver {
  tag 'app:banking-app'
  class {'apache':
    default_mods  => false,
    mpm_module    => 'prefork',
    default_vhost => true,
  }
  itlm::consumes { 'app::database':
    destination  => [ 'profile::app::database' ],
    service      => [ 'app::database' ],
  }
  itlm::provides { 'app::web':
    source => [ 'HQ_DMZ' , 'Users' ],
    ports  => [ '80/tcp' ],
    # ports  => [ '443/tcp' ],
  }
}
