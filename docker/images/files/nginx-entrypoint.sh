#!/usr/bin/env bash

#########################################
## Air Gapped config
#########################################

if [[ $KODA_FLAGS == *"enable-air-gapped-conf"* ]]; then
    rm /etc/nginx/overrides/location.d/external-locations.conf;
    export KODA_FLAGS="$KODA_FLAGS disable-google-fonts-provider disable-dashboard-templates-section"
fi

#########################################
## App Frontend config
#########################################

update_flags() {
  if [ -n "$KODA_FLAGS" ]; then
    echo "$(sed \
      -e "s|^//var kodaFlags = .*;|var kodaFlags = \"$KODA_FLAGS\";|g" \
      "$1")" > "$1"
  fi
}

update_flags /var/www/app/js/config.js

#########################################
## Nginx Config
#########################################

export KODA_BACKEND_URI=${KODA_BACKEND_URI:-http://koda-backend:6060}
export KODA_EXPORTER_URI=${KODA_EXPORTER_URI:-http://koda-exporter:6061}
export KODA_HTTP_SERVER_MAX_MULTIPART_BODY_SIZE=${KODA_HTTP_SERVER_MAX_MULTIPART_BODY_SIZE:-367001600} # Default to 350MiB
envsubst "\$KODA_BACKEND_URI,\$KODA_EXPORTER_URI,\$KODA_HTTP_SERVER_MAX_MULTIPART_BODY_SIZE" \
         < /tmp/nginx.conf.template > /etc/nginx/nginx.conf

KODA_DEFAULT_INTERNAL_RESOLVER="$(awk 'BEGIN{ORS=" "} $1=="nameserver" { sub(/%.*$/,"",$2); print ($2 ~ ":")? "["$2"]": $2}' /etc/resolv.conf)"
export KODA_INTERNAL_RESOLVER=${KODA_INTERNAL_RESOLVER:-$KODA_DEFAULT_INTERNAL_RESOLVER}
envsubst "\$KODA_INTERNAL_RESOLVER" \
         < /tmp/resolvers.conf.template > /etc/nginx/overrides/http.d/resolvers.conf

exec "$@";
