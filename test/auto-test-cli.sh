#!/usr/bin/env bash

script_dir=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
cd $script_dir

run_query() {
  ./run-cli.sh -i "$@" 2>/dev/null >> output.txt
}

rm -f output.txt

base="https://d1ejlg980osaur.cloudfront.net/m20/r14/mars2020_mastcamz_ops_calibrated/data/sol/01618/ids/fdr/zcam"
f=ZLF_1618_0810575881_473FDR_N0790198ZCAM09697_0340LMJ01.IMG

run_query $base/$f -l 327 -s 438

run_query file://$f -l 327 -s 438

run_query $f -l 327 -s 438
run_query $f -l 327.5 -s 438.5
run_query $f -l 327.5 -s 438.5 -p interp_dn
run_query $f -l 327.5 -s 438 -p interp_dn
run_query $f -l 327.5 -s 438.5 -o 0
run_query $f -l 327.5 -s 438.5 -p interp_dn -o 0
run_query $f -l 327.5 -s 438 -p interp_dn -o 0
run_query $f -l 327.5 -s 438 -p interp_dn -o 0 -a

run_query $f -f test-input.txt

# test float image
f=NLF_1616_0810395357_230XYZ_N0790198NCAM14615_0A0195J01.IMG
run_query $f -l 352 -s 456 -a
run_query $f -l 800 -s 900
run_query $f -l 800 -s 900 -a

# test interp_nonzero
run_query $f -l 800.6 -s 900
run_query $f -l 800.6 -s 900 -p interp_dn
run_query $f -l 800.6 -s 900 -p interp_nonzero
run_query $f -l 500 -s 1250
run_query $f -l 500 -s 1250 -p interp_dn
run_query $f -l 500 -s 1250.5 -p interp_dn
run_query $f -l 500 -s 1250 -p interp_nonzero
run_query $f -l 500 -s 1251

# test unpack
f=NLFC0002_0667129561_000ARM_N0010052AUT_04096_0A02I3J02.IMG
run_query $f -l 405 -s 410 -m M20 -a

# test giant image
f=Z_LRGB_1618XRZS_0790198_ORR_L_45M01CMJ08.IMG
run_query $f -l 8952 -s 7545 -a

# test volume query
f=NLF_1616_0810395357_230XYZ_N0790198NCAM14615_0A0195J01.IMG
run_query $f -sp -7,0,0,0.8
run_query $f -sp -7,0,0,0.8 -pl 0,0,1,0.76
run_query $f -sp -7,0,0,0.8 -sf 0,0,1,0.76

if diff expected.txt output.txt > /dev/null 2>&1; then
    echo "SUCCESS"
    rm -f output.txt
else
    echo "FAILURE"
fi
