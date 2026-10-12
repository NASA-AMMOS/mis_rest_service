#!/usr/bin/env bash

script_dir=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
cd $script_dir/..

cleanup() {
  echo "stopping tomcat..."
  ./test/tomcat/bin/catalina.sh stop >/dev/null 2>&1
}

trap cleanup EXIT

test_alive() {
  echo "testing servlet is alive..."

  local status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/image_sampler/version")
  if [[ "$status" != "200" ]]; then
    echo "FAILURE: HTTP status code $status != 200 for image_sampler/version"
    exit 1
  fi
}

test_json() {
  local q="$1"
  local expected="$2"
  echo "testing query $q"

  local response=$(curl -s -w "\n%{http_code}" "http://localhost:8080/image_sampler/$q")
  local status=$(echo "$response" | tail -n1)
  local json_response=$(echo "$response" | sed '$d' | tr -d '\n')

  if [[ "$status" != "200" ]]; then
    echo "FAILURE: HTTP status code $status != 200 for $q"
    exit 1
  fi

  if [[ -z "$expected" ]]; then echo $json_response;
  elif [[ "$json_response" != "$expected" ]]; then
    echo "FAILURE: mismatch $json_response != $expected for $q"
    exit 1
  fi
}

test_fail() {
  local q="$1"
  local expected="$2"
  echo "testing query $q (expect $expected)"
  local status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/image_sampler/$q")
  if [[ -z "$expected" ]]; then echo STATUS=$status
  elif [[ "$status" != "$expected" ]]; then
    echo "FAILURE: HTTP status code $status != $expected for $q"
    exit 1
  fi
}

echo "starting tomcat..."
./test/tomcat/bin/catalina.sh run >/dev/null 2>&1 &

sleep 10

test_alive

base="https://d1ejlg980osaur.cloudfront.net/m20/r14/mars2020_mastcamz_ops_calibrated/data/sol/01618/ids/fdr/zcam"
f=ZLF_1618_0810575881_473FDR_N0790198ZCAM09697_0340LMJ01.IMG

test_json "?url=$base/$f&line=327&sample=438" "[788,583,356]"
test_json "?url=file://$f&line=327&sample=438" "[788,583,356]"
test_json "?url=$base/$f&line=327.6&sample=438.2&interp=none" "[788,583,356]"
test_json "?url=$base/$f&line=327.6&sample=438.2&interp=interp_dn" "[840,597,363]"
test_json "?url=$base/$f&line=327.6&sample=438.2&interp=interp_nonzero" "[840,597,363]"
test_fail "?url=$base/$f&line=327&sample=5000" 400
test_json "?url=$base/$f&line=327&sample=438&origin=0" "[988,697,456]"
test_json "?url=$base/$f&line=327&sample=438&label=true" "{\"_r\":\"788.0\",\"_g\":\"583.0\",\"_b\":\"356.0\"}"

base="https://d1ejlg980osaur.cloudfront.net/m20/r14/mars2020_navcam_ops_stereo/data/sol/01616/ids/rdr/ncam"
f=NLF_1616_0810395357_230XYZ_N0790198NCAM14615_0A0195J01.IMG
test_json "?url=$base/$f&line=800&sample=900" "[-8.533735275268555,9.457708358764648,-0.968799352645874]"
test_json "?url=$base/$f&line=800&sample=900&label=true" "{\"x\":-8.533735275268555,\"y\":9.457708358764648,\"z\":-0.968799352645874}"

base="https://d1ejlg980osaur.cloudfront.net/m20/cumulative/mars2020_navcam_ops_stereo/data/sol/00002/ids/rdr/ncam"
f=NLFC0002_0667129561_000ARM_N0010052AUT_04096_0A02I3J02.IMG
test_json "?url=$base/$f&line=405&sample=410&label=true" "{\"*Shoulder\":\"  O  O  O  O  I  I  I  I\",\"*Elbow\":\"  U  U  D  D  U  U  D  D\",\"*Wrist\":\"  U  D  U  D  U  D  U  D\",\"DRILL\":\"  0  0  0  0  0  0  0  0 \",\"GDRT\":\"  0  0  0  0  0  0  0  0 \",\"WATSON\":\"  0  0  0  0  0  0  0  0 \",\"SHERLOC\":\"  0  0  0  0  0  0  0  0 \",\"PIXL\":\"  0  0  0  0  0  0  0  0 \",\"FCS\":\"  0  0  0  0  0  0  0  0 \"}"

# test giant image
base="https://d1ejlg980osaur.cloudfront.net/m20/r14/mars2020_mastcamz_ops_mosaic/data/sol/01618/ids/rdr/mosaic"
f=Z_LRGB_1618XRZS_0790198_ORR_L_45M01CMJ08.IMG
test_json "?url=$base/$f&line=8952&sample=7545" "[1584,1124,529]"

# test volume query
base="https://d1ejlg980osaur.cloudfront.net/m20/r14/mars2020_navcam_ops_stereo/data/sol/01616/ids/rdr/ncam"
f=NLF_1616_0810395357_230XYZ_N0790198NCAM14615_0A0195J01.IMG
test_json "?url=$base/$f&sphere=-7,0,0,0.8" "[[-7.01116943359375,0.18902641534805298,0.7763946652412415],[-7.015096187591553,0.17755135893821716,0.7796444892883301],[-6.966348171234131,0.21781957149505615,0.767669141292572],[-6.965902328491211,0.20817643404006958,0.7693219184875488],[-6.969134330749512,0.19702108204364777,0.772316575050354],[-6.977504253387451,0.18374477326869965,0.7771876454353333],[-6.916011810302734,0.23899881541728973,0.758683979511261],[-6.915041446685791,0.22960896790027618,0.7601392865180969],[-6.917169094085693,0.21894559264183044,0.7627272605895996],[-6.9249701499938965,0.20594346523284912,0.7673910856246948],[-6.933921813964844,0.19244971871376038,0.7724789977073669],[-6.941463470458984,0.17951779067516327,0.777053952217102],[-6.890510559082031,0.18154579401016235,0.7713842391967773],[-6.89542818069458,0.15988749265670776,0.7768160104751587]]"
test_json "?url=$base/$f&sphere=-7,0,0,0.8&plane=0,0,1,0.76" "[[-6.916011810302734,0.23899881541728973,0.758683979511261]]"
test_json "?url=$base/$f&sphere=-7,0,0,0.8&surface=0,0,1,0.76" "{\"num_points\":14,\"min_distance\":-0.0013160204887390226,\"max_distance\":0.01964448928833007,\"histogram_bin_width\":0.001,\"histogram_outliers_below\":0.0,\"histogram_outliers_above\":0.0,\"histogram_below\":[0,1],\"histogram_above\":[1,0,1,0,0,0,0,2,0,1,0,1,2,0,0,0,2,2,0,1]}"

#RDR URL whitelist: localhost-noaws-web.xml allows only http[s] URLs and local paths with no colon, optionally file://
test_fail "?url=ftp%3A%2F%2Fexample.com%2Fx.IMG&line=1&sample=1" 400               #ftp://example.com/x.IMG

#absolute local paths are rejected with 400, with or without file://, whether or not the file exists
#if they were instead resolved relative to fs_input_dir these would be 404
test_fail "?url=%2Fetc%2Fpasswd.IMG&line=1&sample=1" 400                           #/etc/passwd.IMG
test_fail "?url=file%3A%2F%2F%2Fetc%2Fpasswd.IMG&line=1&sample=1" 400              #file:///etc/passwd.IMG
test_fail "?url=$PWD/test/$f&line=1&sample=1" 400                                  #exists at this absolute path
test_fail "?url=file://$PWD/test/$f&line=1&sample=1" 400                           #exists at this absolute path

#SSRF filter: image URLs on non-public hosts are rejected with 400
#URLs are percent encoded, also [] would otherwise be interpreted by curl as globs
test_fail "?url=http%3A%2F%2Flocalhost%3A8080%2Fx.IMG&line=1&sample=1" 400  #http://localhost:8080/x.IMG
test_fail "?url=http%3A%2F%2FLOCALHOST.%3A8080%2Fx.IMG&line=1&sample=1" 400 #http://LOCALHOST.:8080/x.IMG
test_fail "?url=http%3A%2F%2Ffoo.localhost%2Fx.IMG&line=1&sample=1" 400     #http://foo.localhost/x.IMG
test_fail "?url=http%3A%2F%2F127.0.0.1%3A8080%2Fx.IMG&line=1&sample=1" 400  #http://127.0.0.1:8080/x.IMG
test_fail "?url=http%3A%2F%2F127.1%2Fx.IMG&line=1&sample=1" 400             #http://127.1/x.IMG
test_fail "?url=http%3A%2F%2F2130706433%2Fx.IMG&line=1&sample=1" 400        #http://2130706433/x.IMG (127.0.0.1)
test_fail "?url=http%3A%2F%2F0.0.0.0%2Fx.IMG&line=1&sample=1" 400           #http://0.0.0.0/x.IMG
test_fail "?url=http%3A%2F%2F10.1.2.3%2Fx.IMG&line=1&sample=1" 400          #http://10.1.2.3/x.IMG
test_fail "?url=http%3A%2F%2F172.16.0.1%2Fx.IMG&line=1&sample=1" 400        #http://172.16.0.1/x.IMG
test_fail "?url=http%3A%2F%2F192.168.1.1%2Fx.IMG&line=1&sample=1" 400       #http://192.168.1.1/x.IMG
test_fail "?url=http%3A%2F%2F100.100.100.200%2Fx.IMG&line=1&sample=1" 400   #http://100.100.100.200/x.IMG (CGNAT)
test_fail "?url=http%3A%2F%2F169.254.169.254%2Flatest%2Fmeta-data%2Fx.IMG&line=1&sample=1" 400 #http://169.254.169.254/latest/meta-data/x.IMG
test_fail "?url=http%3A%2F%2Fmetadata.google.internal%2Fx.IMG&line=1&sample=1" 400 #http://metadata.google.internal/x.IMG
test_fail "?url=http%3A%2F%2Fexample.com%40127.0.0.1%2Fx.IMG&line=1&sample=1" 400  #http://example.com@127.0.0.1/x.IMG
test_fail "?url=http%3A%2F%2F%5B%3A%3A1%5D%3A8080%2Fx.IMG&line=1&sample=1" 400     #http://[::1]:8080/x.IMG
test_fail "?url=http%3A%2F%2F%5Bfe80%3A%3A1%5D%2Fx.IMG&line=1&sample=1" 400         #http://[fe80::1]/x.IMG
test_fail "?url=http%3A%2F%2F%5Bfd00%3Aec2%3A%3A254%5D%2Fx.IMG&line=1&sample=1" 400 #http://[fd00:ec2::254]/x.IMG (AWS IPv6 metadata)
test_fail "?url=http%3A%2F%2F%5B%3A%3Affff%3A169.254.169.254%5D%2Fx.IMG&line=1&sample=1" 400 #http://[::ffff:169.254.169.254]/x.IMG
test_fail "?url=http%3A%2F%2F%5B64%3Aff9b%3A%3Aa9fe%3Aa9fe%5D%2Fx.IMG&line=1&sample=1" 400 #http://[64:ff9b::a9fe:a9fe]/x.IMG (NAT64)
test_fail "?url=http%3A%2F%2F%5B2002%3Aa9fe%3Aa9fe%3A%3A%5D%2Fx.IMG&line=1&sample=1" 400 #http://[2002:a9fe:a9fe::]/x.IMG (6to4)
test_fail "?url=http%3A%2F%2Fno-such-host.invalid%2Fx.IMG&line=1&sample=1" 400  #http://no-such-host.invalid/x.IMG

echo "SUCCESS"
