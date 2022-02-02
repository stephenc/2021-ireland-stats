#!/usr/bin/env bash

set -e
set -o pipefail

mkdir -p data

function curl_etag() {
  echo "Fetching $1.csv " && curl -L \
    --etag-save "$1.etag" \
    --etag-compare "$1.etag" \
    --remote-time \
    -o "$1.csv" \
    "$2" && echo ""
}

curl_etag \
  data/COVID-19_Laboratory_Testing_Time_Series \
  https://covid-19.geohive.ie/datasets/f6d6332820ca466999dbd852f6ad4d5a_0.csv
curl_etag \
  data/COVID-19_NOCA_ICUBIS_Historic_Time_Series \
  https://covid-19.geohive.ie/datasets/c8208a0a8ff04a45b2922ae69e9b2206_0.csv
curl_etag \
  data/COVID-19_SDU_Acute_Hospital_Time_Series_Summary \
  https://covid-19.geohive.ie/datasets/fe9bb23592ec4142a4f4c2c9bd32f749_0.csv
curl_etag \
  data/COVID-19_HPSC_Detailed_Statistics_Profile \
  https://covid-19.geohive.ie/datasets/d8eb52d56273413b84b0187a4e9117be_0.csv
curl_etag \
  data/COVID-19_HPSC_County_Statistics_Latest_Point_Geometry \
  https://covid-19.geohive.ie/datasets/4779c505c43c40da9101ce53f34bb923_0.csv
curl_etag \
  data/COVID-19_HPSC_County_Statistics_Historic_Point_Geometry \
  https://covid-19.geohive.ie/datasets/5117479f97724d4ead6171ec2b8b912d_0.csv
curl_etag \
  data/COVID-19_HPSC_County_Statistics_Historic_Data \
  https://covid-19.geohive.ie/datasets/d9be85b30d7748b5b7c09450b8aede63_0.csv
curl_etag \
  data/COVID-19_HSE_Weekly_Vaccination_Figures \
  https://covid-19.geohive.ie/datasets/0101ed10351e42968535bb002f94c8c6_0.csv
curl_etag \
  data/COVID-19_HSE_Daily_Vaccination_Figures \
  https://covid-19.geohive.ie/datasets/a0e3a1c53ad8422faf00604ee08955db_0.csv
curl_etag \
  data/COVID-19_HPSC_HIU_Latest_Local_Electoral_Area_Mapped \
  https://covid-19.geohive.ie/datasets/27d401c9ae084097bb1f3a69b69462a1_0.csv
curl_etag \
  data/COVID-19_HPSC_HIU_Timeseries_Local_Electoral_Area_Mapped \
  https://covid-19.geohive.ie/datasets/7a10c7d87a634e71a1655e7451522b53_0.csv

# This was the only source of antigen data I could find and had to go to the
# backing arcgis service behind geohive to get it
# etags seem to be supported for conditional gets so shouldn't cause trouble
# if we scrape it once an hour with a conditional get
echo "Fetching COVID-19_Antigen.csv " 
curl \
  -H 'Accept: application/json' \
  --etag-save "data/COVID-19_Antigen.etag" \
  --etag-compare "data/COVID-19_Antigen.etag" \
  --remote-time \
  --output "data/COVID-19_Antigen.json" \
  'https://services-eu1.arcgis.com/z6bHNio59iTqqSUY/ArcGIS/rest/services/Registered_Positive_Antigen_View/FeatureServer/0/query?where=RegisteredPositiveAntigenFigure+is+not+null&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&resultType=none&distance=0.0&units=esriSRUnit_Meter&returnGeodetic=false&outFields=DateOfData%2CRegisteredPositiveAntigenFigure&returnGeometry=false&featureEncoding=esriDefault&multipatchOption=none&maxAllowableOffset=&geometryPrecision=&outSR=&datumTransformation=&applyVCSProjection=false&returnIdsOnly=false&returnUniqueIdsOnly=false&returnCountOnly=false&returnExtentOnly=false&returnQueryGeometry=false&returnDistinctValues=false&cacheHint=false&orderByFields=&groupByFieldsForStatistics=&outStatistics=&having=&resultOffset=&resultRecordCount=&returnZ=false&returnM=false&returnExceededLimitFeatures=false&quantizationParameters=&sqlFormat=none&f=json&token='
jq -r  '["DateOfData","RegisteredPositiveAntigenFigure"],(.features[].attributes | [(.DateOfData / 1000 | strftime("%Y/%m/%d %H:%M:%S+00")), .RegisteredPositiveAntigenFigure]) | @csv' data/COVID-19_Antigen.json > data/COVID-19_Antigen.csv
echo ""
