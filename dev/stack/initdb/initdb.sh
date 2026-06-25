#!/usr/bin/env bash


# The psql commands below should connect to the $POSTGRES_DB by default
export PGDATABASE="$POSTGRES_DB"


# Revoke TEMPORARY permission from database

echo "REVOKE TEMPORARY ON DATABASE ${POSTGRES_DB} FROM PUBLIC" | psql


# Create additionals DB roles and grant privileges

# ddluser
DB_USERNAME=ddluser
DB_PASSWORD=ddluser
echo "CREATE USER ${DB_USERNAME} WITH PASSWORD '${DB_PASSWORD}'" | psql
echo "ALTER USER ${DB_USERNAME} CREATEROLE" | psql
echo "GRANT CREATE ON DATABASE ${POSTGRES_DB} TO ${DB_USERNAME}" | psql
# Uncomment in case the following privilege would become necessary:
# echo "GRANT CREATE ON SCHEMA public TO ${DB_USERNAME}" | psql

# dmluser
DB_USERNAME=dmluser
DB_PASSWORD=dmluser
echo "CREATE USER ${DB_USERNAME} WITH PASSWORD '${DB_PASSWORD}'" | psql
