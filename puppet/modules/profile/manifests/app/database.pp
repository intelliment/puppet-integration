class profile::app::database {
  tag 'app:banking-app'
  class { '::mysql::server':
    root_password    => 'strongpassword',
    override_options => {
      'mysqld'       => { 'bind-address' => '0.0.0.0' }
    },
  }
  include ::mysql::server::account_security
  mysql_database { 'appdb':
    ensure  => present,
    charset => 'utf8',
  }
  mysql_user { 'app_user@localhost':
    ensure => present,
  }
  mysql_grant { 'app_user@localhost/appdb.*':
    ensure     => present,
    options    => ['GRANT'],
    privileges => ['ALL'],
    table      => 'appdb.*',
    user       => 'app_user@localhost',
  }
  itlm::provides { 'app::database':
    source  => [ 'all-consumers' ],
    ports   => [ '3306/tcp' ],
  }
}
