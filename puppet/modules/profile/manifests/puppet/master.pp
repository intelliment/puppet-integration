class profile::puppet::master {
  class { '::mysql::server':
    root_password    => 'strongpassword',
    override_options => {
      'mysqld'       => { 'max_connections' => '1024' }
    },
  }
  include ::mysql::server::account_security
  mysql_database { 'lvm':
    ensure  => present,
    charset => 'utf8',
  }
  mysql_user { 'lvm_user@localhost':
    ensure => present,
  }
  mysql_grant { 'lvm_user@localhost/lvm.*':
    ensure     => present,
    options    => ['GRANT'],
    privileges => ['ALL'],
    table      => 'lvm.*',
    user       => 'lvm_user@localhost',
  }
  include multi_node
}
