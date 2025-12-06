#!/bin/bash
set -e

# Koda Production Setup Script for DigitalOcean
# This script sets up a production Koda instance on a fresh Ubuntu 22.04 Droplet

echo "======================================"
echo "  Koda Production Setup Script"
echo "======================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}Please run as root (sudo ./setup.sh)${NC}"
    exit 1
fi

# Get domain from user
read -p "Enter your domain (e.g., koda.example.com): " DOMAIN
if [ -z "$DOMAIN" ]; then
    echo -e "${RED}Domain is required${NC}"
    exit 1
fi

# Get email for Let's Encrypt
read -p "Enter email for SSL certificate: " EMAIL
if [ -z "$EMAIL" ]; then
    echo -e "${RED}Email is required for SSL certificate${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}Setting up Koda for domain: $DOMAIN${NC}"
echo ""

# Step 1: Update system
echo -e "${GREEN}[1/8] Updating system packages...${NC}"
apt-get update && apt-get upgrade -y

# Step 2: Install Docker
echo -e "${GREEN}[2/8] Installing Docker...${NC}"
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com -o get-docker.sh
    sh get-docker.sh
    rm get-docker.sh
    
    # Install Docker Compose
    apt-get install -y docker-compose-plugin
    
    # Enable Docker
    systemctl enable docker
    systemctl start docker
else
    echo "Docker already installed"
fi

# Step 3: Create application directory
echo -e "${GREEN}[3/8] Creating application directory...${NC}"
mkdir -p /opt/koda
cd /opt/koda

# Step 4: Clone or copy the repository
echo -e "${GREEN}[4/8] Setting up Koda files...${NC}"
if [ ! -f "docker-compose.yml" ]; then
    echo -e "${YELLOW}Please copy the docker/production folder contents to /opt/koda${NC}"
    echo "You can do this with: scp -r docker/production/* root@your-server:/opt/koda/"
    exit 1
fi

# Step 5: Create .env file
echo -e "${GREEN}[5/8] Creating environment configuration...${NC}"
if [ ! -f ".env" ]; then
    cp .env.example .env
    
    # Generate secure passwords/keys
    POSTGRES_PASSWORD=$(openssl rand -base64 32 | tr -dc 'a-zA-Z0-9' | head -c 32)
    SECRET_KEY=$(openssl rand -base64 64 | tr -dc 'a-zA-Z0-9' | head -c 64)
    
    # Update .env file
    sed -i "s/DOMAIN=.*/DOMAIN=$DOMAIN/" .env
    sed -i "s|PUBLIC_URI=.*|PUBLIC_URI=https://$DOMAIN|" .env
    sed -i "s/POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$POSTGRES_PASSWORD/" .env
    sed -i "s/SECRET_KEY=.*/SECRET_KEY=$SECRET_KEY/" .env
    
    echo ""
    echo -e "${YELLOW}==================== IMPORTANT ====================${NC}"
    echo -e "Generated credentials saved to /opt/koda/.env"
    echo -e "PostgreSQL Password: $POSTGRES_PASSWORD"
    echo -e "Secret Key: $SECRET_KEY"
    echo -e "${YELLOW}Please update SMTP and Spaces settings in .env${NC}"
    echo -e "${YELLOW}==================================================${NC}"
    echo ""
else
    echo ".env file already exists"
fi

# Step 6: Update nginx config with domain
echo -e "${GREEN}[6/8] Configuring Nginx...${NC}"
sed -i "s/\${DOMAIN}/$DOMAIN/g" nginx.conf

# Step 7: Create required directories
echo -e "${GREEN}[7/8] Creating data directories...${NC}"
mkdir -p data/postgres
mkdir -p data/redis
mkdir -p data/assets
mkdir -p certbot/conf
mkdir -p certbot/www

# Step 8: Initial SSL certificate (staging first)
echo -e "${GREEN}[8/8] Obtaining SSL certificate...${NC}"

# First, start nginx without SSL to get certificate
cat > nginx-initial.conf << 'INITCONF'
events {
    worker_connections 1024;
}
http {
    server {
        listen 80;
        server_name _;
        location /.well-known/acme-challenge/ {
            root /var/www/certbot;
        }
        location / {
            return 200 'Koda is being set up...';
            add_header Content-Type text/plain;
        }
    }
}
INITCONF

# Run temporary nginx for certificate
docker run -d --name nginx-temp \
    -p 80:80 \
    -v $(pwd)/nginx-initial.conf:/etc/nginx/nginx.conf:ro \
    -v $(pwd)/certbot/www:/var/www/certbot:ro \
    nginx:alpine

# Get certificate
docker run --rm \
    -v $(pwd)/certbot/conf:/etc/letsencrypt \
    -v $(pwd)/certbot/www:/var/www/certbot \
    certbot/certbot certonly \
    --webroot \
    --webroot-path=/var/www/certbot \
    --email $EMAIL \
    --agree-tos \
    --no-eff-email \
    -d $DOMAIN

# Stop temporary nginx
docker stop nginx-temp && docker rm nginx-temp
rm nginx-initial.conf

echo ""
echo -e "${GREEN}======================================"
echo "  Setup Complete!"
echo "======================================${NC}"
echo ""
echo "Next steps:"
echo "1. Edit /opt/koda/.env with your SMTP and Spaces credentials"
echo "2. Build and start the services:"
echo "   cd /opt/koda"
echo "   docker compose build"
echo "   docker compose up -d"
echo ""
echo "3. Check logs:"
echo "   docker compose logs -f"
echo ""
echo "4. Your Koda instance will be available at:"
echo "   https://$DOMAIN"
echo ""
echo -e "${YELLOW}Remember to configure DigitalOcean Spaces in .env for file storage!${NC}"
