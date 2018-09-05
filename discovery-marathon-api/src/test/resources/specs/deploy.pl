#!/usr/bin/env perl

my $serviceType   = $ARGV[0];
my $specsPath = $ARGV[1];

if (not defined $serviceType or not length $serviceType) {
  print "Error: The type of services to deploy is required.\n";
  print_usage();
  exit(1);
}

if (not defined $specsPath or not length $specsPath) {
  print "Error: The path to the JSON specs directory is required.\n";
  print_usage();
  exit(1);
}

if ($serviceType eq "app" or $serviceType eq "pod") {
  foreach $spec (<$specsPath/*>) {
    print "Deploying [$spec] ...\n";
    print `dcos marathon $serviceType add < $spec`;
  }
} else {
  print "Error: Unexpected service type [$serviceType] encountered.\n";
  print_usage();
  exit(1);
}

sub print_usage {
  print "\n";
  print "$0 -- Deploys DC/OS apps or pods\n";
  print "Usage: $0 <pod | app> <path to JSON specs>\n";
  print "Example: $0 pod ./marathon_1.6/mesos/single/pods/\n";
  return;
}
