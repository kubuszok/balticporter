#!/usr/bin/env perl
# Count a consumer's tests from what each SUITE reported, per module.
#
#   scripts/test-census.pl <build tree> <log> <out.tsv>
#
# sbt's per-task "Passed: Total N" summaries are not a measurement: with projects testing in
# parallel the same run on the same commit summed to anything from 3029 to 3188 tests, while every
# suite's own "Test run <suite> finished: F failed, I ignored, N total" line agreed. So the count is
# the sum of the suite lines, each suite attributed to the module whose test-classes hold it; a
# module with test classes and no suite that ran is listed as such (cached, skipped or empty), never
# silently absent. Prints one summary line; writes module, suites, tests, failed, ignored to out.tsv.
use strict;
use warnings;
use File::Find;

my ($tree, $log, $out) = @ARGV;
die "usage: $0 <build tree> <log> <out.tsv>\n" unless defined $out;

# suite class -> the modules (target/out/<axis>/scala-<v>/<module>/test-classes) that compiled it
my (%modulesOf, %allModules);
for my $dir (glob("$tree/target/out/*/scala-*/*/test-classes")) {
  my ($axis, $module) = $dir =~ m{/target/out/([^/]+)/scala-[^/]+/([^/]+)/test-classes$} or next;
  my $key = $axis eq 'jvm' ? $module : "$module($axis)";
  $allModules{$key} = 1;
  find({ no_chdir => 1, wanted => sub {
    return unless /\.class$/ && !/\$/;
    (my $fqn = substr($_, length($dir) + 1)) =~ s{\.class$}{};
    $fqn =~ s{/}{.}g;
    push @{ $modulesOf{$fqn} }, $key;
  } }, $dir);
}

# each suite run, in log order
my %runs;
my $sbt = 0;
open(my $fh, '<', $log) or die "cannot read $log: $!\n";
while (my $line = <$fh>) {
  $line =~ s/\e\[[0-9;]*m//g;
  if ($line =~ /^Test run (\S+) finished: (\d+) failed, (\d+) ignored, (\d+) total/) {
    push @{ $runs{$1} }, [$2, $3, $4];
  } elsif ($line =~ /Total (\d+), Failed \d+, Errors \d+/) {
    $sbt += $1;
  }
}
close $fh;

# a suite that ran k times is attributed once to each of the modules holding it, in sorted order
my %per;
for my $suite (sort keys %runs) {
  my @mods = sort @{ $modulesOf{$suite} // ['(no test-classes found)'] };
  my $i = 0;
  for my $r (@{ $runs{$suite} }) {
    my $m = $mods[$i < @mods ? $i : $#mods];
    $i++;
    my $p = $per{$m} //= [0, 0, 0, 0];
    $p->[0]++; $p->[1] += $r->[2]; $p->[2] += $r->[0]; $p->[3] += $r->[1];
  }
}

open(my $oh, '>', $out) or die "cannot write $out: $!\n";
print $oh "module\tsuites\ttests\tfailed\tignored\n";
my ($s, $t, $f, $ig) = (0, 0, 0, 0);
for my $m (sort keys %per) {
  my $p = $per{$m};
  print $oh join("\t", $m, @$p), "\n";
  $s += $p->[0]; $t += $p->[1]; $f += $p->[2]; $ig += $p->[3];
}
my @idle = grep { !exists $per{$_} } sort keys %allModules;
print $oh join("\t", $_, 0, 0, 0, 0), "\n" for @idle;
close $oh;

if ($s == 0) {
  print $sbt ? "$sbt tests by sbt's own summaries (no per-suite lines to count)" : "no test summary";
} else {
  printf "%d tests in %d suites over %d modules, %d failed, %d ignored", $t, $s, scalar(keys %per), $f, $ig;
  printf "; %d modules ran no suite (cached, skipped or empty): %s", scalar(@idle), join(',', @idle) if @idle;
}
print "\n";
