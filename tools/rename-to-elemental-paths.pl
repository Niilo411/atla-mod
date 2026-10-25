#!/usr/bin/perl
# The "Atla mod" -> "Elemental Paths" rename, as one mapping applied identically to every
# branch of every build (NeoForge 1.21.1, Forge 1.20.1, Fabric 1.21.11 and 26.3).
#
#   perl tools/rename-to-elemental-paths.pl content FILE...   rewrite files in place
#   perl tools/rename-to-elemental-paths.pl path PATH...      print each path renamed
#
# A PURE RENAME: only names change. Every rule is word-aware, and the vanilla names that
# merely CONTAIN one of the words (TextureAtlas, AvatarRenderer, BendingTrunkPlacer...) are
# shielded before anything runs. Re-running on an already renamed tree changes nothing.
use strict;
use warnings;

my $PACKAGE = 'io.github.niilo411.elementalpaths';
my $MODID = 'elemental_paths';

# Vanilla (and other third-party) names that contain one of the words being renamed.
# They are swapped for placeholders first and put back last, so no rule can reach them.
my @PROTECTED = (
    qr/\bAvatar(?:RenderState|Renderer|likeEntity)\b/,       # vanilla player rendering (1.21.9+)
    qr/\bClientAvatar(?:Entity|State)\b/,
    qr/\bavatar(?:RenderState|Renderer)\b/, qr/\bclientAvatarState\b/,
    qr/\bcreateAvatarRenderers\b/, qr/\bgetAvatarRenderer\b/, qr/\bprintForAvatar\b/,
    qr/\bnet\.minecraft\.world\.entity\.Avatar\b/,
    qr/\bBendingTrunkPlacer\b/, qr/\bbending_trunk_placer\b/, qr/\bbendLength\b/,
    qr/\bbend_length\b/, qr/\bpitchBend\b/,
    qr/\brule bent\b/,                                       # plain English, not channeling
    # The repositories and folders keep their names: renaming those is a GitHub/manual step.
    qr{github\.com/Niilo411/atla-mod[\w-]*},
    qr{[Aa]tla [Mm]od(?= (?:Fabric|Forge)\b)(?: (?:Fabric|Forge))?},
    qr{`atla Mod`},
);

my $ELEMENT = qr/(?i:fire|water|air|earth|lightning|ice|sound|metal|combustion|blood|lava|gravity)/;

# "bend" in the casing of the word it replaces.
sub channel_word {
    my ($bend, $suffix) = @_;      # $bend is "bend"/"Bend"/"BEND", $suffix "ing", "er", "ers"...
    my $out = "channel" . ($suffix // '');
    return uc $out if $bend eq 'BEND';
    return ucfirst $out if $bend eq 'Bend';
    return $out;
}

sub transform {
    my ($text, $mode) = @_;
    my @saved;
    for my $re (@PROTECTED) {
        $text =~ s/($re)/push @saved, $1; "\x00" . $#saved . "\x00"/ge;
    }

    # --- the mod itself ---------------------------------------------------------------
    $text =~ s{\bcom\.minecraft\.atlamod\b}{$PACKAGE}g;
    $text =~ s{\bcom/minecraft/atlamod\b}{join('/', split /\./, $PACKAGE)}ge;
    $text =~ s{\bAvatar-style elemental bending\b}{Elemental channeling}g;
    $text =~ s{\b[Aa]vatar-style\s+}{}g;                  # "adds Avatar-style **elemental bending**"
    # The old disclaimer named the franchise's owners; the README gets a neutral one instead.
    $text =~ s{^[^\n]*\bNickelodeon\b[^\n]*\n}{}mg;
    $text =~ s{\bAtla [Mm]od\b}{Elemental Paths}g;
    $text =~ s{\batla [Mm]od\b}{Elemental Paths}g;
    $text =~ s{\bAtlamodClient\b}{ElementalPathsClient}g;
    $text =~ s{\bAtlamod(?=[A-Z_]|\b)}{ElementalPaths}g;
    $text =~ s{\bAtlaConfig\b}{PathsConfig}g;
    $text =~ s{\bAtlaSettingsScreen\b}{PathsSettingsScreen}g;
    $text =~ s{\bAtla(?=[A-Z])}{Paths}g;                 # any other Atla* class
    $text =~ s{\bAtla\b}{Paths}g;                         # "Atla comet quad" and the like
    $text =~ s{atlamod}{$MODID}g;                         # mod id, namespaces, lang keys, mixin prefixes

    # --- the command ------------------------------------------------------------------
    $text =~ s{literal\("bend"\)}{literal("paths")}g;
    $text =~ s{/bend\b}{/paths}g;

    # --- renamed abilities --------------------------------------------------------------
    $text =~ s{\bLightning [Rr]edirection\b}{Bolt Deflection}g;
    $text =~ s{\blightning redirection\b}{bolt deflection}g;
    $text =~ s{\bLIGHTNING REDIRECTION\b}{BOLT DEFLECTION}g;
    $text =~ s{LightningRedirection}{BoltDeflection}g;
    $text =~ s{lightningRedirection}{boltDeflection}g;
    $text =~ s{LIGHTNING_REDIRECTION}{BOLT_DEFLECTION}g;
    $text =~ s{lightning_redirection}{bolt_deflection}g;

    $text =~ s{\bAir [Ss]cooter(s?)\b}{Gale Ride$1}g;
    $text =~ s{\bair scooter(s?)\b}{gale ride$1}g;
    $text =~ s{\bAIR SCOOTER(S?)\b}{GALE RIDE$1}g;
    $text =~ s{AirScootering}{GaleRiding}g;
    $text =~ s{airScootering}{galeRiding}g;
    $text =~ s{AirScooter}{GaleRide}g;
    $text =~ s{airScooter}{galeRide}g;
    $text =~ s{AIR_SCOOTER}{GALE_RIDE}g;
    $text =~ s{air_scooter}{gale_ride}g;
    $text =~ s{airscooter}{galeride}g;
    $text =~ s{\bscooter(s?)\b}{gale ride$1}g;            # prose: "the scooter"
    $text =~ s{\bScooter(s?)\b}{Gale Ride$1}g;
    $text =~ s{Scooter}{GaleRide}g;                       # inside identifiers
    $text =~ s{scooter}{galeRide}g;

    # --- Spirit World (and ONLY the world: Spirit Ore/Shard/Armor/Shrine keep their names) ---
    $text =~ s{Spirit World}{Spirit Realm}g;
    $text =~ s{spirit world}{spirit realm}g;
    $text =~ s{SPIRIT WORLD}{SPIRIT REALM}g;
    $text =~ s{SpiritWorld}{SpiritRealm}g;
    $text =~ s{spiritWorld}{spiritRealm}g;
    $text =~ s{SPIRIT_WORLD}{SPIRIT_REALM}g;
    $text =~ s{spirit_world}{spirit_realm}g;
    $text =~ s{spiritworld}{spiritrealm}g;

    # --- Avatar -> Paragon ------------------------------------------------------------
    $text =~ s{\b([Aa])n (Avatar)}{$1 Paragon}g;           # "an Avatar" -> "a Paragon"
    $text =~ s{Avatar}{Paragon}g;
    $text =~ s{avatar}{paragon}g;
    $text =~ s{AVATAR}{PARAGON}g;

    # --- bending -> channeling ---------------------------------------------------------
    $text =~ s{\b([Bb])ending ([Mm])enu\b}{($1 eq 'B' ? 'Paths' : 'paths') . " $2enu"}ge;

    # Compounds: "Firebending" -> "Fire Channeling" in prose, "disablesFirebending" ->
    # "disablesFireChanneling" inside an identifier (a letter, digit, _ / or : beside it).
    # Two passes, identifiers first, because Perl's lookbehind cannot be optional.
    my $SUFFIX = qr/(?:ing|ers?|able|s)?+/;   # possessive: never give "ing" back
    if ($mode eq 'path') {
        $text =~ s{($ELEMENT)[Bb]end($SUFFIX)}{$1 . channel_word('Bend', $2)}ge;
    } else {
        $text =~ s{(?<=[\w/:])($ELEMENT)[Bb]end($SUFFIX)}{$1 . channel_word('Bend', $2)}ge;
        $text =~ s{($ELEMENT)[Bb]end($SUFFIX)(?=[\w/:])}{$1 . channel_word('Bend', $2)}ge;
        $text =~ s{(?<![\w/:])($ELEMENT)[Bb]end($SUFFIX)(?![\w/:])}{
            my ($elem, $suffix) = ($1, $2);
            $elem . ' ' . channel_word($elem =~ /^[A-Z]/ ? 'Bend' : 'bend', $suffix)
        }ge;
    }

    $text =~ s{BEND}{CHANNEL}g;
    $text =~ s{Bend}{Channel}g;
    $text =~ s{bend}{channel}g;
    $text =~ s{\bbent\b}{channeled}g;                     # "bent lava, bent metal"

    $text =~ s/\x00(\d+)\x00/$saved[$1]/g;
    return $text;
}

my $mode = shift @ARGV // die "usage: $0 content|path ARGS...\n";

if ($mode eq 'path') {
    # Paths come as arguments, or one per line on stdin (Windows caps a command line at
    # 32k characters, which a whole repository listing overruns).
    my @paths = @ARGV ? @ARGV : map { chomp; $_ } <STDIN>;
    print transform($_, 'path'), "\n" for @paths;
    exit 0;
}

die "unknown mode $mode\n" unless $mode eq 'content';
for my $file (@ARGV) {
    local $/;
    open my $in, '<:raw', $file or die "$file: $!\n";
    my $before = <$in>;
    close $in;
    my $after = transform($before, 'content');
    next if $after eq $before;
    open my $out, '>:raw', $file or die "$file: $!\n";
    print $out $after;
    close $out;
}
