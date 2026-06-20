#!/bin/sh
set -e

CONFIG_FILE="${CONFIG_FILE:-/config/fsamp-config.env}"
AWS_ENDPOINT="${AWS_ENDPOINT_URL:-http://localstack:4566}"
MAX_WAIT_SECONDS="${CONFIG_WAIT_TIMEOUT:-120}"
POLL_INTERVAL=2

log() {
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] [entrypoint] $1"
}

error() {
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] [entrypoint] ERROR: $1" >&2
}
wait_for_localstack() {
    log "Waiting for LocalStack at $AWS_ENDPOINT..."
    
    waited=0
    while [ $waited -lt $MAX_WAIT_SECONDS ]; do
        if curl -sf "$AWS_ENDPOINT/_localstack/health" > /dev/null 2>&1; then
            log "OK LocalStack is healthy"
            return 0
        fi
        sleep $POLL_INTERVAL
        waited=$((waited + POLL_INTERVAL))
        log "  Waiting for LocalStack... (${waited}s/${MAX_WAIT_SECONDS}s)"
    done
    
    error "LocalStack not ready after ${MAX_WAIT_SECONDS}s"
    return 1
}
discover_cognito() {
    log "Discovering Cognito configuration from LocalStack..."
    
    waited=0
    while [ $waited -lt $MAX_WAIT_SECONDS ]; do
        POOLS_RESPONSE=$(curl -sf -X POST "$AWS_ENDPOINT" \
            -H "Content-Type: application/x-amz-json-1.1" \
            -H "X-Amz-Target: AWSCognitoIdentityProviderService.ListUserPools" \
            -d '{"MaxResults": 10}' 2>/dev/null || echo '{}')
        
        USER_POOL_ID=$(echo "$POOLS_RESPONSE" | grep -o '"Id":"[^"]*"' | head -1 | sed 's/"Id":"//;s/"//')
        
        if [ -n "$USER_POOL_ID" ] && [ "$USER_POOL_ID" != "null" ]; then
            break
        fi
        
        sleep $POLL_INTERVAL
        waited=$((waited + POLL_INTERVAL))
        log "  Waiting for Cognito User Pool... (${waited}s/${MAX_WAIT_SECONDS}s)"
    done
    
    if [ -z "$USER_POOL_ID" ] || [ "$USER_POOL_ID" = "null" ]; then
        error "Could not discover Cognito User Pool"
        return 1
    fi
    
    log "  OK User Pool ID: $USER_POOL_ID"
    
    CLIENTS_RESPONSE=$(curl -sf -X POST "$AWS_ENDPOINT" \
        -H "Content-Type: application/x-amz-json-1.1" \
        -H "X-Amz-Target: AWSCognitoIdentityProviderService.ListUserPoolClients" \
        -d "{\"UserPoolId\": \"$USER_POOL_ID\", \"MaxResults\": 10}" 2>/dev/null || echo '{}')
    
    CLIENT_ID=$(echo "$CLIENTS_RESPONSE" | grep -o '"ClientId":"[^"]*"' | head -1 | sed 's/"ClientId":"//;s/"//')
    
    if [ -z "$CLIENT_ID" ] || [ "$CLIENT_ID" = "null" ]; then
        error "Could not discover Cognito Client ID"
        return 1
    fi
    
    log "  OK Client ID: $CLIENT_ID"
    
    export COGNITO_USER_POOL_ID="$USER_POOL_ID"
    export COGNITO_CLIENT_ID="$CLIENT_ID"
    export COGNITO_JWKS_ENDPOINT="$AWS_ENDPOINT/$USER_POOL_ID/.well-known/jwks.json"
    export COGNITO_ISSUER_URI="$AWS_ENDPOINT/$USER_POOL_ID"
    
    log "OK Cognito configuration discovered"
    return 0
}
use_configured_cognito() {
    if [ -n "${COGNITO_USER_POOL_ID:-}" ] && [ -n "${COGNITO_CLIENT_ID:-}" ]; then
        export COGNITO_JWKS_ENDPOINT="${COGNITO_JWKS_ENDPOINT:-$AWS_ENDPOINT/$COGNITO_USER_POOL_ID/.well-known/jwks.json}"
        export COGNITO_ISSUER_URI="${COGNITO_ISSUER_URI:-$AWS_ENDPOINT/$COGNITO_USER_POOL_ID}"

        log "Using Cognito configuration from environment"
        log "  OK User Pool ID: $COGNITO_USER_POOL_ID"
        log "  OK Client ID: $COGNITO_CLIENT_ID"
        return 0
    fi

    return 1
}
load_config_file() {
    if [ -f "$CONFIG_FILE" ]; then
        log "Loading configuration from $CONFIG_FILE"
        set -a
        # shellcheck disable=SC1090
        . "$CONFIG_FILE"
        set +a
        
        export AWS_ENDPOINT_URL
        export AWS_REGION
        export COGNITO_USER_POOL_ID
        export COGNITO_CLIENT_ID
        export COGNITO_JWKS_ENDPOINT
        export COGNITO_ISSUER_URI
        export S3_BUCKET_NAME
        export SNS_TOPIC_ARN
        export DYNAMODB_TABLE_NAME
        export OUTBOX_TABLE_NAME
        export KMS_KEY_ID
        
        log "OK Configuration loaded from file"
        log "  COGNITO_ISSUER_URI: ${COGNITO_ISSUER_URI:-not set}"
        return 0
    fi
    return 1
}
main() {
    log "Starting FSAMP Gateway entrypoint..."
    log "Environment: ${SPRING_PROFILES_ACTIVE:-default}"
    
    if [ "$SPRING_PROFILES_ACTIVE" = "local" ] || [ "$SPRING_PROFILES_ACTIVE" = "e2e" ]; then
        if ! load_config_file; then
            wait_for_localstack
            use_configured_cognito || discover_cognito
        fi
    fi
    
    log "Configuration:"
    log "  AWS_ENDPOINT_URL: ${AWS_ENDPOINT_URL:-not set}"
    log "  AWS_REGION: ${AWS_REGION:-not set}"
    log "  COGNITO_USER_POOL_ID: ${COGNITO_USER_POOL_ID:-not set}"
    log "  COGNITO_CLIENT_ID: ${COGNITO_CLIENT_ID:-not set}"
    
    if [ "$SPRING_PROFILES_ACTIVE" = "local" ] || [ "$SPRING_PROFILES_ACTIVE" = "e2e" ]; then
        log "Disabling FIPS mode for local development"
        export JAVA_OPTS="${JAVA_OPTS} -Dorg.bouncycastle.fips.approved_only=false"
    fi
    
    log "Starting Java application..."
    exec java $JAVA_OPTS -jar /app/app.jar "$@"
}

main "$@"
