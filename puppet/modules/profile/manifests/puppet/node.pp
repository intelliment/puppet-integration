class profile::puppet::node {
  pe_ini_setting { 'use_cached_catalog':
    ensure  => present,
    path    => $settings::config,
    section => 'agent',
    setting => 'use_cached_catalog',
    value   => 'false',
  }
  pe_ini_setting { 'pluginsync':
    ensure  => present,
    path    => $settings::config,
    section => 'agent',
    setting => 'pluginsync',
    value   => 'false',
  }
}
