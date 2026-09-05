#!/usr/bin/perl
# shim-apply.pl <table.tsv> <src.scala>  -> adapted source on stdout
#
# The NAME/SHIM half of a differential suite (CLAUDE.md §3.5): apply an ENUMERATED table to a
# reference hand port's test file so it names the EMITTED api, and apply it to CODE ONLY —
# comments and string literals are masked first, so a rename can never rewrite prose or an
# expected value and turn a divergence into a pass.
#
# Table rows are TSV, `kind<TAB>from<TAB>to`, `#` comments and blank lines ignored:
#   type    From  To    whole-word identifier rename (a type or an object name)
#   member  from  to    a SELECTION — `.from` only, so the rename is per RECEIVER EXPRESSION
#   text    from  to    literal substring, for a shim that changes the call shape
# Every row that never fires is reported on stderr, because a table row nothing matches is a
# claim about the two APIs that has stopped being true.

use strict;
use warnings;

my ($table, $src) = @ARGV;
die "usage: shim-apply.pl <table.tsv> <src.scala>\n" unless defined $table && defined $src;

my @rules;
open(my $t, '<', $table) or die "cannot read $table: $!";
while (my $line = <$t>) {
  chomp $line;
  next if $line =~ /^\s*(#|$)/;
  my ($kind, $from, $to) = split(/\t/, $line, 3);
  next unless defined $to;
  push @rules, [$kind, $from, $to];
}
close($t);

my $text = do { open(my $f, '<', $src) or die "cannot read $src: $!"; local $/; <$f> };

# --- split into (code | masked) segments -----------------------------------------------------
# Anything that is not code is emitted VERBATIM and never offered to a rule.
my @segs;             # [is_code, text]
my $i = 0;
my $n = length($text);
my $code = '';
sub flush_code { push @segs, [1, $code]; $code = ''; }

while ($i < $n) {
  my $two = substr($text, $i, 2);
  my $one = substr($text, $i, 1);
  if ($two eq '//') {
    my $j = index($text, "\n", $i);
    $j = $n if $j < 0;
    flush_code(); push @segs, [0, substr($text, $i, $j - $i)]; $i = $j;
  } elsif ($two eq '/*') {
    # Scala block comments NEST (CLAUDE.md §4.58)
    my $depth = 1; my $j = $i + 2;
    while ($j < $n && $depth > 0) {
      my $p = substr($text, $j, 2);
      if    ($p eq '/*') { $depth++; $j += 2 }
      elsif ($p eq '*/') { $depth--; $j += 2 }
      else               { $j++ }
    }
    flush_code(); push @segs, [0, substr($text, $i, $j - $i)]; $i = $j;
  } elsif (substr($text, $i, 3) eq '"""') {
    my $j = index($text, '"""', $i + 3);
    $j = $j < 0 ? $n : $j + 3;
    # a triple-quoted string may end in more than three quotes
    $j++ while $j < $n && substr($text, $j, 1) eq '"';
    flush_code(); push @segs, [0, substr($text, $i, $j - $i)]; $i = $j;
  } elsif ($one eq '"') {
    my $j = $i + 1;
    while ($j < $n) {
      my $c = substr($text, $j, 1);
      last if $c eq '"';
      $j += ($c eq "\\") ? 2 : 1;
    }
    $j = $j < $n ? $j + 1 : $n;
    flush_code(); push @segs, [0, substr($text, $i, $j - $i)]; $i = $j;
  } elsif ($one eq "'" && (substr($text, $i, 3) =~ /^'[^\\']'/ || substr($text, $i, 2) eq "'\\")) {
    # a character literal; anything else starting with `'` is code (a quoted expression)
    my $j = $i + 1;
    $j += (substr($text, $j, 1) eq "\\") ? 2 : 1;
    $j++ if $j < $n && substr($text, $j, 1) eq "'";
    flush_code(); push @segs, [0, substr($text, $i, $j - $i)]; $i = $j;
  } else {
    $code .= $one; $i++;
  }
}
flush_code();

# --- apply the table to the code segments only -------------------------------------------------
my %fired;
for my $s (@segs) {
  next unless $s->[0];
  for my $r (@rules) {
    my ($kind, $from, $to) = @$r;
    my $hits = 0;
    if ($kind eq 'type') {
      my $q = quotemeta($from);
      $hits = ($s->[1] =~ s/\b$q\b/$to/g);
    } elsif ($kind eq 'member') {
      my $q = quotemeta($from);
      $hits = ($s->[1] =~ s/(?<=\.)$q\b/$to/g);
    } elsif ($kind eq 'text') {
      my $q = quotemeta($from);
      $hits = ($s->[1] =~ s/$q/$to/g);
    } else {
      die "unknown shim kind '$kind' in $table\n";
    }
    $fired{"$kind\t$from"} += ($hits || 0);
  }
}

print join('', map { $_->[1] } @segs);

# one tally line per rule per file; the LANE sums them, so a row that fires nowhere in the whole
# tree is reported once rather than once per file.
for my $r (@rules) {
  my $k = "$r->[0]\t$r->[1]";
  print STDERR "shim-fired\t$k\t" . ($fired{$k} || 0) . "\n";
}
