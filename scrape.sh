#!/usr/bin/env bash

set -e
set -o pipefail

mkdir -p data

curl -L -o data/irl-lab-historic.raw.csv https://opendata.arcgis.com/datasets/f6d6332820ca466999dbd852f6ad4d5a_0.csv
curl -L -o data/irl-icu-historic.raw.csv https://opendata.arcgis.com/datasets/c8208a0a8ff04a45b2922ae69e9b2206_0.csv
curl -L -o data/irl-hosp-historic.raw.csv https://opendata.arcgis.com/datasets/fe9bb23592ec4142a4f4c2c9bd32f749_0.csv
curl -L -o data/irl-stats.raw.csv https://opendata.arcgis.com/datasets/d8eb52d56273413b84b0187a4e9117be_0.csv

