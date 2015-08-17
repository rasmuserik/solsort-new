# Do whatever you want with distributable package, ie. release etc.

# Autogenerate README.md

printf "
[![Build Status](https://travis-ci.org/rasmuserik/lemon.svg?branch=master)](https://travis-ci.org/rasmuserik/lemon)
[![Dependencies Status](http://jarkeeper.com/rasmuserik/lemon/status.png)](http://jarkeeper.com/rasmuserik/lemon)

\`autogenerated README.md from literate code, do not edit directly\`

" |
cat - src/*/core.cljs |
tr "\n" "\r" |
sed -e "s/\(\r;;[^\r]*\r\)\([^;]\)/\1\r\2/g" |
tr "\r" "\n" |
sed -e "s/^/    /" |
sed -e "s/^    ;;//" \
  > README.md
