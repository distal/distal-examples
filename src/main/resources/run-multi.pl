#!/usr/bin/perl -w

die "wrong number of arguments" unless $#ARGV == 1;
my $runit = shift;
my $properties = shift;

sub doit_forall_confs; #(\@\%);
sub doit; #(\%);


$from = "$properties.multi";
$dst =  "$properties";

open FROM, "<$from";


my %configs = ();
my @names = ();

foreach (<FROM>) {
    s/#.*//;
    chomp;
    next if length == 0;

    my ($name, $vals) = split /=/, $_, 2;
    my @vals = split /(?<!\\")(?<=")\s+(?=")/, $vals;
    @vals = split /\s+/, $vals if $vals !~ /"/;
    $configs{$name} = [ @vals ];
    push @names, $name;
}

close FROM;

doit_forall_confs(\@names,\%configs,0);

sub doit {
    my $hashref = shift;
    open OUT, ">$dst";
    foreach (keys %$hashref) {
        print OUT "$_=$hashref->{$_}\n"
    }
    close OUT;
    system("$runit");
}


%the_config = ();
sub doit_forall_confs {
    my $names = shift @_;
    my @configs = shift @_;
    my $index = shift @_;

    my $name = $names[$index];

    my @confs = @{ $configs{$name} };

    foreach my $conf (@confs) {
        $the_config{$name} = $conf;
        if ($index == $#names) {
            doit(\%the_config);
        } else {
            &doit_forall_confs(\@names,\%configs,$index + 1);
        }
    }
}
