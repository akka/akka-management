#!/usr/bin/env perl

my $serviceType = $ARGV[0];

if (not defined $serviceType or not length $serviceType) {
  print "Error: The type of services to remove is required.\n";
  print_usage();
  exit(1);
}

my @apps = (
  "nginx-bridge",
  "nginx-container",
  "nginx-container-multinetwork",
  "nginx-host", "redis-container",
  "nginx-container-misconfigured"
);

my @pods = (
  "nginx-pod-bridge",
  "nginx-pod-host",
  "nginx-pod-container",
  "nginx-pod-container-multinetwork",
  "redis-pod-container",
  "nginx-pod-container-misconfigured"
);

my @services;

if ($serviceType eq "app") {
  @services = @apps;
} elsif ($serviceType eq "pod") {
  @services = @pods;
} else {
  print "Error: Unexpected service type [$serviceType] encountered.\n";
  print_usage();
  exit(1);
}

foreach $service (@services) {
  print "Removing $serviceType [$service] ...\n";
  print `dcos marathon $serviceType remove $service --force`;
}

sub print_usage {
  print "\n";
  print "$0 -- Remove DC/OS apps or pods\n";
  print "Usage: $0 <pod | app>\n";
  print "Example: $0 pod\n";
  return;
}
