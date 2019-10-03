#!/usr/bin/env bash
# postdeploy script used by Heroku for review apps
set -v

# Set APPLICATION_HOST in review app / demo app
echo $HEROKU_APP_NAME
if echo $HEROKU_APP_NAME | grep -Ewq 'signalement-api-pr-\d+'
then
    export APPLICATION_HOST=$HEROKU_APP_NAME.herokuapp.com
else
    export APPLICATION_HOST='demo-signalement-api.beta.gouv.fr'
fi

# Setup dummy data
psql $DATABASE_URL < ./test/scripts/insert_users.sql        \
&& psql $DATABASE_URL < ./test/scripts/insert_reports.sql
set +v
