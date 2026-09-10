#!/usr/bin/env bash

script_dir=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
cd $script_dir/..

mvn clean

mvn -P jar package

cp target/image_sampler.jar test
