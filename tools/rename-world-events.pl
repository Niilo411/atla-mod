#!/usr/bin/perl
# Renames the two franchise-named world events, identically on every branch:
#   "Sozin's Comet"    -> "Ember Comet"
#   "Day of Black Sun" -> "Day of the Hollow Sun"
# Names only: ids, classes, config keys, messages and docs. The events' mechanics are untouched.
#
#   perl tools/rename-world-events.pl content FILE...   rewrite files in place
#   perl tools/rename-world-events.pl path PATH...      print each path renamed (or stdin)
use strict;
use warnings;

sub transform {
    my ($t) = @_;

    # --- Sozin's Comet -> Ember Comet --------------------------------------------------
    $t =~ s/\bSozin's Comet is\b/The Ember Comet is/g;               # sentence openers
    $t =~ s/\b(under|Draws|draws|during|of|from|with|after|before), Sozin's [Cc]omet/$1, the Ember Comet/g;
    $t =~ s/\b(under|Draws|draws|during|of|from|with|after|before) Sozin's [Cc]omet/$1 the Ember Comet/g;
    $t =~ s/, Sozin's [Cc]omet\b/, the Ember Comet/g;                 # "a blood moon..., Sozin's comet that..."
    $t =~ s/\bSozin's [Cc]omet\b/Ember Comet/g;
    $t =~ s/\bSOZIN'S COMET\b/EMBER COMET/g;
    $t =~ s/SOZINS_COMET/EMBER_COMET/g;
    $t =~ s/SozinsComet/EmberComet/g;
    $t =~ s/sozinsComet/emberComet/g;
    $t =~ s/sozins_comet/ember_comet/g;

    # --- Day of Black Sun -> Day of the Hollow Sun -------------------------------------
    # "Day of" + Black Sun gains "the", even across a comment line break.
    $t =~ s/\bDAY OF(\s+(?:\*\s+)?)BLACK SUN/DAY OF THE$1HOLLOW SUN/g;
    $t =~ s/\b([Dd]ay of)(\s+(?:\*\s+)?)Black Sun/$1 the$2Hollow Sun/g;
    $t =~ s/\b([Dd]ay of)(\s+(?:\*\s+)?)black sun/$1 the$2hollow sun/g;
    $t =~ s/\bBLACK SUN\b/HOLLOW SUN/g;
    $t =~ s/\bBlack Sun\b/Hollow Sun/g;
    $t =~ s/\bblack sun\b/hollow sun/g;
    $t =~ s/dayOfBlackSun/dayOfTheHollowSun/g;
    $t =~ s/BLACK_SUN/HOLLOW_SUN/g;
    $t =~ s/BlackSun/HollowSun/g;
    $t =~ s/blackSun/hollowSun/g;
    $t =~ s/black_sun/hollow_sun/g;
    return $t;
}

my $mode = shift @ARGV // die "usage: $0 content|path ARGS...\n";
if ($mode eq 'path') {
    my @paths = @ARGV ? @ARGV : map { chomp; $_ } <STDIN>;
    print transform($_), "\n" for @paths;
    exit 0;
}
die "unknown mode $mode\n" unless $mode eq 'content';
for my $file (@ARGV) {
    local $/;
    open my $in, '<:raw', $file or die "$file: $!\n";
    my $before = <$in>;
    close $in;
    my $after = transform($before);
    next if $after eq $before;
    open my $out, '>:raw', $file or die "$file: $!\n";
    print $out $after;
    close $out;
}
