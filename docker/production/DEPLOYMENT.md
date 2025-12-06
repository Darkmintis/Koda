# Koda Production Deployment Guide

Deploy Koda on DigitalOcean using student credits (~$200).

## Prerequisites

- DigitalOcean account with student credits
- A domain name (you can get free ones from Freenom or use a subdomain)
- Basic terminal/SSH knowledge

## Cost Breakdown

**Budget Option (~$17/month = 12 months on $200 credits)**
- Droplet (2GB RAM): $12/month
- Spaces (S3 storage): $5/month
- Self-hosted PostgreSQL: Included in Droplet

**Recommended Option (~$27/month = 7 months on $200 credits)**
- Droplet (4GB RAM): $24/month
- Spaces: $5/month

---

## Step 1: Create DigitalOcean Droplet

1. Go to [DigitalOcean](https://cloud.digitalocean.com/)
2. Click **Create** → **Droplets**
3. Choose:
   - **Region**: Choose closest to your users
   - **Image**: Ubuntu 22.04 LTS
   - **Size**: 
     - Budget: Basic $12/mo (2GB RAM, 1 vCPU, 50GB SSD)
     - Recommended: Basic $24/mo (4GB RAM, 2 vCPU, 80GB SSD)
   - **Authentication**: SSH keys (recommended) or Password
   - **Hostname**: `koda-server`
4. Click **Create Droplet**
5. Note the **IP address**

## Step 2: Create DigitalOcean Spaces

1. Go to **Spaces** → **Create a Space**
2. Choose:
   - **Region**: Same as your Droplet
   - **Name**: `koda-files` (or your choice)
   - **Permissions**: Restrict File Listing (recommended)
3. Click **Create a Space**
4. Go to **API** → **Spaces Keys** → **Generate New Key**
5. Save the **Access Key** and **Secret Key**

## Step 3: Point Domain to Droplet

1. In your domain registrar, add an **A Record**:
   - **Host**: `@` (or subdomain like `app`)
   - **Points to**: Your Droplet IP
   - **TTL**: 300 (or lowest available)
2. Wait 5-15 minutes for DNS propagation

## Step 4: Initial Server Setup

SSH into your Droplet:
```bash
ssh root@YOUR_DROPLET_IP
```

Update the system:
```bash
apt update && apt upgrade -y
```

Install Docker:
```bash
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh
apt install -y docker-compose-plugin
systemctl enable docker
```

## Step 5: Deploy Koda

Create application directory:
```bash
mkdir -p /opt/koda
cd /opt/koda
```

**Option A: Clone from repository**
```bash
git clone https://github.com/YOUR_USERNAME/koda.git .
cp docker/production/* /opt/koda/
```

**Option B: Upload files manually**
From your local machine:
```bash
scp -r docker/production/* root@YOUR_DROPLET_IP:/opt/koda/
```

## Step 6: Configure Environment

Create and edit the `.env` file:
```bash
cd /opt/koda
cp .env.example .env
nano .env
```

Update these values:
```env
# Your domain
DOMAIN=your-domain.com
PUBLIC_URI=https://your-domain.com

# Generate secure password
POSTGRES_PASSWORD=GENERATE_STRONG_PASSWORD

# Generate with: openssl rand -base64 64
SECRET_KEY=GENERATE_SECRET_KEY

# Email settings (use any SMTP provider)
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=your-app-password
SMTP_DEFAULT_FROM=noreply@your-domain.com
SMTP_DEFAULT_REPLY_TO=support@your-domain.com

# DigitalOcean Spaces
SPACES_ACCESS_KEY=your-spaces-access-key
SPACES_SECRET_KEY=your-spaces-secret-key
SPACES_ENDPOINT=https://nyc3.digitaloceanspaces.com
SPACES_BUCKET=koda-files
SPACES_REGION=nyc3
```

Generate secure passwords:
```bash
# For POSTGRES_PASSWORD
openssl rand -base64 32 | tr -dc 'a-zA-Z0-9' | head -c 32

# For SECRET_KEY
openssl rand -base64 64 | tr -dc 'a-zA-Z0-9' | head -c 64
```

## Step 7: Get SSL Certificate

Update nginx config with your domain:
```bash
sed -i "s/\${DOMAIN}/your-domain.com/g" nginx.conf
```

Create directories:
```bash
mkdir -p data/{postgres,redis,assets}
mkdir -p certbot/{conf,www}
```

Get SSL certificate:
```bash
# Start temporary nginx for ACME challenge
docker run -d --name nginx-temp \
    -p 80:80 \
    -v $(pwd)/certbot/www:/var/www/certbot:ro \
    nginx:alpine

# Get certificate
docker run --rm \
    -v $(pwd)/certbot/conf:/etc/letsencrypt \
    -v $(pwd)/certbot/www:/var/www/certbot \
    certbot/certbot certonly \
    --webroot \
    --webroot-path=/var/www/certbot \
    --email your-email@example.com \
    --agree-tos \
    --no-eff-email \
    -d your-domain.com

# Stop temporary nginx
docker stop nginx-temp && docker rm nginx-temp
```

## Step 8: Build and Start Koda

Build the Docker images:
```bash
cd /opt/koda
docker compose build
```

Start all services:
```bash
docker compose up -d
```

Check if everything is running:
```bash
docker compose ps
docker compose logs -f
```

## Step 9: Verify Deployment

1. Visit `https://your-domain.com`
2. You should see the Koda login/register page
3. Create an account and verify email works
4. Create a test project

---

## Maintenance

### View Logs
```bash
docker compose logs -f
docker compose logs -f backend
docker compose logs -f nginx
```

### Restart Services
```bash
docker compose restart
docker compose restart backend
```

### Update Koda
```bash
cd /opt/koda
git pull
docker compose build
docker compose up -d
```

### Backup Database
```bash
docker compose exec postgres pg_dump -U koda koda > backup-$(date +%Y%m%d).sql
```

### Restore Database
```bash
cat backup.sql | docker compose exec -T postgres psql -U koda koda
```

### Renew SSL Certificate
Certificates auto-renew via certbot container. Manual renewal:
```bash
docker compose run --rm certbot renew
docker compose restart nginx
```

---

## Troubleshooting

### Port 80/443 in use
```bash
lsof -i :80
lsof -i :443
# Kill conflicting process or stop it
```

### Database connection issues
```bash
docker compose logs postgres
# Check if postgres is healthy
docker compose exec postgres pg_isready -U koda
```

### SSL certificate issues
```bash
# Check certificate status
docker compose run --rm certbot certificates
# Force renewal
docker compose run --rm certbot renew --force-renewal
```

### Out of memory
```bash
# Check memory usage
free -h
docker stats
# Consider upgrading Droplet or reducing Java heap in docker-compose.yml
```

---

## Free SMTP Options

1. **Gmail** (500 emails/day)
   - Enable 2FA → Create App Password
   - Host: smtp.gmail.com, Port: 587

2. **Brevo (Sendinblue)** (300 emails/day free)
   - Sign up at brevo.com
   - Get SMTP credentials from Settings

3. **Mailgun** (5000 emails/month free for 3 months)
   - Good for production

---

## Free Domain Options

1. **Freenom**: Free .tk, .ml, .ga domains
2. **Afraid.org**: Free subdomains
3. **GitHub Student Pack**: Free .me domain from Namecheap

---

## Security Checklist

- [ ] Strong PostgreSQL password
- [ ] Strong secret key
- [ ] Firewall enabled (ufw allow 22,80,443)
- [ ] Regular backups
- [ ] Keep system updated
- [ ] Monitor disk space

```bash
# Enable firewall
ufw allow OpenSSH
ufw allow 80
ufw allow 443
ufw enable
```
