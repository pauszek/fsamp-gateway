#!/bin/sh
# =============================================================================
# FSAMP Gateway - Docker Entrypoint
# =============================================================================
# Enterprise-grade entrypoint that:
# 1. Waits for LocalStack configuration (shared volume)
# 2. Discovers Cognito IDs from LocalStack API
# 3. Sets environment variables dynamically
# 4. Starts the application
#
# This follows the Init Container Pattern for service discovery.
# =============================================================================

set -e

# Configuration
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

# =============================================================================
# Wait for LocalStack to be ready
# =============================================================================
wait_for_localstack() {
    log "Waiting for LocalStack at $AWS_ENDPOINT..."
    
    waited=0
    while [ $waited -lt $MAX_WAIT_SECONDS ]; do
        if curl -sf "$AWS_ENDPOINT/_localstack/health" > /dev/null 2>&1; then
            log "✓ LocalStack is healthy"
            return 0
        fi
        sleep $POLL_INTERVAL
        waited=$((waited + POLL_INTERVAL))
        log "  Waiting for LocalStack... (${waited}s/${MAX_WAIT_SECONDS}s)"
    done
    
    error "LocalStack not ready after ${MAX_WAIT_SECONDS}s"
    return 1
}

# =============================================================================
# Discover Cognito configuration from LocalStack
# =============================================================================
discover_cognito() {
    log "Discovering Cognito configuration from LocalStack..."
    
    # Wait for Cognito to have user pools
    waited=0
    while [ $waited -lt $MAX_WAIT_SECONDS ]; do
        # List user pools
        POOLS_RESPONSE=$(curl -sf -X POST "$AWS_ENDPOINT" \
            -H "Content-Type: application/x-amz-json-1.1" \
            -H "X-Amz-Target: AWSCognitoIdentityProviderService.ListUserPools" \
            -d '{"MaxResults": 10}' 2>/dev/null || echo '{}')
        
        # Extract first pool ID (using grep/sed for Alpine compatibility)
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
    
    log "  ✓ User Pool ID: $USER_POOL_ID"
    
    # Get Client ID
    CLIENTS_RESPONSE=$(curl -sf -X POST "$AWS_ENDPOINT" \
        -H "Content-Type: application/x-amz-json-1.1" \
        -H "X-Amz-Target: AWSCognitoIdentityProviderService.ListUserPoolClients" \
        -d "{\"UserPoolId\": \"$USER_POOL_ID\", \"MaxResults\": 10}" 2>/dev/null || echo '{}')
    
    CLIENT_ID=$(echo "$CLIENTS_RESPONSE" | grep -o '"ClientId":"[^"]*"' | head -1 | sed 's/"ClientId":"//;s/"//')
    
    if [ -z "$CLIENT_ID" ] || [ "$CLIENT_ID" = "null" ]; then
        error "Could not discover Cognito Client ID"
        return 1
    fi
    
    log "  ✓ Client ID: $CLIENT_ID"
    
    # Export environment variables for Spring Boot
    export COGNITO_USER_POOL_ID="$USER_POOL_ID"
    export COGNITO_CLIENT_ID="$CLIENT_ID"
    export COGNITO_JWKS_ENDPOINT="$AWS_ENDPOINT/$USER_POOL_ID/.well-known/jwks.json"
    export COGNITO_ISSUER_URI="$AWS_ENDPOINT/$USER_POOL_ID"
    
    log "✓ Cognito configuration discovered"
    return 0
}

# =============================================================================
# Load configuration from shared volume (if available)
# =============================================================================
load_config_file() {
    if [ -f "$CONFIG_FILE" ]; then
        log "Loading configuration from $CONFIG_FILE"
        # Enable auto-export so all vars are exported to child processes
        set -a
        # shellcheck disable=SC1090
        . "$CONFIG_FILE"
        set +a
        
        # Explicitly export critical vars (belt and suspenders)
        export AWS_ENDPOINT_URL
        export AWS_REGION
        export COGNITO_USER_POOL_ID
        export COGNITO_CLIENT_ID
        export COGNITO_JWKS_ENDPOINT
        export COGNITO_ISSUER_URI
        export S3_BUCKET_NAME
        export SNS_TOPIC_ARN
        export DYNAMODB_TABLE_NAME
        export KMS_KEY_ID
        
        log "✓ Configuration loaded from file"
        log "  COGNITO_ISSUER_URI: ${COGNITO_ISSUER_URI:-not set}"
        return 0
    fi
    return 1
}

# =============================================================================
# Main
# =============================================================================
main() {
    log "Starting FSAMP Gateway entrypoint..."
    log "Environment: ${SPRING_PROFILES_ACTIVE:-default}"
    
    # For local/e2e profiles, discover Cognito from LocalStack
    if [ "$SPRING_PROFILES_ACTIVE" = "local" ] || [ "$SPRING_PROFILES_ACTIVE" = "e2e" ]; then
        # Try loading from config file first (faster)
        if ! load_config_file; then
            # Fall back to discovery from LocalStack API
            wait_for_localstack
            discover_cognito
        fi
    fi
    
    # Log final configuration
    log "Configuration:"
    log "  AWS_ENDPOINT_URL: ${AWS_ENDPOINT_URL:-not set}"
    log "  AWS_REGION: ${AWS_REGION:-not set}"
    log "  COGNITO_USER_POOL_ID: ${COGNITO_USER_POOL_ID:-not set}"
    log "  COGNITO_CLIENT_ID: ${COGNITO_CLIENT_ID:-not set}"
    
    # Disable FIPS mode for local development (LocalStack doesn't support FIPS)
    if [ "$SPRING_PROFILES_ACTIVE" = "local" ] || [ "$SPRING_PROFILES_ACTIVE" = "e2e" ]; then
        log "Disabling FIPS mode for local development"
        export JAVA_OPTS="${JAVA_OPTS} -Dorg.bouncycastle.fips.approved_only=false"
    fi
    
    # Start the application
    log "Starting Java application..."
    exec java $JAVA_OPTS -jar /app/app.jar "$@"
}

main "$@"
