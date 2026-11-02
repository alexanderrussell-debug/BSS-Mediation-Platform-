#!/usr/bin/perl
# smsc_pull.pl - pulls SMSC files for SMSC_SG (cron: */10 * * * *)
# The SMSC only supports FTP *active* mode which the Java collector can't do through the firewall.
# Written 2010 for the old Comverse mediation, still in use.
use strict;
use Net::FTP;

my $host = "10.65.20.14";
my $user = "smscdr";
my $pass = "smsc2010";
my $remote = "/var/cdr/export";
my $local = "/opt/ums/data/input/SMSC_SG";

my $ftp = Net::FTP->new($host, Passive => 0) or die "connect: $@";
$ftp->login($user, $pass) or die "login failed";
$ftp->cwd($remote);
foreach my $f ($ftp->ls("SMSC_*.txt")) {
    $ftp->get($f, "$local/$f.part") or next;
    rename("$local/$f.part", "$local/$f");
    $ftp->delete($f);
}
$ftp->quit;
# clean up archive older than 30 days
system("find /opt/ums/data/archive -mtime +30 -exec rm -f {} \;");
