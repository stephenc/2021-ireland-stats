#!/usr/bin/env sh
sed -e 's/,//g;s:^:2022/:;s:/\(.\)/:/0\1/:;s|/2022 12:00 PM| 11:00:00+00|;s|/2022 11:00 AM| 11:00:00+00|;s:/\(.\) 11:/0\1 11:;s/ \t/,/g' | sort
